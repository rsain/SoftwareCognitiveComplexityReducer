package neo.reducecognitivecomplexity.core;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.ltk.core.refactoring.Change;

import neo.reducecognitivecomplexity.app.Constants;
import neo.reducecognitivecomplexity.core.jdt.CodeExtractionMetrics;
import neo.reducecognitivecomplexity.core.jdt.CodeExtractionMetricsStats;
import neo.reducecognitivecomplexity.core.jdt.Utils;
import neo.reducecognitivecomplexity.core.jdt.Utils.MethodDeclarationFinderVisitor;
import neo.reducecognitivecomplexity.core.refactoringcache.RefactoringCache;
import neo.reducecognitivecomplexity.core.solvers.ilp.IlpSolver;

/**
 * A Solution represents a candidate refactoring plan for a method.
 * <p>
 * It consists of a list of {@link Sequence}s, each representing a block of code 
 * to be extracted into a separate method. The class manages the evaluation (fitness),
 * feasibility, and application of these refactorings.
 * </p>
 */
public class Solution {
	private static final Logger LOGGER = Logger.getLogger(IlpSolver.class.getName());
    
    /**
     * List of code sequences to extract. 
     * Ordered by their position in the source code (top to bottom).
     */
    private List<Sequence> sequenceList;
    
    /**
     * The CompilationUnit this Solution belongs to.
     */
    private CompilationUnit compilationUnit;

    /**
     * The {@link MethodDeclaration} node of the method being refactored.
     */
    private ASTNode method;

    /**
     * Name of the method being refactored.
     */
    private String methodName;

    /**
     * True if all code extractions in the list are valid and applicable.
     */
    private boolean feasible = false;

    /**
     * Quality of the Solution (lower is better, but code initializes to Double.MAX_VALUE).
     */
    private double fitness;

    /**
     * Initial cognitive complexity of the method before refactoring.
     */
    private int initialComplexity;
    
    /**
     * Total reduction in cognitive complexity if the Solution is applied.
     */
    private int reducedComplexity;

    /**
     * Aggregated metrics of the code extractions associated with this Solution.
     */
    private CodeExtractionMetricsStats extractionMetricsStats = null;

    /**
     * Create a Solution from a given list of Sequences.
     *
     * @param sequenceList          The list of sequences to extract.
     * @param compilationUnit       The CompilationUnit containing the code.
     * @param methodDeclarationNode The MethodDeclaration node.
     */
    public Solution(List<Sequence> sequenceList, CompilationUnit compilationUnit, ASTNode methodDeclarationNode) {
        this.sequenceList = sequenceList;
        this.compilationUnit = compilationUnit;
        this.method = methodDeclarationNode;
        this.methodName = ((MethodDeclaration) this.method).getName().toString();
        this.initialComplexity = Utils.getIntegerPropertyOfNode(method, Constants.ACCUMULATED_CONTRIBUTION_TO_COGNITIVE_COMPLEXITY);
        this.fitness = Double.MAX_VALUE;
    }

    /**
     * Create an empty Solution for a given CompilationUnit.
     *
     * @param compilationUnit       The CompilationUnit.
     * @param methodDeclarationNode The MethodDeclaration node.
     */
    public Solution(CompilationUnit compilationUnit, ASTNode methodDeclarationNode) {
        this(new ArrayList<>(), compilationUnit, methodDeclarationNode);
    }
    
