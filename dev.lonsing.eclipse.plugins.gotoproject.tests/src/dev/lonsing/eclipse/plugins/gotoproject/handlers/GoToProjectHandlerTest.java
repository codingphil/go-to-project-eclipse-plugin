package dev.lonsing.eclipse.plugins.gotoproject.handlers;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.IPageLayout;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IViewReference;
import org.eclipse.ui.IViewSite;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.junit.jupiter.api.Test;

class GoToProjectHandlerTest {
  @Test
  void cancellationDoesNothing() {
    GoToProjectHandler handler = new GoToProjectHandler(window -> null, (window, project, afterOpening) -> {
      throw new AssertionError("Project opener must not run after cancellation");
    });

    assertDoesNotThrow(() -> handler.execute(workbenchWindow(null)));
  }

  @Test
  void closedProjectThatRemainsClosedStopsBeforeNavigation() {
    ProjectState projectState = new ProjectState("closed", false, false);
    IProject project = proxy(IProject.class, projectState);
    ViewState projectExplorer = new ViewState();
    PageState page = new PageState();
    page.showViewResult = projectExplorer.view;
    AtomicBoolean openerCalled = new AtomicBoolean();
    AtomicReference<Runnable> afterOpening = new AtomicReference<>();
    GoToProjectHandler handler = new GoToProjectHandler(window -> project,
        (window, selectedProject, continuation) -> {
          openerCalled.set(true);
          afterOpening.set(continuation);
        });

    assertDoesNotThrow(() -> handler.execute(workbenchWindow(page.page)));
    assertTrue(openerCalled.get());
    assertNotNull(afterOpening.get());
    assertNull(page.shownViewId);
    assertNull(projectExplorer.selectionProvider.selection);
    assertEquals(0, projectExplorer.focusCount);

    afterOpening.get().run();

    assertNull(page.shownViewId);
    assertNull(projectExplorer.selectionProvider.selection);
    assertEquals(0, projectExplorer.focusCount);
  }

  @Test
  void closedProjectIsOpenedBeforeProjectExplorerFallback() throws Exception {
    ProjectState projectState = new ProjectState("closed", false, false);
    IProject project = proxy(IProject.class, projectState);
    ViewState projectExplorer = new ViewState();
    PageState page = new PageState();
    page.showViewResult = projectExplorer.view;
    AtomicReference<Runnable> afterOpening = new AtomicReference<>();

    GoToProjectHandler handler = new GoToProjectHandler(window -> project,
        (window, selectedProject, continuation) -> afterOpening.set(continuation));
    handler.execute(workbenchWindow(page.page));

    assertNotNull(afterOpening.get());
    assertNull(page.shownViewId);
    assertNull(projectExplorer.selectionProvider.selection);
    assertEquals(0, projectExplorer.focusCount);

    projectState.open = true;
    afterOpening.get().run();

    assertEquals(IPageLayout.ID_PROJECT_EXPLORER, page.shownViewId);
    assertSame(project, firstElement(projectExplorer));
    assertEquals(1, projectExplorer.focusCount);
  }

  @Test
  void asynchronousNavigationFailureIsReported() throws Exception {
    ProjectState projectState = new ProjectState("closed", false, false);
    IProject project = proxy(IProject.class, projectState);
    AtomicReference<Runnable> afterOpening = new AtomicReference<>();
    AtomicReference<ExecutionException> reportedFailure = new AtomicReference<>();
    GoToProjectHandler handler = new GoToProjectHandler(window -> project,
        (window, selectedProject, continuation) -> afterOpening.set(continuation), reportedFailure::set);

    handler.execute(workbenchWindow(null));

    assertNotNull(afterOpening.get());
    assertNull(reportedFailure.get());

    projectState.open = true;
    assertDoesNotThrow(() -> afterOpening.get().run());

    assertNotNull(reportedFailure.get());
  }

  @Test
  void missingActivePageIsReported() {
    IProject project = project("project", true, false);
    GoToProjectHandler handler = new GoToProjectHandler(window -> project, unexpectedProjectOpener());

    assertThrows(ExecutionException.class, () -> handler.execute(workbenchWindow(null)));
  }

