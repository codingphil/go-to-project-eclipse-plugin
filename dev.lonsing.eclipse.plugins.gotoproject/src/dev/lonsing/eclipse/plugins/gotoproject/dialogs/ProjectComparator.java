package dev.lonsing.eclipse.plugins.gotoproject.dialogs;

import java.util.Comparator;

import org.eclipse.core.resources.IProject;

public class ProjectComparator implements Comparator<IProject> {
  private static final Comparator<IProject> PROJECT_NAME_COMPARATOR = Comparator
      .comparing(IProject::getName, String.CASE_INSENSITIVE_ORDER)
      .thenComparing(IProject::getName);

  @Override
  public int compare(IProject projectLhs, IProject projectRhs) {
    return PROJECT_NAME_COMPARATOR.compare(projectLhs, projectRhs);
  }
}
