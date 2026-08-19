package dev.lonsing.eclipse.plugins.gotoproject.dialogs;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.jface.dialogs.DialogSettings;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class GoToProjectSelectionDialogUiTest {
  private static final int POLL_INTERVAL_MILLIS = 25;
  private static final long INTERACTION_TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(10);
  private final List<IProject> fixtureProjects = new ArrayList<>();

  @AfterEach
  void deleteFixtureProjects() throws Exception {
    for (IProject project : fixtureProjects.reversed()) {
      if (project.exists()) {
        project.delete(true, true, null);
      }
    }
  }

  @Test
  void sortsCaseInsensitivelyAndAcceptsSelection() throws Exception {
    IProject zeta = createProject("sort-zeta");
    IProject bravo = createProject("sort-Bravo");
    IProject alpha = createProject("sort-alpha");
    boolean[] patternEntered = { false };

    DialogResult result = runDialog(
        parent -> new TestDialog(parent, List.of(zeta, bravo, alpha)), dialog -> {
          Table table = findTable(dialog);
          if (table == null) {
            return false;
          }
          if (!patternEntered[0]) {
            ((Text) dialog.getPatternControl()).setText("sOrT");
            patternEntered[0] = true;
            return false;
          }
          if (table.getItemCount() != 3 || table.getSelectionIndex() != 0) {
            return false;
          }

          assertArrayEquals(new String[] { "sort-alpha", "sort-Bravo", "sort-zeta" }, itemTexts(table));
          click(dialog.button(IDialogConstants.OK_ID));
          return true;
        });

    assertEquals(Window.OK, result.returnCode());
    assertSame(alpha, result.selectedProject());
  }

  @Test
  void filtersWithCaseInsensitiveSearchPatternAndAcceptsMatch() throws Exception {
    IProject beta = createProject("BetaProject");
    IProject alpha = createProject("AlphaProject");
    IProject gamma = createProject("GammaProject");
    boolean[] patternEntered = { false };

    DialogResult result = runDialog(
        parent -> new TestDialog(parent, List.of(beta, alpha, gamma)), dialog -> {
          Table table = findTable(dialog);
          if (table == null) {
            return false;
          }
          if (!patternEntered[0]) {
            ((Text) dialog.getPatternControl()).setText("aLpHaP");
            patternEntered[0] = true;
            return false;
          }
          if (table.getItemCount() != 1 || !"AlphaProject".equals(table.getItem(0).getText())) {
            return false;
          }

          click(dialog.button(IDialogConstants.OK_ID));
          return true;
        });

    assertTrue(patternEntered[0]);
    assertEquals(Window.OK, result.returnCode());
    assertSame(alpha, result.selectedProject());
  }

  @Test
  void cancelReturnsNoSelection() throws Exception {
    IProject onlyProject = createProject("OnlyProject");
    boolean[] patternEntered = { false };
    DialogResult result = runDialog(parent -> new TestDialog(parent, List.of(onlyProject)), dialog -> {
      Table table = findTable(dialog);
      if (table == null) {
        return false;
      }
      if (!patternEntered[0]) {
        ((Text) dialog.getPatternControl()).setText("oNlY");
        patternEntered[0] = true;
        return false;
      }
      if (table.getItemCount() != 1) {
        return false;
      }

      click(dialog.button(IDialogConstants.CANCEL_ID));
      return true;
    });

    assertEquals(Window.CANCEL, result.returnCode());
    assertNull(result.selectedProject());
  }

  @Test
  void emptyProjectInputCanBeCancelledWithoutSelection() {
    DialogResult result = runDialog(parent -> new TestDialog(parent, List.of()), dialog -> {
      Table table = findTable(dialog);
      if (table == null || table.getItemCount() != 0) {
        return false;
      }

      click(dialog.button(IDialogConstants.CANCEL_ID));
      return true;
    });

    assertEquals(Window.CANCEL, result.returnCode());
    assertNull(result.selectedProject());
  }

  @Test
  void workspaceDialogIncludesClosedProject() throws Exception {
    IProject closedProject = createProject("closed-project-" + System.nanoTime());
    assertFalse(closedProject.isOpen());
    boolean[] patternEntered = { false };

    DialogResult result = runDialog(TestDialog::new, dialog -> {
      Table table = findTable(dialog);
      if (table == null) {
        return false;
      }
      if (!patternEntered[0]) {
        ((Text) dialog.getPatternControl()).setText(closedProject.getName());
        patternEntered[0] = true;
        return false;
      }
      if (table.getItemCount() != 1 || !closedProject.equals(table.getItem(0).getData())) {
        return false;
      }

      click(dialog.button(IDialogConstants.OK_ID));
      return true;
    });

    assertEquals(Window.OK, result.returnCode());
    assertSame(closedProject, result.selectedProject());
    assertFalse(result.selectedProject().isOpen());
  }

  private IProject createProject(String name) throws Exception {
    IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(name);
    project.create(null);
    fixtureProjects.add(project);
    return project;
  }

  private static DialogResult runDialog(DialogFactory dialogFactory, DialogInteraction interaction) {
    Display display = Display.getCurrent();
    assertNotNull(display, "Tycho must run UI tests on the SWT UI thread");

    Shell parent = new Shell(display);
    TestDialog dialog = dialogFactory.create(parent);
    PollingDriver driver = new PollingDriver(display, dialog, interaction);
    int returnCode;
    IProject selectedProject;

    try {
      parent.open();
      display.timerExec(POLL_INTERVAL_MILLIS, driver);
      returnCode = dialog.open();
      selectedProject = dialog.getSelectedProject();
    } finally {
      display.timerExec(-1, driver);
      dialog.close();
      if (!parent.isDisposed()) {
        parent.dispose();
      }
    }

    driver.rethrowFailure();
    assertTrue(driver.completed(), "Dialog closed before the scheduled interaction completed");
    return new DialogResult(returnCode, selectedProject);
  }

  private static Table findTable(TestDialog dialog) {
    if (dialog.getShell() == null || dialog.getShell().isDisposed()) {
      return null;
    }
    return findTable(dialog.getShell());
  }

  private static Table findTable(org.eclipse.swt.widgets.Composite parent) {
    for (org.eclipse.swt.widgets.Control child : parent.getChildren()) {
      if (child instanceof Table table) {
        return table;
      }
      if (child instanceof org.eclipse.swt.widgets.Composite composite) {
        Table table = findTable(composite);
        if (table != null) {
          return table;
        }
      }
    }
    return null;
  }

  private static String[] itemTexts(Table table) {
    return List.of(table.getItems()).stream().map(TableItem::getText).toArray(String[]::new);
  }

  private static void click(Button button) {
    assertNotNull(button);
    Event event = new Event();
    event.widget = button;
    button.notifyListeners(SWT.Selection, event);
  }

  private record DialogResult(int returnCode, IProject selectedProject) {
  }

  @FunctionalInterface
  private interface DialogFactory {
    TestDialog create(Shell parent);
  }

  @FunctionalInterface
  private interface DialogInteraction {
    boolean run(TestDialog dialog) throws Exception;
  }

  private static final class TestDialog extends GoToProjectSelectionDialog {
    private final IDialogSettings dialogSettings = new DialogSettings("GoToProjectSelectionDialogUiTest");

    private TestDialog(Shell shell) {
      super(shell);
    }

    private TestDialog(Shell shell, List<IProject> projects) {
      super(shell, projects);
    }

    private Button button(int id) {
      return getButton(id);
    }

    @Override
    protected IDialogSettings getDialogSettings() {
      return dialogSettings;
    }
  }

  private static final class PollingDriver implements Runnable {
    private final Display display;
    private final TestDialog dialog;
    private final DialogInteraction interaction;
    private final long deadlineNanos = System.nanoTime() + INTERACTION_TIMEOUT_NANOS;
    private Throwable failure;
    private boolean completed;

    private PollingDriver(Display display, TestDialog dialog, DialogInteraction interaction) {
      this.display = display;
      this.dialog = dialog;
      this.interaction = interaction;
    }

    @Override
    public void run() {
      try {
        if (interaction.run(dialog)) {
          completed = true;
          return;
        }
        if (System.nanoTime() >= deadlineNanos) {
          throw new AssertionError("Timed out waiting for the modal dialog state: " + describeDialog(dialog));
        }
        display.timerExec(POLL_INTERVAL_MILLIS, this);
      } catch (Throwable throwable) {
        failure = throwable;
        dialog.close();
      }
    }

    private boolean completed() {
      return completed;
    }

    private void rethrowFailure() {
      if (failure instanceof AssertionError assertionError) {
        throw assertionError;
      }
      if (failure != null) {
        fail("Scheduled modal dialog interaction failed", failure);
      }
    }

    private static String describeDialog(TestDialog dialog) {
      Table table = findTable(dialog);
      if (table == null) {
        return "table unavailable";
      }
      return "itemCount=" + table.getItemCount() + ", selectionIndex=" + table.getSelectionIndex()
          + ", items=" + String.join(",", itemTexts(table));
    }
  }
}
