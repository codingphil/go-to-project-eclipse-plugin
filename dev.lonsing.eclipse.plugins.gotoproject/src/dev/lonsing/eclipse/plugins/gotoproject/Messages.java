package dev.lonsing.eclipse.plugins.gotoproject;

import org.eclipse.osgi.util.NLS;

public class Messages extends NLS {
  private static final String BUNDLE_NAME = "dev.lonsing.eclipse.plugins.gotoproject.messages"; //$NON-NLS-1$
  public static String GoToProjectHandler_FailedToCheckProjectHandlerExceptionMessage;
  public static String GoToProjectSelectionDialog_SelectProjectDialogMessage;
  public static String GoToProjectSelectionDialog_SelectProjectDialogTitle;
  static {
    // initialize resource bundle
    NLS.initializeMessages(BUNDLE_NAME, Messages.class);
  }

  private Messages() {
  }
}
