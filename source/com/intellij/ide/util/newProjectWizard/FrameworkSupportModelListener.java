package com.intellij.ide.util.newProjectWizard;

import org.jetbrains.annotations.NotNull;

import java.util.EventListener;

/**
 * @author nik
 */
public interface FrameworkSupportModelListener extends EventListener {

  void frameworkSelected(@NotNull FrameworkSupportProvider provider);

  void frameworkUnselected(@NotNull FrameworkSupportProvider provider);

}
