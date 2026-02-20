package neo.reducecognitivecomplexity.core.jdt;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodDeclaration;

import neo.reducecognitivecomplexity.core.eclipse.Utils;

/**
 * A processor that analyzes Java projects to extract
 * method information and calculate cognitive complexity using AST parsing. *
 * <p>
 * This class orchestrates the traversal of the Java Model (Projects -> Packages
 * -> Units) and delegates the actual metric calculation to visitors.
 * </p>
 */
public class JavaMethodProcessor {

	private static final Logger LOGGER = Logger.getLogger(JavaMethodProcessor.class.getName());

	/**
	 * Immutable record holding the complexity data for a specific method.
	 */
	public static class MethodComplexityRecord {
		/** The simple name of the method. */
		public final String methodName;
		/** The starting line number of the method in the source file. */
		public final int lineNumber;
		/** The calculated cognitive complexity score. */
		public final int complexity;
		/** The AST node representing the method declaration. */
		public final MethodDeclaration methodDeclaration;

		public MethodComplexityRecord(String methodName, int lineNumber, int complexity,
				MethodDeclaration methodDeclaration) {
			this.methodName = methodName;
			this.lineNumber = lineNumber;
			this.complexity = complexity;
			this.methodDeclaration = methodDeclaration;
		}
	}

	/**
	 * Container class holding the aggregate results of a project processing run.
	 */
	public static final class ProcessingResult {
		/** The name of the processed project. */
		public final String projectName;
		/** Total number of methods successfully analyzed in the project. */
		public final int totalMethods;
		/** List of error messages encountered during processing. */
		public final List<String> errors;
		/** Map linking AST CompilationUnits to their list of method records. */
		public final Map<CompilationUnit, List<MethodComplexityRecord>> unitComplexities;

		ProcessingResult(String projectName, int totalMethods, List<String> errors,
				Map<CompilationUnit, List<MethodComplexityRecord>> unitComplexities) {
			this.projectName = projectName;
			this.totalMethods = totalMethods;
			this.errors = errors;
			this.unitComplexities = unitComplexities;
		}
	}

	// =========================================================================
	// PUBLIC API
	// =========================================================================

	/**
	 * Main entry point for processing a Java project.
	 * <p>
	 * Initializes the workspace environment, locates the project, and traverses its
	 * hierarchy to calculate complexity metrics for all methods.
	 * </p>
	 * * @param projectName The name of the Eclipse project to process.
	 * 
	 * @return A {@link ProcessingResult} object containing the outcome and data.
	 */
	public ProcessingResult processProject(String projectName) {
		LOGGER.info("Starting processing of project: " + projectName);

		try {
			// Ensure Eclipse environment is ready (headless setup)
			Utils.initializeWorkspace();

			// Safely process the project and get results
			ProcessingResult result = processProjectSafely(projectName);

			printResults(result);
			return result;

		} catch (Exception e) {
			LOGGER.severe("Fatal error during processing: " + e.getMessage());
			e.printStackTrace();

			// Return a safe empty error result
			List<String> fatalError = new ArrayList<>();
			fatalError.add(e.getMessage());
			return new ProcessingResult(projectName, 0, fatalError, new HashMap<>());
		}
	}

	// =========================================================================
	// INTERNAL PROCESSING LOGIC
	// =========================================================================

	private ProcessingResult processProjectSafely(String projectName) {
		List<String> errors = new ArrayList<>();
		Map<CompilationUnit, List<MethodComplexityRecord>> unitComplexities = new HashMap<>();
		int totalMethods = 0;

		try {
			IProject project = Utils.getProject(projectName);
			if (project == null) {
				errors.add("Project not found in workspace: " + projectName);
				return new ProcessingResult(projectName, 0, errors, unitComplexities);
			}

			IJavaProject javaProject = Utils.getJavaProject(project);
			if (javaProject == null) {
				errors.add("Project is not a Java project or cannot be accessed: " + projectName);
				return new ProcessingResult(projectName, 0, errors, unitComplexities);
			}

			totalMethods = processJavaProject(javaProject, errors, unitComplexities);

		} catch (Exception e) {
			errors.add("Critical error processing project '" + projectName + "': " + e.getClass().getSimpleName()
					+ " - " + e.getMessage());
		}

		return new ProcessingResult(projectName, totalMethods, errors, unitComplexities);
	}

	private int processJavaProject(IJavaProject javaProject, List<String> errors,
			Map<CompilationUnit, List<MethodComplexityRecord>> unitComplexities) {
		int totalMethods = 0;

		try {
			IPackageFragmentRoot[] roots = javaProject.getPackageFragmentRoots();
			LOGGER.info("Found " + roots.length + " package fragment roots");

			for (IPackageFragmentRoot root : roots) {
				// Only process source folders (skip libraries/jars)
				if (root.getKind() == IPackageFragmentRoot.K_SOURCE) {
					totalMethods += processPackageFragmentRoot(root, errors, unitComplexities);
				}
			}
		} catch (CoreException e) {
			errors.add("Error accessing package fragment roots in project '" + javaProject.getElementName() + "': "
					+ e.getMessage());
		}

		return totalMethods;
	}

