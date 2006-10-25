package com.intellij.localvcs;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class LocalVcsTest extends Assert {
  private LocalVcs myVcs;

  @Before
  public void setUp() {
    myVcs = new LocalVcs();
  }

  @Test
  public void testAddingFiles() {
    myVcs.addFile("file", "");
    assertFalse(myVcs.hasFile("file"));

    myVcs.commit();
    assertTrue(myVcs.hasFile("file"));
  }

  @Test
  public void testAddingTwoFiles() {
    myVcs.addFile("file1", "");
    myVcs.addFile("file2", "");
    myVcs.commit();

    assertTrue(myVcs.hasFile("file1"));
    assertTrue(myVcs.hasFile("file2"));

    assertFalse(myVcs.hasFile("unknown file"));
  }

  @Test
  public void testClearingChangesOnCommit() {
    myVcs.addFile("file", "content");
    myVcs.changeFile("file", "new content");
    myVcs.renameFile("file", "new file");
    myVcs.deleteFile("new file");

    assertFalse(myVcs.isClean());

    myVcs.commit();
    assertTrue(myVcs.isClean());
  }

  @Test
  public void testChangingContent() {
    myVcs.addFile("file", "content");
    myVcs.commit();

    myVcs.changeFile("file", "new content");
    myVcs.commit();

    assertRevisionContent("new content", myVcs.getFileRevision("file"));
  }

  @Test
  public void testKeepingOldVersions() {
    myVcs.addFile("file", "content");
    myVcs.commit();

    myVcs.changeFile("file", "new content");
    myVcs.commit();

    assertRevisionsContent(new String[]{"new content", "content"},
                           myVcs.getFileRevisions("file"));
  }

  @Test
  public void testDoesNotKeepUncommittedChanges() {
    myVcs.addFile("file", "content");
    myVcs.commit();

    myVcs.changeFile("file", "new content");

    assertRevisionsContent(new String[]{"content"},
                           myVcs.getFileRevisions("file"));
  }

  @Test
  public void testContentOfUnknownFile() {
    assertNull(myVcs.getFileRevision("unknown file"));
  }

  @Test
  public void testChangingOnlyOneFile() {
    myVcs.addFile("file1", "content1");
    myVcs.addFile("file2", "content2");
    myVcs.commit();

    myVcs.changeFile("file1", "new content");
    myVcs.commit();

    assertRevisionContent("new content", myVcs.getFileRevision("file1"));
    assertRevisionContent("content2", myVcs.getFileRevision("file2"));
  }

  @Test
  public void testDoesNotChangeContentBeforeCommit() {
    myVcs.addFile("file", "content");
    myVcs.commit();

    myVcs.changeFile("file", "new content");

    assertRevisionContent("content", myVcs.getFileRevision("file"));
  }

  @Test
  public void testRenaming() {
    myVcs.addFile("file", "content");
    myVcs.commit();

    myVcs.renameFile("file", "new file");
    myVcs.commit();

    assertFalse(myVcs.hasFile("file"));
    assertTrue(myVcs.hasFile("new file"));

    assertRevisionContent("content", myVcs.getFileRevision("new file"));
  }

  @Test
  public void testRenamingKeepsOldNameAndContent() {
    myVcs.addFile("file", "content");
    myVcs.commit();

    myVcs.renameFile("file", "new file");
    myVcs.commit();

    List<Revision> revs = myVcs.getFileRevisions("new file");

    assertEquals(2, revs.size());

    assertEquals("new file", revs.get(0).getName());
    assertEquals("content", revs.get(0).getContent());

    assertEquals("file", revs.get(1).getName());
    assertEquals("content", revs.get(1).getContent());
  }

  @Test
  public void testDeleting() {
    myVcs.addFile("file", "content");
    myVcs.commit();

    myVcs.deleteFile("file");
    assertRevisionContent("content", myVcs.getFileRevision("file"));

    myVcs.commit();
    assertFalse(myVcs.hasFile("file"));
    assertNull(myVcs.getFileRevision("file"));
  }

  @Test
  public void testDeletingOnlyOneFile() {
    myVcs.addFile("file1", "");
    myVcs.addFile("file2", "");
    myVcs.commit();

    myVcs.deleteFile("file2");
    myVcs.commit();

    assertTrue(myVcs.hasFile("file1"));
    assertFalse(myVcs.hasFile("file2"));
  }

  @Test
  public void testAddingAndDeletingSameFileBeforeCommit() {
    myVcs.addFile("file", "");
    myVcs.deleteFile("file");
    myVcs.commit();

    assertFalse(myVcs.hasFile("file"));
  }

  @Test
  public void testDeletingAndAddingSameFileBeforeCommit() {
    myVcs.addFile("file", "");
    myVcs.commit();

    myVcs.deleteFile("file");
    myVcs.addFile("file", "");
    myVcs.commit();

    assertTrue(myVcs.hasFile("file"));
  }

  @Test
  public void testAddingAndChangingSameFileBeforeCommit() {
    myVcs.addFile("file", "content");
    myVcs.changeFile("file", "new content");
    myVcs.commit();

    assertRevisionContent("new content", myVcs.getFileRevision("file"));
  }

  @Test
  public void testFileRevisions() {
    assertTrue(myVcs.getFileRevisions("file").isEmpty());

    myVcs.addFile("file", "");
    myVcs.commit();

    assertEquals(1, myVcs.getFileRevisions("file").size());
  }

  @Test
  public void testRevertingToPreviousVersion() {
    myVcs.addFile("file", "");
    myVcs.commit();
    assertTrue(myVcs.hasFile("file"));

    myVcs.revert();
    assertFalse(myVcs.hasFile("file"));
  }

  @Test
  public void testRevertingClearsAllPendingChanges() {
    myVcs.addFile("file1", "");
    myVcs.commit();

    myVcs.addFile("file2", "");
    assertFalse(myVcs.isClean());

    myVcs.revert();
    assertTrue(myVcs.isClean());
  }

  @Test
  public void testRevertingWhenNoPreviousVersions() {
    try {
      myVcs.revert();
      myVcs.revert();
    } catch (Exception e) {
      fail(e.toString());
    }
  }

  @Test
  public void testClearingChangesAfterRevertWhenNoPreviousVersions() {
    myVcs.addFile("file", "");
    assertFalse(myVcs.isClean());

    myVcs.revert();
    assertTrue(myVcs.isClean());
  }

  private void assertRevisionContent(String expectedContent,
                                     Revision actualRevision) {
    assertEquals(expectedContent, actualRevision.getContent());
  }

  private void assertRevisionsContent(String[] expectedContents,
                                      Collection<Revision> actualRevisions) {
    List<String> actualContents = new ArrayList<String>();
    for (Revision rev : actualRevisions) {
      actualContents.add(rev.getContent());
    }
    assertEquals(expectedContents, actualContents.toArray(new Object[0]));
  }
}
