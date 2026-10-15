package neo.reducecognitivecomplexity.core;

/**
 * An immutable data container representing a specific Java method targeted for refactoring.
 * <p>
 * This class maps directly to a single row parsed from the batch processing CSV file. 
 * It holds the exact location identifiers required by the Eclipse JDT to locate 
 * the corresponding {@link org.eclipse.jdt.core.dom.MethodDeclaration} within the workspace.
 * </p>
 */
public class TargetMethod {
    
    /** * The name of the Eclipse project containing the method (e.g., "bytecode-viewer"). 
     */
    public final String project;
    
    /** * The relative path to the source folder within the project (e.g., "src/main/java"). 
     */
    public final String sourceFolder;
    
    /** * The package directory path or package name containing the class (e.g., "the/bytecode/club/..."). 
     */
    public final String packageName;
    
    /** * The name of the Java class file containing the method (e.g., "ProcyonDecompiler.java"). 
     */
    public final String className;
    
    /** * The exact name of the method to be analyzed and refactored (e.g., "doSaveJarDecompiled"). 
     */
    public final String methodName;

    /**
     * The line number (1-based) where the target method is declared in the
     * source file, as reported by whatever tool generated the input CSV, or
     * {@code -1} if the input row did not specify one (older 5-column CSV
     * format).
     * <p>
     * When known, this is used to pinpoint the EXACT overload this row
     * refers to, instead of matching every method sharing {@link #methodName}
     * in the class - see {@link BatchCsvProcessor#processCsv(String)}. This
     * matters because a class can have several overloaded methods with the
     * same name, each individually exceeding the complexity threshold and
     * therefore each listed as its OWN row (with its own line number) in the
     * input CSV.
     * </p>
     */
    public final int lineNumber;

    /**
     * Constructs a new {@code TargetMethod} instance with the specified location coordinates.
     *
     * @param project      the name of the target Eclipse project
     * @param sourceFolder the source folder path
     * @param packageName  the package name or directory path
     * @param className    the name of the class (with or without the .java extension)
     * @param methodName   the precise name of the target method
     */
    public TargetMethod(String project, String sourceFolder, String packageName, String className, String methodName) {
        this(project, sourceFolder, packageName, className, methodName, -1);
    }

    /**
     * Constructs a new {@code TargetMethod} instance with the specified location coordinates,
     * including the specific line number of the intended overload.
     *
     * @param project      the name of the target Eclipse project
     * @param sourceFolder the source folder path
     * @param packageName  the package name or directory path
     * @param className    the name of the class (with or without the .java extension)
     * @param methodName   the precise name of the target method
     * @param lineNumber   the line number of the specific overload this row refers to,
     *                     or a negative value if unknown/not specified.
     */
    public TargetMethod(String project, String sourceFolder, String packageName, String className, String methodName,
            int lineNumber) {
        this.project = project;
        this.sourceFolder = sourceFolder;
        this.packageName = packageName;
        this.className = className;
        this.methodName = methodName;
        this.lineNumber = lineNumber;
    }
}