    /**
     * Import a Solution from a string representation.
     *
     * @param compilationUnit           The CompilationUnit.
     * @param solutionString            String representation of offsets (e.g., "[[8520], [10105 10147]]").
     * @param methodCognitiveComplexity The known complexity of the method.
     */
    public Solution(CompilationUnit compilationUnit, String solutionString, int methodCognitiveComplexity) {
        this.sequenceList = new ArrayList<>();
        this.compilationUnit = compilationUnit;

        // Pattern to match arrays of numbers: "[123 456]"
        Pattern p = Pattern.compile("\\[([0-9\\s]+)\\]");
        Matcher m = p.matcher(solutionString);

        while (m.find()) {
            // content inside brackets: "123 456 789"
            String value = m.group(1); 
            String[] offsets = value.split("\\s+");

            List<ASTNode> nodes = new ArrayList<>();
            for (String offsetStr : offsets) {
                if (offsetStr.isEmpty()) continue;
                int offset = Integer.parseInt(offsetStr);
                // 0 length implies we want the exact node starting at that offset
                NodeFinder finder = new NodeFinder(compilationUnit, offset, 0);
                ASTNode node = finder.getCoveringNode();
                if (node != null) {
                    nodes.add(node);
                }
            }
            if (!nodes.isEmpty()) {
                this.sequenceList.add(new Sequence(compilationUnit, nodes));
            }
        }

        if (!sequenceList.isEmpty()) {
            this.method = sequenceList.get(0).getMethodDeclaration();
            this.methodName = ((MethodDeclaration) this.method).getName().toString();
        }
        this.initialComplexity = methodCognitiveComplexity;
        this.fitness = Double.MAX_VALUE;
    }

    /**
     * Copy Constructor.
     *
     * @param other The solution to copy.
     */
    public Solution(Solution other) {
        this.compilationUnit = other.compilationUnit;
        this.method = other.method;
        this.methodName = ((MethodDeclaration) other.method).getName().toString();
        this.sequenceList = new ArrayList<>();
        for (Sequence s : other.sequenceList) {
            this.sequenceList.add(s.copy());
        }
        this.fitness = other.fitness;
        this.feasible = other.feasible;
        this.initialComplexity = other.initialComplexity;
        this.reducedComplexity = other.reducedComplexity;
        this.extractionMetricsStats = other.extractionMetricsStats;
    }
    
