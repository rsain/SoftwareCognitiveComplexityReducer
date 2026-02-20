package neo.reducecognitivecomplexity.app;

import org.eclipse.core.runtime.Plugin;
import org.osgi.framework.BundleContext;

/**
 * The activator class controls the plug-in life cycle.
 * <p>
 * This class serves as the entry point for the Refactoring Plugin within the
 * Eclipse OSGi framework. It manages the singleton instance of the plugin and
 * is responsible for initializing and disposing of bundle-level resources when
 * the plugin is started or stopped.
 * </p>
 */
public class Activator extends Plugin {

	/**
	 * The shared singleton instance of the plugin.
	 */
	private static Activator plugin;

	/**
	 * Constructs a new Activator.
	 * <p>
	 * <b>Note:</b> This constructor is instantiated exclusively by the Eclipse
	 * platform (OSGi framework). Client code must not instantiate this class
	 * directly; use {@link #getDefault()} to access the shared instance.
	 * </p>
	 */
	public Activator() {
	}

	/**
	 * Starts the bundle.
	 * <p>
	 * This method is automatically invoked by the OSGi framework when the plugin is
	 * activated. It initializes the shared singleton instance, allowing other parts
	 * of the application to access plugin resources.
	 * </p>
	 *
	 * @param context the bundle context for this plug-in
	 * @throws Exception if the plug-in fails to initialize properly
	 */
	@Override
	public void start(final BundleContext context) throws Exception {
		super.start(context);
		plugin = this;
	}

	/**
	 * Stops the bundle.
	 * <p>
	 * This method is automatically invoked by the OSGi framework when the plugin is
	 * de-activated. It clears the shared singleton instance and releases any
	 * allocated resources to ensure a clean shutdown.
	 * </p>
	 *
	 * @param context the bundle context for this plug-in
	 * @throws Exception if the plug-in fails to shut down properly
	 */
	@Override
	public void stop(final BundleContext context) throws Exception {
		plugin = null;
		super.stop(context);
	}

	/**
	 * Returns the shared instance.
	 * <p>
	 * This method provides access to the singleton {@link Activator} instance,
	 * which can be used to retrieve preferences, image registries, or the plugin's
	 * log.
	 * </p>
	 *
	 * @return the shared singleton instance of the {@link Activator}
	 */
	public static Activator getDefault() {
		return plugin;
	}
}