  @Test
  void hiddenPackageExplorerStillUsesProjectExplorerFallback() throws Exception {
    IProject project = project("project", true, false);
    ViewState packageExplorer = new ViewState();
    ViewState projectExplorer = new ViewState();
    PageState page = new PageState();
    page.addReference(JavaUI.ID_PACKAGES, packageExplorer.view, false);
    page.showViewResult = projectExplorer.view;

    new GoToProjectHandler(window -> project, unexpectedProjectOpener())
        .execute(workbenchWindow(page.page));

    assertEquals(IPageLayout.ID_PROJECT_EXPLORER, page.shownViewId);
    assertNull(packageExplorer.selectionProvider.selection);
    assertSame(project, firstElement(projectExplorer));
  }

  @Test
  void packageExplorerReceivesJavaProject() throws Exception {
    IProject project = project("java-project", true, true);
    ViewState packageExplorer = new ViewState();
    PageState page = new PageState();
    page.addReference(JavaUI.ID_PACKAGES, packageExplorer.view, true);

    new GoToProjectHandler(window -> project, unexpectedProjectOpener())
        .execute(workbenchWindow(page.page));

    assertInstanceOf(IJavaProject.class, firstElement(packageExplorer));
    assertEquals(1, packageExplorer.focusCount);
  }

  @Test
  void bothVisibleExplorersAreUpdatedAndActiveExplorerKeepsFocus() throws Exception {
    IProject project = project("java-project", true, true);
    ViewState projectExplorer = new ViewState();
    ViewState packageExplorer = new ViewState();
    PageState page = new PageState();
    page.addReference(IPageLayout.ID_PROJECT_EXPLORER, projectExplorer.view, true);
    page.addReference(JavaUI.ID_PACKAGES, packageExplorer.view, true);
    page.activePart = packageExplorer.view;

    new GoToProjectHandler(window -> project, unexpectedProjectOpener())
        .execute(workbenchWindow(page.page));

    assertSame(project, firstElement(projectExplorer));
    assertInstanceOf(IJavaProject.class, firstElement(packageExplorer));
    assertEquals(0, projectExplorer.focusCount);
    assertEquals(1, packageExplorer.focusCount);
  }

  @Test
  void nullSiteOrSelectionProviderIsSkipped() throws Exception {
    IProject project = project("project", true, false);
    ViewState invalidProjectExplorer = new ViewState(false, false);
    ViewState packageExplorer = new ViewState(true, true);
    PageState page = new PageState();
    page.addReference(IPageLayout.ID_PROJECT_EXPLORER, invalidProjectExplorer.view, true);
    page.addReference(JavaUI.ID_PACKAGES, packageExplorer.view, true);

    new GoToProjectHandler(window -> project, unexpectedProjectOpener())
        .execute(workbenchWindow(page.page));

    assertSame(project, firstElement(packageExplorer));
  }

  @Test
  void noExplorerSelectionProviderIsReported() {
    IProject project = project("project", true, false);
    ViewState invalidView = new ViewState(true, false);
    PageState page = new PageState();
    page.addReference(IPageLayout.ID_PROJECT_EXPLORER, invalidView.view, true);

    GoToProjectHandler handler = new GoToProjectHandler(window -> project, unexpectedProjectOpener());
    assertThrows(ExecutionException.class, () -> handler.execute(workbenchWindow(page.page)));
  }

  @Test
  void projectExplorerInitializationFailureIsReported() {
    IProject project = project("project", true, false);
    PageState page = new PageState();
    page.showViewFailure = new PartInitException("failure");

    GoToProjectHandler handler = new GoToProjectHandler(window -> project, unexpectedProjectOpener());
    assertThrows(ExecutionException.class, () -> handler.execute(workbenchWindow(page.page)));
  }

  private static IProject project(String name, boolean open, boolean javaProject) {
    return proxy(IProject.class, new ProjectState(name, open, javaProject));
  }

  private static GoToProjectHandler.ProjectOpener unexpectedProjectOpener() {
    return (window, project, afterOpening) -> {
      throw new AssertionError("Project opener must not run for an open project");
    };
  }

  private static Object firstElement(ViewState viewState) {
    return ((IStructuredSelection) viewState.selectionProvider.selection).getFirstElement();
  }

  private static IWorkbenchWindow workbenchWindow(IWorkbenchPage page) {
    return proxy(IWorkbenchWindow.class, (proxy, method, arguments) -> switch (method.getName()) {
      case "getActivePage" -> page;
      case "equals" -> proxy == arguments[0];
      case "hashCode" -> System.identityHashCode(proxy);
      case "toString" -> "TestWorkbenchWindow";
      default -> defaultValue(method.getReturnType());
    });
  }