    /**
     * Compute the metrics of the solution using the provided {@link RefactoringCache}.
     * <p>
     * This method evaluates metrics for every sequence. It accounts for nested extractions
     * by subtracting the complexity of inner extractions from outer ones.
     * </p>
     *
     * @param rf The refactoring cache.
     * @return Aggregated metrics of the solution.
     */
    public CodeExtractionMetrics evaluate(RefactoringCache rf) {
        int complexityOfNewExtractedMethod;
        CodeExtractionMetrics[] metrics = new CodeExtractionMetrics[sequenceList.size()];
        CodeExtractionMetrics results = new CodeExtractionMetrics(true, "", false, 0, 0, new ArrayList<Change>(), new ArrayList<Change>(), 0);
        
        ExtractionTextRange currentRange = null;
        ExtractionTextRange lastRange = null; // "last" in loop iteration (physically later in file)
        
        fitness = sequenceList.size();
        reducedComplexity = 0;
                    
        // Process sequences from bottom to top (Sequence list is sorted by offset)
        for (int i = sequenceList.size() - 1; i >= 0; i--) {
            
            // Lazy evaluate metrics
            if (metrics[i] == null) {
                metrics[i] = sequenceList.get(i).evaluate(rf);
            }

            // Fast fail: if any sequence is not feasible
            if (!metrics[i].isFeasible()) {
                fitness = Double.MAX_VALUE;
                feasible = false;
                reducedComplexity = 0;
                return metrics[i];
            }

            currentRange = sequenceList.get(i).getTextRange();
            
            // Check for nesting: if we have processed a previous extraction (which is physically LATER),
            // we check if that 'last' extraction is contained inside the 'current' one.
            if (lastRange != null) {                
                int innerIndex = i;
                ExtractionTextRange potentialParentRange = currentRange;
                
                // Scan backwards (towards parents/earlier nodes) to update their metrics                              
                while (innerIndex >= 0) {
                    // Adapt length/metrics if nesting occurs
                    // 'lastRange' is the inner extracted method.
                    // 'potentialParentRange' is a candidate outer method.
                    if (lastRange.isContainedIn(potentialParentRange)) {
                        
                        // Ensure the parent is evaluated
                        if (metrics[innerIndex] == null) {
                            metrics[innerIndex] = sequenceList.get(innerIndex).evaluate(rf);
                        }
                        
                        // Child metrics (metrics[i+1] because we are moving backwards)
                        // Actually, 'lastRange' corresponded to metrics of the previously processed item.
                        // In this loop structure, we need to be careful with indices.
                        // The original logic assumes 'last' corresponds to the iteration i+1 (the previous loop step)
                        
                        // Subtract child complexity from parent
                        CodeExtractionMetrics parentMetrics = metrics[innerIndex];
                        CodeExtractionMetrics childMetrics = metrics[i + 1]; 
                        
                        parentMetrics.setReductionOfCognitiveComplexity(parentMetrics.getReductionOfCognitiveComplexity() - childMetrics.getReductionOfCognitiveComplexity());
                        parentMetrics.setAccumulatedInherentComponent(parentMetrics.getAccumulatedInherentComponent() - childMetrics.getAccumulatedInherentComponent());
                        parentMetrics.setNumberNestingContributors(parentMetrics.getNumberNestingContributors() - childMetrics.getNumberNestingContributors());                        
                        parentMetrics.setAccumulatedNestingComponent(childMetrics.getNesting() - parentMetrics.getNesting());                        
                    } 
                    
                    // Move to the next potential parent (physically earlier in list)
                    if (innerIndex > 0) {
                        potentialParentRange = sequenceList.get(innerIndex - 1).getTextRange();
                    }
                    innerIndex--;
                }
            }
            
            // Penalize if the extracted method itself is too complex
            complexityOfNewExtractedMethod = metrics[i].getCognitiveComplexityOfNewExtractedMethod();
            if (complexityOfNewExtractedMethod > Constants.COGNITIVE_COMPLEXITY_THRESHOLD) {
                fitness += (complexityOfNewExtractedMethod - Constants.COGNITIVE_COMPLEXITY_THRESHOLD) * 10;
            }
            
            reducedComplexity += metrics[i].getReductionOfCognitiveComplexity();
            results.joinMetrics(metrics[i]);
            
            lastRange = currentRange;
        }

        this.extractionMetricsStats = new CodeExtractionMetricsStats(metrics);
        int finalMethodComplexity = this.initialComplexity - reducedComplexity;

        // Penalize if the original method (after extraction) is still too complex
        if (finalMethodComplexity > Constants.COGNITIVE_COMPLEXITY_THRESHOLD) {
            fitness += (finalMethodComplexity - Constants.COGNITIVE_COMPLEXITY_THRESHOLD) * 10;
        }
        
        feasible = results.isFeasible();

        return results;
    }

    public boolean isFeasible() {
        return feasible;
    }

    public void removeSequence(int i) {
        this.sequenceList.remove(i);
    }

    public List<Sequence> getSequenceList() {
        return this.sequenceList;
    }

    public Sequence getSequence(int i) {
        return this.sequenceList.get(i);
    }

    public int getSize() {
        return this.sequenceList.size();
    }

    public String getMethodName() {
        return this.methodName;
    }

    public MethodDeclaration getMethodDeclaration() {
        return (MethodDeclaration) method;
    }

    public boolean contains(ASTNode node) {
        return sequenceList.stream().anyMatch(s -> s.contains(node));
    }

    @Override
    public String toString() {
        return "Solution [methodName=" + methodName + ", sequenceList=" + sequenceList + ", isFeasible=" + feasible
                + ", fitness=" + fitness + ", reducedComplexity=" + reducedComplexity + "]";
    }

