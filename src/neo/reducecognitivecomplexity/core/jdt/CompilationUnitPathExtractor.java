package neo.reducecognitivecomplexity.core.jdt;

import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.core.resources.*;
import org.eclipse.core.runtime.IPath;
import java.util.HashMap;
import java.util.Map;

/**
 * Utility class to extract various path and name components from a Java
 * Compilation Unit. Handles both JDT Java Model elements (ICompilationUnit) and
 * AST nodes (CompilationUnit).
 */
public class CompilationUnitPathExtractor {

	// =========================================================================
	// INDIVIDUAL STATIC GETTER METHODS
	// =========================================================================

	/**
	 * Get project name separately.
	 */
	public static String getProjectName(ICompilationUnit unit) {
		if (unit == null)
			return null;
		try {
			IJavaProject project = unit.getJavaProject();
			return project != null ? project.getElementName() : null;
		} catch (Exception e) {
			return null;
		}
	}

	/**
	 * Get source folder separately (e.g., "src/main/java"). Returns the path
	 * relative to the project.
	 */
	public static String getSourceFolder(ICompilationUnit unit) {
		if (unit == null)
			return null;
		try {
			IJavaElement parent = unit.getParent(); // Package Fragment
			while (parent != null) {
				if (parent instanceof IPackageFragmentRoot) {
					IPackageFragmentRoot root = (IPackageFragmentRoot) parent;
					IJavaProject project = root.getJavaProject();
					IPath sourcePath = root.getPath().makeRelativeTo(project.getPath());
					return sourcePath.toString().replace('\\', '/');
				}
				parent = parent.getParent();
			}
		} catch (Exception e) {
			// Fallthrough
		}
		return null;
	}

	/**
	 * Get package directory separately (e.g., "com/example/myapp").
	 */
	public static String getPackageDirectory(ICompilationUnit unit) {
		if (unit == null)
			return null;
		try {
			IPackageFragment pkg = (IPackageFragment) unit.getParent();
			return pkg.getElementName().replace('.', '/');
		} catch (Exception e) {
			return null;
		}
	}

	/**
	 * Get file name separately (e.g., "MyClass.java").
	 */
	public static String getFileName(ICompilationUnit unit) {
		return unit != null ? unit.getElementName() : null;
	}

	/**
	 * Get class name separately without extension (e.g., "MyClass").
	 */
	public static String getClassName(ICompilationUnit unit) {
		if (unit == null)
			return null;
		String fileName = getFileName(unit);
		if (fileName != null && fileName.endsWith(".java")) {
			return fileName.substring(0, fileName.length() - 5);
		}
		return fileName;
	}

	/**
	 * Get package name separately (e.g., "com.example.myapp").
	 */
	public static String getPackageName(ICompilationUnit unit) {
		if (unit == null)
			return null;
		try {
			IPackageFragment pkg = (IPackageFragment) unit.getParent();
			return pkg.getElementName();
		} catch (Exception e) {
			return null;
		}
	}

	/**
	 * Get full package path (source folder + package directory). e.g.,
	 * "src/main/java/com/example/myapp"
	 */
	public static String getFullPackagePath(ICompilationUnit unit) {
		String sourceFolder = getSourceFolder(unit);
		String packageDir = getPackageDirectory(unit);

		if (sourceFolder == null)
			return null;
		if (packageDir == null || packageDir.isEmpty()) {
			return sourceFolder;
		}
		return sourceFolder + "/" + packageDir;
	}

	/**
	 * Get complete file path (project relative). e.g.,
	 * "src/main/java/com/example/myapp/MyClass.java"
	 */
	public static String getCompleteFilePath(ICompilationUnit unit) {
		String fullPackagePath = getFullPackagePath(unit);
		String fileName = getFileName(unit);

		if (fullPackagePath == null || fileName == null) {
			return null;
		}
		return fullPackagePath + "/" + fileName;
	}

	/**
	 * Get fully qualified class name. e.g., "com.example.myapp.MyClass"
	 */
	public static String getFullyQualifiedClassName(ICompilationUnit unit) {
		if (unit == null)
			return null;

		IType primaryType = unit.findPrimaryType();
		if (primaryType != null) {
			return primaryType.getFullyQualifiedName();
		}

		// Fallback
		String packageName = getPackageName(unit);
		String className = getClassName(unit);

		if (packageName == null || packageName.isEmpty()) {
			return className;
		}
		return packageName + "." + className;
	}

	// =========================================================================
	// PATH COMPONENTS CLASS
	// =========================================================================

	public static class PathComponents {
		private final String projectName;
		private final String sourceFolder;
		private final String packageDirectory;
		private final String fileName;
		private final String className;
		private final String packageName;

		/**
		 * Constructor that extracts all components from a provided ICompilationUnit.
		 */
		public PathComponents(ICompilationUnit unit) {
			this.projectName = CompilationUnitPathExtractor.getProjectName(unit);
			this.sourceFolder = CompilationUnitPathExtractor.getSourceFolder(unit);
			this.packageDirectory = CompilationUnitPathExtractor.getPackageDirectory(unit);
			this.fileName = CompilationUnitPathExtractor.getFileName(unit);
			this.className = CompilationUnitPathExtractor.getClassName(unit);
			this.packageName = CompilationUnitPathExtractor.getPackageName(unit);
		}

		/**
		 * Constructor for direct creation with known string values.
		 */
		public PathComponents(String projectName, String sourceFolder, String packageDirectory, String fileName,
				String className, String packageName) {
			this.projectName = projectName;
			this.sourceFolder = sourceFolder;
			this.packageDirectory = packageDirectory;
			this.fileName = fileName;
			this.className = className;
			this.packageName = packageName;
		}

