package neo.reducecognitivecomplexity.core.eclipse;

import java.io.File;
import java.io.IOException;
import java.util.logging.Logger;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceDescription;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.launching.IVMInstall;
import org.eclipse.jdt.launching.IVMInstallType;
import org.eclipse.jdt.launching.JavaRuntime;
import org.eclipse.jdt.launching.VMStandin;
import org.eclipse.swt.widgets.Display;

import neo.reducecognitivecomplexity.app.Activator;

/**
 * Utility class for managing the Eclipse workspace and runtime environment.
 * <p>
 * This class provides helper methods to:
 * <ul>
 * <li>Initialize the SWT Display for headless operations.</li>
 * <li>Register the running JVM so JDT can resolve system classes (e.g.,
 * java.lang.Object).</li>
 * <li>Disable automatic workspace building to improve performance.</li>
 * <li>Retrieve and initialize projects within the workspace.</li>
 * </ul>
 */
public class Utils {

	private static final Logger LOGGER = Logger.getLogger(Utils.class.getName());

	// Track if workspace is initialized to prevent redundant setup
	private static boolean workspaceInitialized = false;

	// =========================================================================
	// ENVIRONMENT SETUP
	// =========================================================================

	/**
	 * Initializes the SWT Display and registers the running JRE.
	 * <p>
	 * Even for headless applications, initializing the default Display can be
	 * required by some Eclipse workbench features (like refactoring processors)
	 * that implicitly depend on UI threads or resource mapping.
	 * </p>
	 *
	 * @throws CoreException if JRE registration fails.
	 */
	public static void initializeEnvironment() throws CoreException {
		// Initialize SWT Display (required for some JDT internals even in headless)
		Display.getDefault();

		// Ensure JDT has a JDK to work with
		registerRunningJRE();
	}

	/**
	 * Ensures the Eclipse workspace and JDT preferences are initialized.
	 * <p>
	 * This method attempts to access the workspace root and initialize JDT UI
	 * preference nodes. This is often necessary in headless modes to prevent
	 * NullPointerExceptions when JDT tries to access formatting or code generation
	 * settings.
	 * </p>
	 */
	public static void initializeWorkspace() {
		if (!workspaceInitialized) {
			try {
				if (ResourcesPlugin.getWorkspace() == null) {
					throw new RuntimeException("Workspace is not available");
				}
				// Force workspace load
				ResourcesPlugin.getWorkspace().getRoot().getProjects();

				// Initialize JDT Preferences
				try {
					org.eclipse.core.runtime.preferences.InstanceScope.INSTANCE.getNode("org.eclipse.jdt.ui");
					org.eclipse.core.runtime.preferences.DefaultScope.INSTANCE.getNode("org.eclipse.jdt.ui");
					LOGGER.info("JDT UI Preferences Initialized");
				} catch (Exception e) {
					LOGGER.warning("Warning: Could not init JDT UI prefs: " + e.getMessage());
				}

				workspaceInitialized = true;
				LOGGER.info("Workspace initialized successfully");
			} catch (IllegalStateException e) {
				LOGGER.info("Workspace may not be fully ready, but continuing...");
				workspaceInitialized = true;
			} catch (Exception e) {
				throw new RuntimeException("Failed to initialize workspace: " + e.getMessage(), e);
			}
		}
	}

	/**
	 * Registers the currently running JVM as the default JRE in the Eclipse
	 * instance.
	 * <p>
	 * <b>Why is this needed?</b><br>
	 * When running a "Headless" Eclipse application (without the UI), the
	 * "Installed JREs" preference list is often empty. Without a registered JRE,
	 * the JDT cannot resolve basic system types like {@code java.lang.Object},
	 * causing AST parsing and binding resolution to fail.
	 * </p>
	 * * @throws CoreException if the VM installation cannot be created or set as
	 * default.
	 */
	public static void registerRunningJRE() throws CoreException {
		LOGGER.info(">> [JRE-REGISTRY] Checking for registered VMs...");

		if (JavaRuntime.getDefaultVMInstall() != null) {
			LOGGER.info(">> [JRE-REGISTRY] Default VM is already set: " + JavaRuntime.getDefaultVMInstall().getName());
			return;
		}

		LOGGER.info(">> [JRE-REGISTRY] No Default VM found. Registering current process JVM...");

		// 1. Get the Standard VM Type
		IVMInstallType installType = JavaRuntime
				.getVMInstallType("org.eclipse.jdt.internal.debug.ui.launcher.StandardVMType");
		if (installType == null) {
			LOGGER.severe("!!! FATAL: StandardVMType not found. JDT Launching bundle is likely missing.");
			return;
		}

		// 2. Create a "Standin" VM based on the java.home of the running process
		File javaHome = new File(System.getProperty("java.home"));
		String vmName = "Headless-JVM";

		VMStandin vmStandin = new VMStandin(installType, vmName);
		vmStandin.setInstallLocation(javaHome);
		vmStandin.setName(vmName);

		// 3. Convert to a real VM Install and make it default
		IVMInstall realVM = vmStandin.convertToRealVM();
		JavaRuntime.setDefaultVMInstall(realVM, null);

		LOGGER.info(">> [JRE-REGISTRY] Success! Registered '" + vmName + "' at " + javaHome.getAbsolutePath());
	}