    public String toStringVerbose() {
        StringBuilder result = new StringBuilder();

        result.append("Solution [methodName=").append(methodName)
              .append(", sequenceList=").append(sequenceList)
              .append(", isFeasible=").append(feasible)
              .append(", fitness=").append(fitness)
              .append(", reducedComplexity=").append(reducedComplexity).append("]\n");

        if (this.compilationUnit != null && this.compilationUnit.getJavaElement() != null) {
             result.append("COMPILATION UNIT ").append(this.compilationUnit.getJavaElement().getPath()).append("\n");
        }
        result.append("Printing sequence list (AST nodes) [").append(sequenceList.size()).append(" code extraction(s)] ...\n");

        int count = 0;
        for (Sequence seq : sequenceList) {
            result.append("EXTRACTION ").append(count + 1).append("\n");
            result.append("Nodes starting position: ").append(seq.toString()).append("\n");
            result.append("OFFSET: ").append(seq.getTextRange()).append("\n");
            result.append("CODE: ").append(seq.toSourceCodeString()).append("\n");
            count++;
        }

        return result.toString();
    }

    public String toStringForFileFormat() {
        return this.sequenceList.toString();
    }

    public double getFitness() {
        return fitness;
    }

    public int getReducedComplexity() {
        return reducedComplexity;
    }

    public int getInitialComplexity() {
        return initialComplexity;
    }
    
    public CodeExtractionMetricsStats getExtractionMetricsStats() {
        return extractionMetricsStats;
    }

