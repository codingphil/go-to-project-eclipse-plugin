package dev.lonsing.eclipse.plugins.gotoproject.handlers;

import java.util.Arrays;
import java.util.Objects;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.ui.IPageLayout;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.handlers.HandlerUtil;

import dev.lonsing.eclipse.plugins.gotoproject.Messages;
import dev.lonsing.eclipse.plugins.gotoproject.dialogs.GoToProjectSelectionDialog;
import dev.lonsing.eclipse.plugins.gotoproject.exceptions.GoToProjectException;

public class GoToProjectHandler extends AbstractHandler {

  @Override
  public Object execute(ExecutionEvent event) throws ExecutionException {
    IWorkbenchWindow window = HandlerUtil.getActiveWorkbenchWindowChecked(event);

    GoToProjectSelectionDialog selectionDialog = new GoToProjectSelectionDialog(window.getShell());
    if (selectionDialog.open() == IDialogConstants.OK_ID) {
      IProject selectedProject = selectionDialog.getSelectedProject();
      selectProjectInViews(window, selectedProject);
    }
    return null;
  }

  private void selectProjectInViews(IWorkbenchWindow window, IProject selectedProject) throws ExecutionException {
    IWorkbenchPage activePage = window.getActivePage();
    IViewPart projectExplorerViewPart = getViewPart(activePage, IPageLayout.ID_PROJECT_EXPLORER);
    IViewPart packageExplorerViewPart = getViewPart(activePage, JavaUI.ID_PACKAGES);
    if (!activePage.isPartVisible(projectExplorerViewPart) && !activePage.isPartVisible(packageExplorerViewPart)) {
      if (packageExplorerViewPart != null) {
        try {
          packageExplorerViewPart = activePage.showView(JavaUI.ID_PACKAGES);
        } catch (PartInitException e) {
          throw new ExecutionException("Failed to show Package Explorer", e);
        }
      } else {
        try {
          projectExplorerViewPart = activePage.showView(IPageLayout.ID_PROJECT_EXPLORER);
        } catch (PartInitException e) {
          throw new ExecutionException("Failed to show Project Explorer", e);
        }
      }
    }
    selectProjectInView(selectedProject, projectExplorerViewPart, activePage);
    IJavaProject selectedJavaProject = projectToJavaProject(selectedProject);
    selectProjectInView(selectedJavaProject != null ? selectedJavaProject : selectedProject, packageExplorerViewPart,
        activePage);
  }

  private IViewPart getViewPart(IWorkbenchPage activePage, String viewId) {
    return Arrays.stream(activePage.getViewReferences()).filter(r -> viewId.equals(r.getId()))
        .map(r -> r.getView(false)).filter(Objects::nonNull).findAny().orElse(null);
  }

  private void selectProjectInView(Object selectedProject, IViewPart viewPart, IWorkbenchPage activePage) {
    if (viewPart != null && viewPart.getViewSite() != null) {
      if (activePage.isPartVisible(viewPart)) {
        viewPart.setFocus();
      }
      viewPart.getViewSite().getSelectionProvider().setSelection(new StructuredSelection(selectedProject));
    }
  }

  private IJavaProject projectToJavaProject(IProject selectedProject) {
    if (isJavaProject(selectedProject)) {
      return JavaCore.create(selectedProject);
    }
    return null;
  }

  public static boolean isJavaProject(IProject project) {
    return hasProjectNature(project, JavaCore.NATURE_ID);
  }

  private static boolean hasProjectNature(IProject project, String natureId) {
    try {
      return project.isOpen() && Arrays.asList(project.getDescription().getNatureIds()).indexOf(natureId) != -1;
    } catch (CoreException e) {
      throw new GoToProjectException(Messages.GoToProjectHandler_FailedToCheckProjectHandlerExceptionMessage, e);
    }
  }
}
