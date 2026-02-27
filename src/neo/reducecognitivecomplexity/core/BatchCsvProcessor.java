package neo.reducecognitivecomplexity.core;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import org.eclipse.core.resources.IProject;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodDeclaration;

import neo.reducecognitivecomplexity.app.Config;
import neo.reducecognitivecomplexity.app.Constants;
import neo.reducecognitivecomplexity.core.io.CsvResultWriter;
import neo.reducecognitivecomplexity.core.jdt.JavaMethodProcessor.MethodComplexityRecord;
import neo.reducecognitivecomplexity.core.eclipse.Utils;
import neo.reducecognitivecomplexity.core.jdt.CognitiveComplexityVisitor;

/**
 * Processes a batch of target methods defined in an external CSV file.
 * <p>
 * This class orchestrates the automated analysis and refactoring of specific methods
 * across multiple projects within the Eclipse workspace. For each target method specified
 * in the input CSV, it performs the following steps:
 * <ol>
 * <li>Resolves the corresponding {@link IProject} and {@link IJavaProject}.</li>
 * <li>Locates the specific class and parses it into an Abstract Syntax Tree (AST).</li>
 * <li>Finds the target {@link MethodDeclaration} by name.</li>
 * <li>Calculates the initial cognitive complexity of the method.</li>
 * <li>Passes the method to the {@link MethodRefactoringPipeline} for refactoring optimization.</li>
 * </ol>
 * </p>
 * <p>
 * It handles errors gracefully on a per-method basis, ensuring that a failure in one
 * target does not halt the entire batch process.
 * </p>
 */
public class BatchCsvProcessor {

    private static final Logger LOGGER = Logger.getLogger(BatchCsvProcessor.class.getName());

    private final Config config;
    private final CsvResultWriter resultWriter;

    /**
     * Initializes the processor with the necessary dependencies.
     *
     * @param config       The application configuration settings (e.g., selected solver, graph generation flags).
     * @param resultWriter The centralized writer used to output analysis metrics and refactoring results.
     */
    public BatchCsvProcessor(Config config, CsvResultWriter resultWriter) {
        this.config = config;
        this.resultWriter = resultWriter;
    }

    /**
     * Executes the batch processing workflow using the provided CSV file.
     * <p>
     * The CSV file is expected to have a header row, followed by rows containing:
     * {@code project;sourceFolder;package;class;method}
     * </p>
     * * @param csvFilePath The absolute or relative path to the input CSV file containing target methods.
     */
    public void processCsv(String csvFilePath) {
        List<TargetMethod> targets = readCsv(csvFilePath);
        LOGGER.info("Found " + targets.size() + " target methods in CSV.");

        // Initialize the pipeline once
        MethodRefactoringPipeline pipeline = new MethodRefactoringPipeline(config, resultWriter);

        for (TargetMethod target : targets) {
            try {
                LOGGER.info("=======================================================");
                LOGGER.info("Processing target: " + target.project + " -> " + target.className + "." + target.methodName);
                
                // --- RESOLVE PROJECT ---
                IProject project = Utils.getProject(target.project);
                if (project == null) continue;

                IJavaProject javaProject = Utils.getJavaProject(project);
                if (javaProject == null) continue;

                // --- FORMAT CSV DATA FOR JDT ---
                String formattedPackage = target.packageName.replace("/", ".").replace("\\", ".");
                String formattedClass = target.className;
                if (formattedClass.endsWith(".java")) {
                    formattedClass = formattedClass.substring(0, formattedClass.length() - 5);
                }

                String fullyQualifiedName = formattedPackage.isEmpty() ? 
                        formattedClass : formattedPackage + "." + formattedClass;

                // --- FIND COMPILATION UNIT ---
                IType type = javaProject.findType(fullyQualifiedName);
                if (type == null) {
                    LOGGER.warning("Could not find class " + fullyQualifiedName);
                    continue;
                }

                ICompilationUnit icu = type.getCompilationUnit();
                if (icu == null || !icu.exists()) continue;

                // --- PARSE AST AND FIND METHOD ---
                CompilationUnit astRoot = neo.reducecognitivecomplexity.core.jdt.Utils.parse(icu);
                neo.reducecognitivecomplexity.core.jdt.Utils.MethodDeclarationFinderVisitor finder = 
                        new neo.reducecognitivecomplexity.core.jdt.Utils.MethodDeclarationFinderVisitor(astRoot, target.methodName, null);

                if (!finder.found()) {
                    LOGGER.warning("Could not find method " + target.methodName + " in class " + formattedClass);
                    continue;
                }

                MethodDeclaration methodNode = finder.getMethodDeclaration();
                
                // Extract the starting line number from the AST root
                int lineNumber = astRoot.getLineNumber(methodNode.getStartPosition());

                // --- BUILD THE RECORD AND EXECUTE PIPELINE ---
                
                // Calculate the real initial cognitive complexity (just like JavaMethodProcessor does)
                CognitiveComplexityVisitor.Result ccResult = CognitiveComplexityVisitor.methodComplexity(methodNode);
                int initialComplexity = ccResult.complexity;

                // Instantiate using the parameterized constructor
                MethodComplexityRecord record = new MethodComplexityRecord(
                        target.methodName, 
                        lineNumber, 
                        initialComplexity, 
                        methodNode
                );

                LOGGER.info("Method found! Initial CC is " + initialComplexity + ". Passing to Refactoring Pipeline...");
                
                // Execute the solver pipeline
                pipeline.process(astRoot, record);
                
                LOGGER.info("Successfully completed: " + target.methodName);

            } catch (Exception e) {
                LOGGER.severe("Error processing target " + target.className + "." + target.methodName + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    /**
     * Parses the CSV file into a list of {@link TargetMethod} objects.
     * <p>
     * Skips the first line (header) and splits the remaining lines using the 
     * delimiter defined in {@link Constants#CSV_SEPARATOR}.
     * </p>
     *
     * @param path The path to the CSV file.
     * @return A list of valid target methods extracted from the file.
     */
    private List<TargetMethod> readCsv(String path) {
        List<TargetMethod> targets = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            String line = br.readLine(); // Skip header
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] cols = line.split(Constants.CSV_SEPARATOR); // e.g. ";"
                
                if (cols.length >= 5) {
                    targets.add(new TargetMethod(cols[0].trim(), cols[1].trim(), cols[2].trim(), cols[3].trim(), cols[4].trim()));
                }
            }
        } catch (IOException e) {
            LOGGER.severe("Failed to read CSV file: " + path);
        }
        return targets;
    }
}