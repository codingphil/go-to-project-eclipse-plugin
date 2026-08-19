package dev.lonsing.eclipse.plugins.gotoproject.handlers;

import java.util.Arrays;
import java.util.function.Consumer;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.resources.WorkspaceJob;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IPageLayout;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.actions.OpenResourceAction;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.statushandlers.StatusManager;

import dev.lonsing.eclipse.plugins.gotoproject.GoToProjectPlugin;
import dev.lonsing.eclipse.plugins.gotoproject.Messages;
import dev.lonsing.eclipse.plugins.gotoproject.dialogs.GoToProjectSelectionDialog;
import dev.lonsing.eclipse.plugins.gotoproject.exceptions.GoToProjectException;

public class GoToProjectHandler extends AbstractHandler {

  @FunctionalInterface
  interface ProjectSelector {
    IProject select(IWorkbenchWindow window);
  }

  @FunctionalInterface
  interface ProjectOpener {
    void open(IWorkbenchWindow window, IProject project, Runnable afterOpening);
  }

  private final ProjectSelector projectSelector;
  private final ProjectOpener projectOpener;
  private final Consumer<ExecutionException> asynchronousErrorReporter;

  public GoToProjectHandler() {
    this(GoToProjectHandler::showProjectSelectionDialog, GoToProjectHandler::openProject,
        GoToProjectHandler::reportAsynchronousError);
  }

  GoToProjectHandler(ProjectSelector projectSelector, ProjectOpener projectOpener) {
    this(projectSelector, projectOpener, GoToProjectHandler::reportAsynchronousError);
  }

  GoToProjectHandler(ProjectSelector projectSelector, ProjectOpener projectOpener,
      Consumer<ExecutionException> asynchronousErrorReporter) {
    this.projectSelector = projectSelector;
    this.projectOpener = projectOpener;
    this.asynchronousErrorReporter = asynchronousErrorReporter;
  }

  @Override
  public Object execute(ExecutionEvent event) throws ExecutionException {
    IWorkbenchWindow window = HandlerUtil.getActiveWorkbenchWindowChecked(event);
    execute(window);
    return null;
  }

  void execute(IWorkbenchWindow window) throws ExecutionException {
    IProject selectedProject = projectSelector.select(window);
    if (selectedProject == null) {
      return;
    }
    if (!selectedProject.isOpen()) {
      projectOpener.open(window, selectedProject, () -> continueAfterOpening(window, selectedProject));
      return;
    }
    selectProjectInViews(window, selectedProject);
  }

  private void continueAfterOpening(IWorkbenchWindow window, IProject selectedProject) {
    if (!selectedProject.isOpen()) {
      return;
    }
    try {
      selectProjectInViews(window, selectedProject);
    } catch (ExecutionException e) {
      asynchronousErrorReporter.accept(e);
    }
  }

  private static IProject showProjectSelectionDialog(IWorkbenchWindow window) {
    GoToProjectSelectionDialog selectionDialog = new GoToProjectSelectionDialog(window.getShell());
    if (selectionDialog.open() == IDialogConstants.OK_ID) {
      return selectionDialog.getSelectedProject();
    }
    return null;
  }

  private static void openProject(IWorkbenchWindow window, IProject project, Runnable afterOpening) {
    Shell shell = window.getShell();
    if (shell == null || shell.isDisposed()) {
      return;
    }
    Display display = shell.getDisplay();
    OpenResourceAction action = new OpenResourceAction(window);
    action.selectionChanged(new StructuredSelection(project));

    // OpenResourceAction schedules its WorkspaceJob before returning. Scheduling this
    // lower-priority job afterwards with the identical root rule makes it a barrier:
    // it cannot run until the project-opening job has released the workspace root.
    action.run();
    WorkspaceJob navigationBarrier = new WorkspaceJob(Messages.GoToProjectHandler_FinishNavigationJobName) {
      @Override
      public IStatus runInWorkspace(IProgressMonitor monitor) {
        if (!display.isDisposed()) {
          display.asyncExec(() -> {
            if (!display.isDisposed() && !shell.isDisposed()) {
              afterOpening.run();
            }
          });
        }
        return Status.OK_STATUS;
      }
    };
    navigationBarrier.setRule(ResourcesPlugin.getWorkspace().getRoot());
    navigationBarrier.setPriority(Job.DECORATE);
    navigationBarrier.setSystem(true);
    navigationBarrier.schedule();
  }