	/**
	 * Disables the Eclipse Workspace "Build Automatically" feature.
	 * <p>
	 * <b>Performance Optimization:</b> Refactoring operations modify the AST and
	 * underlying files frequently. If auto-build is on, Eclipse will try to
	 * re-compile the project after every single change. This method ensures
	 * auto-build is off before processing begins.
	 * </p>
	 */
	public static void disableAutoBuild() {
		try {
			IWorkspace workspace = ResourcesPlugin.getWorkspace();
			IWorkspaceDescription description = workspace.getDescription();
			if (description.isAutoBuilding()) {
				description.setAutoBuilding(false);
				workspace.setDescription(description);
				LOGGER.info("Workspace Auto-Build has been disabled for performance.");
			}
		} catch (CoreException e) {
			LOGGER.warning("Could not disable Auto-Build: " + e.getMessage());
		}
	}

	// =========================================================================
	// PROJECT & RESOURCE HELPERS
	// =========================================================================

	/**
	 * Retrieves a generic {@link IProject} by name, ensuring it is open and built.
	 * <p>
	 * This method performs the following steps:
	 * <ol>
	 * <li>Checks if the project exists.</li>
	 * <li>Opens the project if it is currently closed.</li>
	 * <li>Refreshes the project (depth infinite) to sync with the file system.</li>
	 * <li>Triggers a full build to ensure AST bindings are resolvable.</li>
	 * <li>Waits for the build to complete.</li>
	 * </ol>
	 * * @param projectName The name of the project to retrieve.
	 * 
	 * @return The initialized {@link IProject}, or {@code null} if it does not
	 *         exist.
	 * @throws RuntimeException if an error occurs while opening or building the
	 *                          project.
	 */
	public static IProject getProject(String projectName) {
		try {
			IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(projectName);

			if (!project.exists()) {
				LOGGER.info("Project does not exist: " + projectName);
				return null;
			}

			// 1. Open if necessary
			if (!project.isOpen()) {
				LOGGER.info("Opening closed project: " + projectName);
				project.open(new NullProgressMonitor());
			}

			// 2. Refresh and Build
			project.refreshLocal(IResource.DEPTH_INFINITE, new NullProgressMonitor());
			project.build(IncrementalProjectBuilder.FULL_BUILD, new NullProgressMonitor());
			waitForBuild();

			return project;

		} catch (Exception e) {
			throw new RuntimeException("Error accessing project: " + projectName, e);
		}
	}

	/**
	 * Converts a generic {@link IProject} into a {@link IJavaProject} if it has the
	 * Java nature. * @param project The Eclipse project resource.
	 * 
	 * @return The {@link IJavaProject} representation, or {@code null} if the
	 *         project does not have the Java nature.
	 * @throws RuntimeException if checking the project nature fails.
	 */
	public static IJavaProject getJavaProject(IProject project) {
		try {
			if (!project.hasNature(JavaCore.NATURE_ID)) {
				LOGGER.info("Project is not a Java project: " + project.getName());
				return null;
			}
			IJavaProject javaProject = JavaCore.create(project);
			LOGGER.info("Successfully created Java project for: " + project.getName());
			return javaProject;
		} catch (CoreException e) {
			throw new RuntimeException("Error checking project nature: " + project.getName(), e);
		}
	}

	/**
	 * Returns the absolute file system path to the plugin's installation directory.
	 * * @return the absolute path string of the bundle's file location, or
	 * {@code null} if an I/O error occurs.
	 */
	public static String getPluginPath() {
		try {
			if (Activator.getDefault() == null) {
				LOGGER.warning("Activator is null. Plugin path cannot be resolved.");
				return null;
			}
			return FileLocator.getBundleFile(Activator.getDefault().getBundle()).toString();
		} catch (IOException e) {
			LOGGER.severe("Failed to resolve plugin path: " + e.getMessage());
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * Helper method to block current thread execution until the Eclipse Auto-Build
	 * and Manual-Build jobs are finished.
	 */
	private static void waitForBuild() {
		try {
			Job.getJobManager().join(ResourcesPlugin.FAMILY_AUTO_BUILD, new NullProgressMonitor());
			Job.getJobManager().join(ResourcesPlugin.FAMILY_MANUAL_BUILD, new NullProgressMonitor());
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			LOGGER.warning("Interrupted while waiting for build.");
		}
	}

	/**
	 * Prints a list of all projects currently in the workspace to the console.
	 * Useful for debugging workspace resolution issues.
	 */
	public static void listAvailableProjects() {
		try {
			System.out.println("Available projects in workspace:");
			IProject[] projects = ResourcesPlugin.getWorkspace().getRoot().getProjects();
			if (projects.length == 0) {
				System.out.println("  No projects found in workspace");
			} else {
				for (IProject project : projects) {
					String status = project.isOpen() ? " (open)" : " (closed)";
					String javaNature = "";
					try {
						if (project.isOpen() && project.hasNature(JavaCore.NATURE_ID)) {
							javaNature = " [Java]";
						}
					} catch (CoreException e) {
						javaNature = " [Nature check failed]";
					}
					System.out.println("  - " + project.getName() + status + javaNature);
				}
			}
		} catch (Exception e) {
			LOGGER.severe("Could not list available projects: " + e.getMessage());
		}
	}
}