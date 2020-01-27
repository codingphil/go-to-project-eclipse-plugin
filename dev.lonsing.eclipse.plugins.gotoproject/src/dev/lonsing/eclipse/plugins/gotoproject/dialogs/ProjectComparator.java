package dev.lonsing.eclipse.plugins.gotoproject.dialogs;

import java.util.Comparator;
import java.util.Objects;

import org.eclipse.core.resources.IProject;

public class ProjectComparator implements Comparator<IProject> {

  @Override
  public int compare(IProject projectLhs, IProject projectRhs) {
    return Objects.compare(projectLhs, projectRhs,
        (IProject p1, IProject p2) -> p1.getName().compareToIgnoreCase(p2.getName()));
  }

}