	private int processPackageFragmentRoot(IPackageFragmentRoot root, List<String> errors,
			Map<CompilationUnit, List<MethodComplexityRecord>> unitComplexities) {
		int methodCount = 0;

		try {
			IJavaElement[] children = root.getChildren();

			for (IJavaElement element : children) {
				if (element instanceof IPackageFragment) {
					IPackageFragment packageFragment = (IPackageFragment) element;
					methodCount += processPackageFragment(packageFragment, errors, unitComplexities);
				}
			}
		} catch (JavaModelException e) {
			errors.add("Error processing package fragment root '" + root.getElementName() + "': " + e.getMessage());
		}

		return methodCount;
	}

	private int processPackageFragment(IPackageFragment packageFragment, List<String> errors,
			Map<CompilationUnit, List<MethodComplexityRecord>> unitComplexities) {
		int methodCount = 0;

		try {
			ICompilationUnit[] units = packageFragment.getCompilationUnits();

			for (ICompilationUnit unit : units) {
				methodCount += processICompilationUnit(unit, errors, unitComplexities);
			}
		} catch (JavaModelException e) {
			errors.add("Error accessing compilation units in package '" + packageFragment.getElementName() + "': "
					+ e.getMessage());
		}

		return methodCount;
	}

	private int processICompilationUnit(ICompilationUnit unit, List<String> errors,
	        Map<CompilationUnit, List<MethodComplexityRecord>> unitComplexities) {
	    try {
	        CompilationUnit compilationUnit = neo.reducecognitivecomplexity.core.jdt.Utils.parse(unit);
	        if (compilationUnit == null) return 0;

	        return processCompilationUnit(compilationUnit, errors, unitComplexities);
	    } catch (Exception e) {
	        errors.add("Error parsing unit '" + unit.getElementName() + "': " + e.getMessage());
	        return 0;
	    }
	}

	public int processCompilationUnit(CompilationUnit compilationUnit, List<String> errors,
	        Map<CompilationUnit, List<MethodComplexityRecord>> unitComplexities) {
	    
	    final List<MethodDeclaration> methods = new ArrayList<>();
	    String methodUnderProcessing = "Init";

	    try {
	        // 1. Collect all methods
	        compilationUnit.accept(new MethodCollectorVisitor(methods));

	        List<MethodComplexityRecord> currentUnitRecords = new ArrayList<>();

	        // 2. Analyze each method
	        for (MethodDeclaration method : methods) {
	            String methodName = method.getName().getIdentifier();
	            methodUnderProcessing = methodName;

	            // Get line number from AST
	            int lineNumber = compilationUnit.getLineNumber(method.getStartPosition());

	            // Calculate Complexity
	            CognitiveComplexityVisitor.Result result = CognitiveComplexityVisitor.methodComplexity(method);

	            currentUnitRecords.add(new MethodComplexityRecord(methodName, lineNumber, result.complexity, method));
	        }

	        if (!currentUnitRecords.isEmpty()) {
	            unitComplexities.put(compilationUnit, currentUnitRecords);
	        }

	        return methods.size();

	    } catch (Exception e) {
	        errors.add("Error processing AST for '" + compilationUnit.getJavaElement().getElementName() + "', method '" 
	                + methodUnderProcessing + "': " + e.getMessage());
	        return 0;
	    }
	}

	private void printResults(ProcessingResult result) {
		System.out.println("\n" + "=".repeat(50));
		System.out.println("PROCESSING SUMMARY");
		System.out.println("=".repeat(50));
		System.out.println("Project: " + result.projectName);
		System.out.println("Total methods found: " + result.totalMethods);
		System.out.println("Total units processed: " + result.unitComplexities.size());

		if (!result.errors.isEmpty()) {
			System.out.println("\nErrors encountered (" + result.errors.size() + "):");
			for (String error : result.errors) {
				System.out.println("  - " + error);
			}
		} else {
			System.out.println("Status: SUCCESS - No errors encountered");
		}
		System.out.println("=".repeat(50));
	}

	// =========================================================================
	// HELPER VISITORS
	// =========================================================================

	/**
	 * Simple AST Visitor to collect all MethodDeclaration nodes into a list.
	 */
	private static class MethodCollectorVisitor extends ASTVisitor {
		private final List<MethodDeclaration> methods;

		MethodCollectorVisitor(List<MethodDeclaration> methods) {
			this.methods = methods;
		}

		@Override
		public boolean visit(MethodDeclaration method) {
			methods.add(method);
			return true;
		}
	}
}