    public void writeInFile(String absolutePathToFile) {
        try (PrintWriter pw = new PrintWriter(absolutePathToFile)) {
            pw.write(this.toStringVerbose());
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    /**
     * Return a list of text ranges for the sequences in this solution.
     */
    public List<ExtractionTextRange> getRanges() {
        return sequenceList.stream()
                .map(Sequence::getTextRange)
                .collect(Collectors.toList());
    }

    // -------------------------------------------------------------------------
    // Refactoring Application Logic
    // -------------------------------------------------------------------------

    /**
     * Interface to handle optional actions after a successful extraction (e.g., file writing).
     */
    @FunctionalInterface
    private interface PostExtractionAction {
        void execute(int extractionIndex, CompilationUnit cu) throws IOException, CoreException;
    }

    /**
     * Applies the list of extract method refactoring operations associated to this solution.
     *
     * @param printDebuggingInformation true to print information during the process
     * @return true if all extractions were performed
     * @throws CoreException when processing undoing code extractions
     */
    public boolean applyExtractMethodsRefactoring(boolean printDebuggingInformation) throws CoreException {
        try {
            // Pass a no-op action
            return applyExtractionsInternal(printDebuggingInformation, (index, cu) -> {});
        } catch (IOException e) {
            // Should not happen as the no-op action doesn't throw IOException
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Applies the list of extract method refactoring operations and writes the
     * intermediate source code to files in the specified folder.
     *
     * @param printDebuggingInformation true to print information during the process
     * @param folder                    place where output files will be generated
     * @return true if all extractions were performed
     * @throws IOException   when processing files
     * @throws CoreException when processing undoing code extractions
     */
    public boolean applyExtractMethodsRefactoringWritingExtractionsInFiles(boolean printDebuggingInformation, String folder) throws IOException, CoreException {
        
        // 1. Write the Original file before any modifications
        String outputFile = compilationUnit.getJavaElement().getPath().toString();
        outputFile = outputFile.substring(1, outputFile.length() - (".java").length());
        outputFile = outputFile.replace(File.separatorChar, '.');
        outputFile += "." + this.getMethodName() + ".Original.java";
        outputFile = folder + File.separatorChar + outputFile;
        
        try (FileWriter fw = new FileWriter(outputFile)) {
            fw.write(this.getMethodDeclaration().toString());
        }

        // 2. Define the action to write subsequent extraction files
        PostExtractionAction writeAction = (index, cu) -> {
            String stepFile = cu.getJavaElement().getPath().toString();
            stepFile = stepFile.substring(1, stepFile.length() - (".java").length());
            stepFile = stepFile.replace(File.separatorChar, '.');
            // In the loop, index goes from size-1 down to 0. 
            // So "Extraction 1" corresponds to index=size-1? 
            // We can calculate size based on getRanges().size()
            
            int stepNumber = getRanges().size() - index;
            stepFile += "." + this.methodName + ".Extraction" + stepNumber + ".java";
            stepFile = folder + File.separatorChar + stepFile;

            try (FileWriter fwStep = new FileWriter(stepFile)) {
                // We use the new CU root to find the method signature again
                MethodDeclarationFinderVisitor methodFinderVisitor = new MethodDeclarationFinderVisitor(
                        cu.getRoot(), this.methodName, this.getMethodDeclaration().parameters());
                
                if (methodFinderVisitor.getMethodDeclaration() != null) {
                    fwStep.write(methodFinderVisitor.getMethodDeclaration().toString());
                } else {
                	LOGGER.severe("Error: Could not find method " + this.methodName + " in the extracted source.");
                }
            }
        };

        // 3. Delegate to the internal engine
        return applyExtractionsInternal(printDebuggingInformation, writeAction);
    }

    /**
     * Internal engine that performs the extractions.
     * Contains the core logic for offsets, nesting, and imports.
     */
    private boolean applyExtractionsInternal(boolean printDebuggingInformation, PostExtractionAction postAction) throws CoreException, IOException {
        boolean extractionsApplied = true;
        String currentMethodName = null;
        CodeExtractionMetrics extractionMetrics;
        ExtractionTextRange current = null, last = null;
        List<ExtractionTextRange> rangesForExtractions = getRanges();
        Stack<CodeExtractionMetrics> extractionMetricsStack = new Stack<>();

        int numberImportedLibraries = compilationUnit.imports().size();
        int lengthPreviousImportedLibraries = Utils.lengthImportDeclaration(compilationUnit);
        int lengthImportedLibraries = lengthPreviousImportedLibraries;
        int deltaInLengthImportedLibraries = 0;
        int deltaInNumberImportedLibraries = 0;

        if (printDebuggingInformation)
            LOGGER.info("Extracting code " + (this.toStringForFileFormat()) + " ...");

        // loop for code extractions: from back to front
        int indexOfExtraction = rangesForExtractions.size() - 1;
        
        while (indexOfExtraction >= 0 && extractionsApplied) {
            if (printDebuggingInformation) {
            	LOGGER.info("Applying extraction " + (indexOfExtraction + 1) + " (there are " + rangesForExtractions.size() + ") ...");
            	LOGGER.info(rangesForExtractions.get(indexOfExtraction).toString() + " ...");
            }

            current = rangesForExtractions.get(indexOfExtraction);

            // Handle Nested Extractions
            if (last != null) {
                ASTNode lastExtractionCall = null;
                int auxiliarLength = 0;
                int indexOfExtractionWhenUpdatingOffsets = indexOfExtraction;
                ExtractionTextRange currentInNextExtractions = current;
                
                while (indexOfExtractionWhenUpdatingOffsets >= 0) {
                    if (printDebuggingInformation) {
                    	LOGGER.info("Checking if " + last + " is contained in " + currentInNextExtractions);
                    }

                    if (last.isContainedIn(currentInNextExtractions)) {
                        if (lastExtractionCall == null) {
                            lastExtractionCall = new NodeFinder(compilationUnit, last.getStart(), 0).getCoveringNode();
                            // Heuristic to find the correct call node
                            while (!(lastExtractionCall instanceof SimpleName)
                                    || ((lastExtractionCall instanceof SimpleName)
                                            && !((SimpleName) lastExtractionCall).getIdentifier().equals(currentMethodName))) {
                                auxiliarLength++;
                                lastExtractionCall = new NodeFinder(compilationUnit, last.getStart() + auxiliarLength, 0).getCoveringNode();
                                if (auxiliarLength > 1000) break; // Safety break
                            }
                            if (lastExtractionCall instanceof SimpleName && ((SimpleName) lastExtractionCall).getIdentifier().equals(currentMethodName))
                                lastExtractionCall = lastExtractionCall.getParent();
                        }

                        int computedEndPosition = currentInNextExtractions.getEnd() - (last.getEnd() - last.getStart())
                                + (lastExtractionCall.getLength()) + auxiliarLength + 1;
                        
                        if (deltaInNumberImportedLibraries > 0) {
                            computedEndPosition = computedEndPosition - deltaInLengthImportedLibraries - deltaInNumberImportedLibraries;
                        }
                        currentInNextExtractions.setEnd(computedEndPosition);
                    }
                    if (indexOfExtractionWhenUpdatingOffsets > 0)
                        currentInNextExtractions = rangesForExtractions.get(indexOfExtractionWhenUpdatingOffsets - 1);
                    indexOfExtractionWhenUpdatingOffsets--;
                }
            }

            // Generate Method Name
            currentMethodName = this.methodName + "_extraction_" + (indexOfExtraction + 1);
            currentMethodName = currentMethodName.replaceFirst("^.", currentMethodName.substring(0, 1).toLowerCase());
            if (printDebuggingInformation) LOGGER.info("New method name = " + currentMethodName);

            // Perform Extraction
            int length = current.getEnd() - current.getStart();
            if (printDebuggingInformation) {
            	LOGGER.info("Extracting from " + current.getStart() + " length " + length);
            }
            
            extractionMetrics = Utils.extractCode(compilationUnit, current.getStart(), length, currentMethodName, false);
            extractionsApplied = extractionsApplied && extractionMetrics.isApplied();

            if (!extractionsApplied) {
            	LOGGER.severe("ERROR extracting sequence " + sequenceList.get(indexOfExtraction));
            	LOGGER.severe(extractionMetrics.getReason());
                while (!extractionMetricsStack.isEmpty())
                    extractionMetricsStack.pop().getUndoChanges().get(0).perform(null);
            } else {
                extractionMetricsStack.push(extractionMetrics);
                if (printDebuggingInformation) {
                	LOGGER.info("Extraction applied successfully! Reloading CU.");
                }
                
                // Reload AST
                compilationUnit = Utils.createCompilationUnitFromFileInWorkspace(
                        compilationUnit.getJavaElement().getPath().toOSString());
                
                // Execute Post Action (File writing)
                if (postAction != null) {
                    postAction.execute(indexOfExtraction, compilationUnit);
                }
            }

            last = current;
            indexOfExtraction--;

            // Handle Import Updates
            if (compilationUnit.imports().size() != numberImportedLibraries) {
                if (printDebuggingInformation) LOGGER.info("Imports modified. Updating offsets...");
                
                lengthPreviousImportedLibraries = lengthImportedLibraries;
                lengthImportedLibraries = Utils.lengthImportDeclaration(compilationUnit);
                deltaInLengthImportedLibraries = lengthImportedLibraries - lengthPreviousImportedLibraries;
                deltaInNumberImportedLibraries = compilationUnit.imports().size() - numberImportedLibraries;
                
                for (int index = indexOfExtraction; index >= 0; index--) {
                    ExtractionTextRange r = rangesForExtractions.get(index);
                    r.setStart(r.getStart() + deltaInLengthImportedLibraries + deltaInNumberImportedLibraries);
                    r.setEnd(r.getEnd() + deltaInLengthImportedLibraries + deltaInNumberImportedLibraries);
                }
                numberImportedLibraries = compilationUnit.imports().size();
            } else {
                deltaInLengthImportedLibraries = 0;
                deltaInNumberImportedLibraries = 0;
            }
        }

        return extractionsApplied;
    }
}