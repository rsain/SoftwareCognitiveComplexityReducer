package neo.reducecognitivecomplexity.core.solvers;

import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodDeclaration;

import neo.reducecognitivecomplexity.app.Config;
import neo.reducecognitivecomplexity.core.graphs.GraphBundle;
import neo.reducecognitivecomplexity.core.jdt.CompilationUnitPathExtractor;
import neo.reducecognitivecomplexity.core.jdt.JavaMethodProcessor.MethodComplexityRecord;
import neo.reducecognitivecomplexity.core.jdt.Utils;

/**
 * A Data Transfer Object (DTO) that encapsulates the environment and state 
 * required by a {@link neo.reducecognitivecomplexity.core.solvers.RefactoringSolver}.
 * <p>
 * This class holds references to the AST, the configuration, and optional resources
 * like pre-computed graphs. It implements {@link AutoCloseable} to ensure
 * heavy resources (like Graphs) are cleaned up after the solver finishes.
 * </p>
 */
public class SolverContext implements AutoCloseable {
    
    // AST & Metadata
    public final MethodDeclaration ast;
    public final CompilationUnit compilationUnit;
    public final MethodComplexityRecord record;
    
    // Configuration & Paths
    public final String algorithm;
    public final String project;
    public final String folder;
    public final String packageInProject;
    public final String classInPackage;

    // Heavy Resources (Managed)
    private GraphBundle graphs;

    /**
     * Creates a new solver context.
     * * @param unit   The compilation unit containing the method.
     * @param record The complexity record identifying the method.
     * @param config The application configuration.
     */
    public SolverContext(CompilationUnit unit, MethodComplexityRecord record, Config config) {
        this.ast = record.methodDeclaration;
        // Ensure we have the root CU. If 'unit' is null, try to derive it from AST.
        this.compilationUnit = (unit != null) ? unit : Utils.getCompilationUnit(ast);
        this.record = record;
        this.algorithm = config.getSolver();

        // Extract path components for file naming and logging
        CompilationUnitPathExtractor.PathComponents components = 
                CompilationUnitPathExtractor.computeAllComponents(this.compilationUnit);

        this.project = components.getProjectName();
        this.folder = components.getSourceFolder();
        this.packageInProject = components.getPackageName();
        this.classInPackage = components.getClassName();
    }

    /**
     * Stores pre-computed graphs to avoid rebuilding them inside the solver.
     */
    public void setPrecomputedGraphs(GraphBundle graphs) {
        this.graphs = graphs;
    }

    /**
     * @return The pre-computed graphs, or null if not available.
     */
    public GraphBundle getGraphs() {
        return graphs;
    }

    /**
     * Generates a standardized file prefix for solver outputs (Solutions, LP files, etc.).
     * <p>
     * Format: {@code ProjectName@SourceFolder-Package-File-MethodName-LineNumber}
     * </p>
     */
    public String generatePrefixForSolverFileNames() {
        // Re-compute components (lightweight) or use stored fields. 
        // Using stored fields to ensure consistency.
        return project + "@" + 
               folder.replace('/', '.') + "-" + 
               packageInProject.replace('/', '.') + "-" + 
               classInPackage + "-" + 
               record.methodName + "-" + 
               record.lineNumber;
    }

    @Override
    public void close() {
        // Automatically clear graphs to free memory when the try-with-resources block ends
        if (graphs != null) {
            graphs.clear();
            graphs = null;
        }
    }
}