		// ===== GETTERS =====

		public String getProjectName() {
			return projectName;
		}

		public String getSourceFolder() {
			return sourceFolder;
		}

		public String getPackageDirectory() {
			return packageDirectory;
		}

		public String getFileName() {
			return fileName;
		}

		public String getClassName() {
			return className;
		}

		public String getPackageName() {
			return packageName;
		}

		// ===== COMPUTED GETTERS =====

		public String getFullPackagePath() {
			if (sourceFolder == null)
				return null;
			if (packageDirectory == null || packageDirectory.isEmpty()) {
				return sourceFolder;
			}
			return sourceFolder + "/" + packageDirectory;
		}

		public String getProjectRelativeFilePath() {
			String fullPackagePath = getFullPackagePath();
			if (fullPackagePath == null || fileName == null) {
				return null;
			}
			return fullPackagePath + "/" + fileName;
		}

		public String getProjectRelativePathWithProject() {
			String relativePath = getProjectRelativeFilePath();
			if (projectName == null || relativePath == null) {
				return relativePath;
			}
			return projectName + "/" + relativePath;
		}

		public String getFullyQualifiedClassName() {
			if (packageName == null || packageName.isEmpty()) {
				return className;
			}
			return packageName + "." + className;
		}

		public String getWorkspaceAbsolutePath() {
			String path = getProjectRelativePathWithProject();
			return path != null ? "/" + path : null;
		}

		@Override
		public String toString() {
			return getProjectRelativePathWithProject();
		}

		public void printAll() {
			System.out.println("Project Name: " + projectName);
			System.out.println("Source Folder: " + sourceFolder);
			System.out.println("Package Directory: " + packageDirectory);
			System.out.println("File Name: " + fileName);
			System.out.println("Class Name: " + className);
			System.out.println("Package Name: " + packageName);
			System.out.println("Full Package Path: " + getFullPackagePath());
			System.out.println("Project Relative Path: " + getProjectRelativeFilePath());
			System.out.println("With Project: " + getProjectRelativePathWithProject());
			System.out.println("Fully Qualified Class: " + getFullyQualifiedClassName());
			System.out.println("Workspace Path: " + getWorkspaceAbsolutePath());
		}
	}

	// =========================================================================
	// FACTORY METHODS
	// =========================================================================

	/**
	 * Create PathComponents by computing all fields from an AST CompilationUnit.
	 */
	public static PathComponents computeAllComponents(CompilationUnit node) {
		if (node == null)
			return null;

		ITypeRoot root = node.getTypeRoot();
		if (root instanceof ICompilationUnit) {
			return new PathComponents((ICompilationUnit) root);
		}
		return null;
	}

	// =========================================================================
	// QUICK ACCESS METHODS
	// =========================================================================

	/**
	 * Quick method to get all components as a map.
	 */
	public static Map<String, String> getAllComponentsAsMap(ICompilationUnit unit) {
		Map<String, String> map = new HashMap<>();

		map.put("projectName", getProjectName(unit));
		map.put("sourceFolder", getSourceFolder(unit));
		map.put("packageDirectory", getPackageDirectory(unit));
		map.put("fileName", getFileName(unit));
		map.put("className", getClassName(unit));
		map.put("packageName", getPackageName(unit));
		map.put("fullPackagePath", getFullPackagePath(unit));
		map.put("completeFilePath", getCompleteFilePath(unit));
		map.put("fullyQualifiedClassName", getFullyQualifiedClassName(unit));

		return map;
	}

	/**
	 * Get just the essential info formatted as Project/Source/Package.Class.
	 */
	public static String getEssentialInfo(ICompilationUnit unit) {
		String project = getProjectName(unit);
		String source = getSourceFolder(unit);
		String pkg = getPackageName(unit);
		String cls = getClassName(unit);

		if (project == null || source == null || pkg == null || cls == null) {
			return null;
		}

		return String.format("%s/%s/%s.%s", project, source, pkg, cls);
	}

	/**
	 * Get workspace absolute path (from IResource).
	 */
	public static String getWorkspaceAbsolutePath(ICompilationUnit unit) {
		if (unit == null)
			return null;

		IResource resource = unit.getResource();
		if (resource != null) {
			return resource.getFullPath().toString().replace('\\', '/');
		}
		return null;
	}

	/**
	 * Get project relative path (from IResource).
	 */
	public static String getProjectRelativePath(ICompilationUnit unit) {
		if (unit == null)
			return null;

		IResource resource = unit.getResource();
		if (resource != null) {
			return resource.getProjectRelativePath().toString().replace('\\', '/');
		}
		return getCompleteFilePath(unit);
	}

	// =========================================================================
	// VALIDATION METHODS
	// =========================================================================

	public static boolean isValidCompilationUnit(ICompilationUnit unit) {
		if (unit == null)
			return false;
		// Check if it exists and is accessible
		return unit.exists() && unit.getParent() instanceof IPackageFragment;
	}

	public static boolean isInSourceFolder(ICompilationUnit unit) {
		try {
			IJavaElement parent = unit.getParent(); // Package Fragment
			if (parent != null) {
				IJavaElement root = parent.getParent(); // Package Fragment Root
				if (root instanceof IPackageFragmentRoot) {
					return ((IPackageFragmentRoot) root).getKind() == IPackageFragmentRoot.K_SOURCE;
				}
			}
			return false;
		} catch (Exception e) {
			return false;
		}
	}
}