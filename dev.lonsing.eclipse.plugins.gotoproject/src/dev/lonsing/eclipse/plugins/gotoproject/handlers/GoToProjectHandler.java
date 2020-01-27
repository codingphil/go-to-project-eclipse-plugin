package dev.lonsing.eclipse.plugins.gotoproject.handlers;

import java.util.Arrays;
import java.util.Optional;

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
import org.eclipse.ui.IViewReference;
import org.eclipse.ui.IWorkbenchWindow;
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
      IViewReference[] viewRefs = window.getActivePage().getViewReferences();
      selectProjectInView(selectedProject, IPageLayout.ID_PROJECT_EXPLORER, viewRefs);
      IJavaProject selectedJavaProject = projectToJavaProject(selectedProject);
      selectProjectInView(selectedJavaProject != null ? selectedJavaProject : selectedProject, JavaUI.ID_PACKAGES,
          viewRefs);
    }
    return null;
  }

  private void selectProjectInView(Object selectedProject, String viewId, IViewReference[] viewRefs) {
    Optional<IViewReference> projectViewRef = Arrays.stream(viewRefs).filter(v -> v.getId().equals(viewId)).findAny();
    if (projectViewRef.isPresent()) {
      IViewPart projectViewPart = projectViewRef.get().getView(false);
      if (projectViewPart != null && projectViewPart.getViewSite() != null) {
        projectViewPart.getViewSite().getSelectionProvider().setSelection(new StructuredSelection(selectedProject));
      }
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
