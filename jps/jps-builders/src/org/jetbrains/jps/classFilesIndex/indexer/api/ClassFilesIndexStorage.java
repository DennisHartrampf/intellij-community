/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.jps.classFilesIndex.indexer.api;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.Processor;
import com.intellij.util.containers.SLRUCache;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.KeyDescriptor;
import com.intellij.util.io.PersistentHashMap;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author Dmitry Batkovich
 *         <p/>
 *         synchronization only on write actions
 */
public class ClassFilesIndexStorage<K, V> {
  private static final String INDEX_FILE_NAME = "index";
  private static final int INITIAL_INDEX_SIZE = 16 * 1024;
  private static final int CACHE_QUEUES_SIZE = 16 * 1024;

  private final File myIndexFile;
  private final KeyDescriptor<K> myKeyDescriptor;
  private final DataExternalizer<V> myValueExternalizer;
  private final Lock myWriteLock = new ReentrantLock();
  private PersistentHashMap<K, CompiledDataValueContainer<V>> myMap;
  @SuppressWarnings("FieldAccessedSynchronizedAndUnsynchronized") private SLRUCache<K, CompiledDataValueContainer<V>> myCache;

  public ClassFilesIndexStorage(final File indexDir, final KeyDescriptor<K> keyDescriptor, final DataExternalizer<V> valueExternalizer)
    throws IOException {
    myIndexFile = getIndexFile(indexDir);
    myKeyDescriptor = keyDescriptor;
    myValueExternalizer = valueExternalizer;
    initialize();
  }

  private void initialize() throws IOException {
    myMap = new PersistentHashMap<K, CompiledDataValueContainer<V>>(myIndexFile, myKeyDescriptor,
                                                                    createValueContainerExternalizer(myValueExternalizer),
                                                                    INITIAL_INDEX_SIZE);
    myCache = new SLRUCache<K, CompiledDataValueContainer<V>>(CACHE_QUEUES_SIZE, CACHE_QUEUES_SIZE) {
      @NotNull
      @Override
      public CompiledDataValueContainer<V> createValue(final K key) {
        try {
          final CompiledDataValueContainer<V> valueContainer = myMap.get(key);
          if (valueContainer != null) {
            return valueContainer;
          }
        }
        catch (final IOException e) {
          throw new RuntimeException(e);
        }
        return new CompiledDataValueContainer<V>();
      }

      @Override
      protected void onDropFromCache(final K key, final CompiledDataValueContainer<V> value) {
        try {
          myMap.put(key, value);
        }
        catch (final IOException e) {
          throw new RuntimeException(e);
        }
      }
    };
  }

  public Collection<V> getData(final K key) {
    return myCache.get(key).getValues();
  }

  public void putData(final K key, final V value, final String inputId) {
    try {
      myWriteLock.lock();
      final CompiledDataValueContainer<V> container = myCache.get(key);
      container.putValue(inputId, value);
    }
    finally {
      myWriteLock.unlock();
    }
  }

  public void delete() throws IOException {
    try {
      myWriteLock.lock();
      doDelete();
    }
    finally {
      myWriteLock.unlock();
    }
  }

  private void doDelete() throws IOException {
    close();
    PersistentHashMap.deleteFilesStartingWith(myIndexFile);
  }

  public void clear() throws IOException {
    try {
      myWriteLock.lock();
      doDelete();
      initialize();
    }
    finally {
      myWriteLock.unlock();
    }
  }

  public void flush() {
    try {
      myWriteLock.lock();
      myCache.clear();
    }
    finally {
      myWriteLock.unlock();
    }
    myMap.force();
  }

  public void close() throws IOException {
    flush();
    myMap.close();
  }

  public static class CompiledDataValueContainer<V> {
    private final THashMap<String, V> myUnderlying;

    private CompiledDataValueContainer(final THashMap<String, V> map) {
      myUnderlying = map;
    }

    private CompiledDataValueContainer() {
      this(new THashMap<String, V>());
    }

    private void putValue(final String inputId, final V value) {
      myUnderlying.put(inputId, value);
    }

    public Collection<V> getValues() {
      return myUnderlying.values();
    }

  }

  public static File getIndexFile(final File indexDir) {
    return new File(indexDir, INDEX_FILE_NAME);
  }

  public static File getIndexDir(final String indexName, final File projectSystemBuildDirectory) {
    return new File(projectSystemBuildDirectory, "compiler.output.data.indices/" + indexName);
  }

  private static <V> DataExternalizer<CompiledDataValueContainer<V>> createValueContainerExternalizer(final DataExternalizer<V> valueExternalizer) {
    final DataExternalizer<String> stringDataExternalizer = new EnumeratorStringDescriptor();
    return new DataExternalizer<CompiledDataValueContainer<V>>() {
      @Override
      public void save(final DataOutput out, final CompiledDataValueContainer<V> value) throws IOException {
        final THashMap<String, V> underlying = value.myUnderlying;
        out.writeInt(underlying.size());
        for (final Map.Entry<String, V> entry : underlying.entrySet()) {
          stringDataExternalizer.save(out, entry.getKey());
          valueExternalizer.save(out, entry.getValue());
        }
      }

      @Override
      public CompiledDataValueContainer<V> read(final DataInput in) throws IOException {
        final THashMap<String, V> map = new THashMap<String, V>();
        final int size = in.readInt();
        for (int i = 0; i < size; i++) {
          map.put(stringDataExternalizer.read(in), valueExternalizer.read(in));
        }
        return new CompiledDataValueContainer<V>(map);
      }
    };
  }
}
