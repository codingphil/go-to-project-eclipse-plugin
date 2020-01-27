package dev.lonsing.eclipse.plugins.gotoproject;

import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

/**
 * The activator class controls the plug-in life cycle
 */
public class GoToProjectPlugin extends AbstractUIPlugin {

  // The plug-in ID
  public static final String PLUGIN_ID = "dev.lonsing.eclipse.plugins.gotoproject"; //$NON-NLS-1$

  // The shared instance
  private static GoToProjectPlugin plugin;

  /**
   * The constructor
   */
  public GoToProjectPlugin() {
  }

  @Override
  public void start(BundleContext context) throws Exception {
    super.start(context);
    plugin = this;
  }

  @Override
  public void stop(BundleContext context) throws Exception {
    plugin = null;
    super.stop(context);
  }

  /**
   * Returns the shared instance
   *
   * @return the shared instance
   */
  public static GoToProjectPlugin getDefault() {
    return plugin;
  }

}