  private static final class ProjectState implements InvocationHandler {
    private final String name;
    private final boolean javaProject;
    private boolean open;

    private ProjectState(String name, boolean open, boolean javaProject) {
      this.name = name;
      this.open = open;
      this.javaProject = javaProject;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] arguments) {
      return switch (method.getName()) {
        case "getName" -> name;
        case "getType" -> IResource.PROJECT;
        case "isOpen" -> open;
        case "hasNature" -> javaProject;
        case "equals" -> proxy == arguments[0];
        case "hashCode" -> System.identityHashCode(proxy);
        case "toString" -> name;
        default -> defaultValue(method.getReturnType());
      };
    }
  }

  private static final class ViewState implements InvocationHandler {
    private final TestSelectionProvider selectionProvider = new TestSelectionProvider();
    private final IViewSite site;
    private final IViewPart view;
    private int focusCount;

    private ViewState() {
      this(true, true);
    }

    private ViewState(boolean hasSite, boolean hasSelectionProvider) {
      site = hasSite ? proxy(IViewSite.class, (proxy, method, arguments) -> switch (method.getName()) {
        case "getSelectionProvider" -> hasSelectionProvider ? selectionProvider : null;
        case "equals" -> proxy == arguments[0];
        case "hashCode" -> System.identityHashCode(proxy);
        default -> defaultValue(method.getReturnType());
      }) : null;
      view = proxy(IViewPart.class, this);
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] arguments) {
      return switch (method.getName()) {
        case "getViewSite", "getSite" -> site;
        case "setFocus" -> {
          focusCount++;
          yield null;
        }
        case "equals" -> proxy == arguments[0];
        case "hashCode" -> System.identityHashCode(proxy);
        default -> defaultValue(method.getReturnType());
      };
    }
  }

  private static final class PageState implements InvocationHandler {
    private final List<IViewReference> references = new ArrayList<>();
    private final Map<IViewPart, Boolean> visibility = new IdentityHashMap<>();
    private final IWorkbenchPage page = proxy(IWorkbenchPage.class, this);
    private IViewPart showViewResult;
    private PartInitException showViewFailure;
    private String shownViewId;
    private IWorkbenchPart activePart;

    private void addReference(String id, IViewPart view, boolean visible) {
      references.add(proxy(IViewReference.class, (proxy, method, arguments) -> switch (method.getName()) {
        case "getId" -> id;
        case "getView" -> view;
        case "equals" -> proxy == arguments[0];
        case "hashCode" -> System.identityHashCode(proxy);
        default -> defaultValue(method.getReturnType());
      }));
      visibility.put(view, visible);
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] arguments) throws Throwable {
      return switch (method.getName()) {
        case "getViewReferences" -> references.toArray(IViewReference[]::new);
        case "isPartVisible" -> visibility.getOrDefault(arguments[0], false);
        case "getActivePart" -> activePart;
        case "showView" -> {
          shownViewId = (String) arguments[0];
          if (showViewFailure != null) {
            throw showViewFailure;
          }
          visibility.put(showViewResult, true);
          yield showViewResult;
        }
        case "equals" -> proxy == arguments[0];
        case "hashCode" -> System.identityHashCode(proxy);
        default -> defaultValue(method.getReturnType());
      };
    }
  }

  private static final class TestSelectionProvider implements ISelectionProvider {
    private ISelection selection;

    @Override
    public void addSelectionChangedListener(ISelectionChangedListener listener) {
    }

    @Override
    public ISelection getSelection() {
      return selection;
    }

    @Override
    public void removeSelectionChangedListener(ISelectionChangedListener listener) {
    }

    @Override
    public void setSelection(ISelection selection) {
      this.selection = selection;
    }
  }

  @SuppressWarnings("unchecked")
  private static <T> T proxy(Class<T> type, InvocationHandler handler) {
    return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type }, handler);
  }

  private static Object defaultValue(Class<?> returnType) {
    if (!returnType.isPrimitive()) {
      return null;
    }
    if (returnType == boolean.class) {
      return false;
    }
    if (returnType == char.class) {
      return '\0';
    }
    return 0;
  }
}