  private static void reportAsynchronousError(ExecutionException exception) {
    IStatus status = new Status(IStatus.ERROR, GoToProjectPlugin.PLUGIN_ID, exception.getMessage(), exception);
    StatusManager.getManager().handle(status, StatusManager.LOG | StatusManager.SHOW);
  }

  void selectProjectInViews(IWorkbenchWindow window, IProject selectedProject) throws ExecutionException {
    IWorkbenchPage activePage = window.getActivePage();
    if (activePage == null) {
      throw new ExecutionException(Messages.GoToProjectHandler_NoActiveWorkbenchPage);
    }

    IViewPart projectExplorerViewPart = getViewPart(activePage, IPageLayout.ID_PROJECT_EXPLORER);
    IViewPart packageExplorerViewPart = getViewPart(activePage, JavaUI.ID_PACKAGES);

    if (!isPartVisible(activePage, projectExplorerViewPart) && !isPartVisible(activePage, packageExplorerViewPart)) {
      try {
        projectExplorerViewPart = activePage.showView(IPageLayout.ID_PROJECT_EXPLORER);
      } catch (PartInitException e) {
        throw new ExecutionException(Messages.GoToProjectHandler_FailedToShowProjectExplorer, e);
      }
    }

    boolean selectedInProjectExplorer = selectProjectInView(selectedProject, projectExplorerViewPart, activePage);
    IJavaProject selectedJavaProject = projectToJavaProject(selectedProject);
    Object packageExplorerSelection = selectedJavaProject != null ? selectedJavaProject : selectedProject;
    boolean selectedInPackageExplorer = selectProjectInView(packageExplorerSelection, packageExplorerViewPart,
        activePage);

    IWorkbenchPart activePart = activePage.getActivePart();
    IViewPart focusTarget = null;
    if (activePart == projectExplorerViewPart && selectedInProjectExplorer) {
      focusTarget = projectExplorerViewPart;
    } else if (activePart == packageExplorerViewPart && selectedInPackageExplorer) {
      focusTarget = packageExplorerViewPart;
    } else if (selectedInProjectExplorer) {
      focusTarget = projectExplorerViewPart;
    } else if (selectedInPackageExplorer) {
      focusTarget = packageExplorerViewPart;
    }

    if (focusTarget == null) {
      throw new ExecutionException(Messages.GoToProjectHandler_NoExplorerCanAcceptSelection);
    }
    focusTarget.setFocus();
  }

  private static IViewPart getViewPart(IWorkbenchPage activePage, String viewId) {
    if (activePage.getViewReferences() == null) {
      return null;
    }
    return Arrays.stream(activePage.getViewReferences()).filter(reference -> reference != null)
        .filter(reference -> viewId.equals(reference.getId())).map(reference -> reference.getView(false))
        .filter(view -> view != null).findFirst().orElse(null);
  }

  private static boolean isPartVisible(IWorkbenchPage activePage, IViewPart viewPart) {
    return viewPart != null && activePage.isPartVisible(viewPart);
  }

  private static boolean selectProjectInView(Object selectedProject, IViewPart viewPart,
      IWorkbenchPage activePage) {
    if (!isPartVisible(activePage, viewPart) || viewPart.getViewSite() == null
        || viewPart.getViewSite().getSelectionProvider() == null) {
      return false;
    }
    viewPart.getViewSite().getSelectionProvider().setSelection(new StructuredSelection(selectedProject));
    return true;
  }

  private static IJavaProject projectToJavaProject(IProject selectedProject) {
    if (isJavaProject(selectedProject)) {
      return JavaCore.create(selectedProject);
    }
    return null;
  }

  public static boolean isJavaProject(IProject project) {
    try {
      return project != null && project.isOpen() && project.hasNature(JavaCore.NATURE_ID);
    } catch (CoreException e) {
      throw new GoToProjectException(Messages.GoToProjectHandler_FailedToCheckProjectHandlerExceptionMessage, e);
    }
  }
}
