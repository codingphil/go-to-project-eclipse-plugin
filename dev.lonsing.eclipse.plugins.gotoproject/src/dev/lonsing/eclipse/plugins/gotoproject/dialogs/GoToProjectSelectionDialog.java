package dev.lonsing.eclipse.plugins.gotoproject.dialogs;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.dialogs.FilteredItemsSelectionDialog;
import org.eclipse.ui.model.WorkbenchLabelProvider;

import dev.lonsing.eclipse.plugins.gotoproject.GoToProjectPlugin;
import dev.lonsing.eclipse.plugins.gotoproject.Messages;

public class GoToProjectSelectionDialog extends FilteredItemsSelectionDialog {
  private ProjectComparator itemsComparator = new ProjectComparator();
  private List<IProject> allProjects;

  private class ProjectItemsFilter extends ItemsFilter {

    @Override
    public boolean matchItem(Object item) {
      String pattern = this.getPattern();
      if (pattern == null || pattern.trim().isEmpty()) {
        return true;
      }
      String projectName = ((IProject) item).getName();
      return this.patternMatcher.matches(projectName);
    }

    @Override
    public boolean isConsistentItem(Object item) {
      return true;
    }
  }

  public GoToProjectSelectionDialog(Shell shell) {
    super(shell, false);
    this.setTitle(Messages.GoToProjectSelectionDialog_SelectProjectDialogTitle);
    this.setMessage(Messages.GoToProjectSelectionDialog_SelectProjectDialogMessage);
    this.setListLabelProvider(new WorkbenchLabelProvider());
    this.setDetailsLabelProvider(new WorkbenchLabelProvider());
    allProjects = getAllProjectsOfWorkspace();
    allProjects.sort(new ProjectComparator());
  }

  public IProject getSelectedProject() {
    return (IProject) getFirstResult();
  }

  @Override
  protected Control createExtendedContentArea(Composite parent) {
    return null;
  }

  @Override
  protected IDialogSettings getDialogSettings() {
    return GoToProjectPlugin.getDefault().getDialogSettings();
  }

  @Override
  protected IStatus validateItem(Object item) {
    return Status.OK_STATUS;
  }

  @Override
  protected ItemsFilter createFilter() {
    return new ProjectItemsFilter();
  }

  @Override
  protected Comparator<IProject> getItemsComparator() {
    return itemsComparator;
  }

  @Override
  public String getElementName(Object item) {
    return ((IProject) item).getName();
  }

  @Override
  protected void fillContentProvider(AbstractContentProvider contentProvider, ItemsFilter itemsFilter,
      IProgressMonitor progressMonitor) throws CoreException {
    for (IProject project : allProjects) {
      contentProvider.add(project, itemsFilter);
    }
  }

  private static List<IProject> getAllProjectsOfWorkspace() {
    return Arrays.asList(ResourcesPlugin.getWorkspace().getRoot().getProjects());
  }
}
