package neo.reducecognitivecomplexity.core.jdt;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.CatchClause;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ConditionalExpression;
import org.eclipse.jdt.core.dom.DoStatement;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.ForStatement;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.StructuralPropertyDescriptor;
import org.eclipse.jdt.core.dom.SwitchStatement;
import org.eclipse.jdt.core.dom.WhileStatement;
import org.eclipse.jdt.core.refactoring.CompilationUnitChange;
import org.eclipse.jdt.internal.corext.refactoring.code.ExtractMethodRefactoring;
import org.eclipse.jface.text.Document;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.CompositeChange;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.text.edits.TextEdit;

import neo.reducecognitivecomplexity.app.Constants;
import neo.reducecognitivecomplexity.core.ExtractionTextRange;

/**
 * Utility class for performing JDT (Java Development Tools) operations.
 * <p>
 * This class provides helper methods for:
 * <ul>
 * <li>Creating and parsing ASTs from files or workspace resources.</li>
 * <li>Performing programmatic refactorings (specifically Method
 * Extraction).</li>
 * <li>Calculating code metrics.</li>
 * <li>Navigating the AST (finding nodes, siblings, parents).</li>
 * </ul>
 * </p>
 */
@SuppressWarnings("restriction")
public class Utils {
	
	private static final Logger LOGGER = Logger.getLogger(Utils.class.getName());

	/**
	 * Temporal name used by the oracle when testing if a code extraction into a new
	 * method is feasible.
	 */
	private final static String TEMPORAL_NAME_FOR_EXTRACTED_METHOD = "temporalMethodUnderTest";

	// =========================================================================
	// AST CREATION & PARSING
	// =========================================================================

	/**
	 * Create a {@link CompilationUnit} from the path of a file in the workspace.
	 *
	 * @param pathToFileInWorkspace relative file path in the workspace.
	 * @return The {@link CompilationUnit} associated to the file.
	 */
	public static CompilationUnit createCompilationUnitFromFileInWorkspace(String pathToFileInWorkspace) {
		IWorkspace workspace = ResourcesPlugin.getWorkspace();
		IPath path = Path.fromOSString(pathToFileInWorkspace);
		IFile file = workspace.getRoot().getFile(path);
		ICompilationUnit element = JavaCore.createCompilationUnitFrom(file);

		ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setResolveBindings(true);
		parser.setBindingsRecovery(true);
		parser.setStatementsRecovery(false);
		parser.setSource(element);

		return (CompilationUnit) parser.createAST(null);
	}

	/**
	 * Creates and configures an AST parser with optimal settings for method
	 * analysis.
	 *
	 * @return a configured ASTParser instance.
	 */
	static ASTParser createASTParser() {
		ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setResolveBindings(true); // Enable binding resolution for accurate analysis
		parser.setBindingsRecovery(true); // Allow recovery from binding problems
		parser.setStatementsRecovery(false); // Strict parsing regarding syntax
		return parser;
	}

	/**
	 * Create a {@link CompilationUnit} from the absolute path of a file in the
	 * system.
	 * <p>
	 * Note: This method reads the file as a raw string. The resulting AST will
	 * likely NOT have resolved bindings because it is not attached to a Java
	 * Project context.
	 * </p>
	 *
	 * @param pathToFile absolute file path in the system.
	 * @return The {@link CompilationUnit} associated to the file or null if an
	 *         error occurs.
	 */
	public static CompilationUnit createCompilationUnitFromFile(String pathToFile) {
		try {
			String sourceCode = new String(Files.readAllBytes(Paths.get(pathToFile)));
			ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
			parser.setSource(sourceCode.toCharArray());
			parser.setKind(ASTParser.K_COMPILATION_UNIT);

			return (CompilationUnit) parser.createAST(null);
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
	}

	/**
	 * Parses an {@link ICompilationUnit} into an AST.
	 *
	 * @param sourceFile the compilation unit to parse.
	 * @return the parsed CompilationUnit.
	 * @throws RuntimeException if there's a critical parsing error.
	 */
	public static CompilationUnit parse(ICompilationUnit sourceFile) {
		ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setSource(sourceFile);
		parser.setResolveBindings(true);
		parser.setBindingsRecovery(true);
		parser.setStatementsRecovery(false);

		try {
			return (CompilationUnit) parser.createAST(new NullProgressMonitor());
		} catch (Exception e) {
			throw new RuntimeException("Failed to create AST for: " + sourceFile.getElementName(), e);
		}
	}

	// =========================================================================
	// REFACTORING & EXTRACTION
	// =========================================================================

	/**
	 * Check if a node can be extracted as a new method in the same compilation
	 * unit.
	 *
	 * @param unit The compilation unit under processing.
	 * @param node The node to check.
	 * @return {@link CodeExtractionMetrics} containing feasibility, errors, or
	 *         resulting code stats.
	 */
	public static CodeExtractionMetrics checkNodeExtraction(CompilationUnit unit, ASTNode node) {
		return extractCode(unit, node.getStartPosition(), node.getLength(), TEMPORAL_NAME_FOR_EXTRACTED_METHOD, true);
	}

	/**
	 * Check if existing code between two nodes (inclusive) can be extracted as a
	 * new method.
	 *
	 * @param unit  The compilation unit under processing.
	 * @param nodeA The starting node.
	 * @param nodeB The ending node.
	 * @return {@link CodeExtractionMetrics} containing feasibility results.
	 */
	public static CodeExtractionMetrics checkCodeExtractionBetweenTwoNodes(CompilationUnit unit, ASTNode nodeA,
			ASTNode nodeB) {
		int startPosition = nodeA.getStartPosition();
		int endPosition = nodeB.getStartPosition() + nodeB.getLength();
		int length = endPosition - startPosition;

		return extractCode(unit, startPosition, length, TEMPORAL_NAME_FOR_EXTRACTED_METHOD, true);
	}

	/**
	 * Extract existing code between two nodes (inclusive) as a new method.
	 *
	 * @param unit                The compilation unit.
	 * @param nodeA               The starting node.
	 * @param nodeB               The ending node.
	 * @param extractedMethodName Name of the extracted method.
	 * @param simulation          true to only check validity (undoes changes),
	 *                            false to apply permanently.
	 * @return {@link CodeExtractionMetrics} containing the results.
	 */
	public static CodeExtractionMetrics extractCodeBetweenTwoNodes(CompilationUnit unit, ASTNode nodeA, ASTNode nodeB,
			String extractedMethodName, boolean simulation) {
		int startPosition = nodeA.getStartPosition();
		int endPosition = nodeB.getStartPosition() + nodeB.getLength();
		int length = endPosition - startPosition;

		return extractCode(unit, startPosition, length, extractedMethodName, simulation);
	}

	/**
	 * Refactors a compilation unit by extracting the specified source code into a new method.
	 * <p>
	 * This method uses the Eclipse JDT {@link ExtractMethodRefactoring} class to perform 
	 * the heavy lifting. It evaluates both initial and final preconditions. If running in 
	 * {@code simulation} mode, it applies the changes to a temporary in-memory document 
	 * to verify compilation viability without triggering expensive workspace resource events.
	 * </p>
	 *
	 * @param originalUnit        The compilation unit under processing.
	 * @param selectionStart      The offset of the start of the code to refactor.
	 * @param selectionLength     The length of the code to refactor.
	 * @param extractedMethodName Name of the new method to be created.
	 * @param simulation          If {@code true}, performs the refactoring strictly in-memory 
	 * to gather metrics and verify compilation, bypassing workspace edits.
	 * @return A {@link CodeExtractionMetrics} object containing the feasibility, lines of code, 
	 * parameters of the extracted method, and the generated JDT {@link Change} objects.
	 */
	public static CodeExtractionMetrics extractCode(CompilationUnit originalUnit, int selectionStart,
	        int selectionLength, String extractedMethodName, boolean simulation) {

	    List<Change> changes = new ArrayList<>();
	    List<Change> undoChanges = new ArrayList<>();
	    boolean feasible = true;
	    String resultOfRefactoring = "";
	    boolean refactoringApplied = false;
	    int numberOfExtractedLinesOfCode = 0;
	    int numberOfParametersInExtractedMethod = 0;
	    IProgressMonitor npm = new NullProgressMonitor();
	    ICompilationUnit workingCopy = null;
	    int selectionEnd = selectionStart + selectionLength;
	    
	    MethodDeclaration md = getOutermostMethodDeclaration(originalUnit, selectionStart, selectionLength);
	    // Safe fallback in case the text selection is completely outside any method
	    String targetMethodName = (md != null && md.getName() != null) ? md.getName().getIdentifier() : "UnknownMethod";

	    long startTime = System.currentTimeMillis();
	    long runtime = startTime;

	    try {
	        // Convert AST to Java Model (Working Copy) needed for Refactoring definitions
	        workingCopy = getICompilationUnit(originalUnit).getWorkingCopy(npm);

	        ExtractMethodRefactoring refactoring = new ExtractMethodRefactoring(workingCopy, selectionStart,
	                selectionLength);
	        refactoring.setMethodName(extractedMethodName);

	        RefactoringStatus status = refactoring.checkInitialConditions(npm);

	        if (status.isOK()) {
	            status = refactoring.checkFinalConditions(npm);

	            if (status.isOK()) {
	                resultOfRefactoring = "OK";

	                numberOfExtractedLinesOfCode = numberOfLinesOfCode(originalUnit, selectionStart, selectionLength);
	                numberOfParametersInExtractedMethod = refactoring.getParameterInfos().size();
	                refactoring.setReplaceDuplicates(false);

	                // Create the change but DO NOT perform it yet
	                Change c = refactoring.createChange(npm);

	                if (simulation) {
	                    // =================================================================
	                    // FAST IN-MEMORY SIMULATION (No Workspace Events Triggered)
	                    // =================================================================
	                    TextEdit edit = extractTextEdit(c);
	                    
	                    if (edit == null) {
	                        feasible = false;
	                        resultOfRefactoring = "Failed to extract TextEdit for simulation.";
	                    } else {
	                        // Apply the edit to a raw String document
	                        Document document = new Document(workingCopy.getSource());
	                        edit.apply(document);

	                        // Parse the updated string back into a temporary AST to check for errors
	                        ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
	                        parser.setSource(document.get().toCharArray());
	                        parser.setProject(workingCopy.getJavaProject());
	                        parser.setUnitName(workingCopy.getElementName());
	                        parser.setResolveBindings(true);
	                        parser.setBindingsRecovery(true);
	                        
	                        CompilationUnit astAfter = (CompilationUnit) parser.createAST(npm);
	                        boolean compilationErrors = builtWithCompilationErrors(astAfter);

	                        if (compilationErrors) {
	                            resultOfRefactoring = "Compilation unit does not compile after method extraction.";
	                            feasible = false;
	                        } else {
	                            changes.add(c);
	                            // undoChanges remains empty because nothing was actually applied
	                        }
	                    }
	                } else {
	                    // =================================================================
	                    // ACTUAL WORKSPACE MODIFICATION (Final Refactoring Application)
	                    // =================================================================
	                    Change undo = c.perform(npm);
	                    
	                    // Verify compilation after change on the real working copy
	                    CompilationUnit astAfter = parse(workingCopy);
	                    boolean compilationErrors = builtWithCompilationErrors(astAfter);

	                    if (compilationErrors) {
	                        resultOfRefactoring = "Compilation unit does not compile after method extraction.";
	                        feasible = false;
	                        undo.perform(npm); // Revert the real change
	                    } else {
	                        changes.add(c);
	                        undoChanges.add(undo);
	                        refactoringApplied = true;
	                    }
	                }
	            } else {
	                feasible = false;
	                resultOfRefactoring = status.getEntryWithHighestSeverity().getMessage();
	            }
	        } else {
	            feasible = false;
	            resultOfRefactoring = status.getEntryWithHighestSeverity().getMessage();
	        }

	    } catch (Exception e) {
	        feasible = false;
	        resultOfRefactoring = "Exception: " + e.getMessage();
	        LOGGER.log(Level.WARNING, "Error while refactoring method {0} ([{1}, {2}]): {3}", 
	                new Object[]{targetMethodName, selectionStart, selectionEnd, e.getMessage()});
	        // Log the actual stack trace via the logger instead of standard out
	        LOGGER.log(Level.FINE, "Stack trace:", e);
	    } finally {
	        runtime = System.currentTimeMillis() - startTime;
	        if (workingCopy != null) {
	            try {
	                workingCopy.discardWorkingCopy();
	            } catch (Exception e) {
	                /* gracefully ignore */ 
	            }
	        }
	    }

	    return new CodeExtractionMetrics(feasible, resultOfRefactoring, refactoringApplied,
	            numberOfExtractedLinesOfCode, numberOfParametersInExtractedMethod, changes, undoChanges, runtime);
	}

	/**
	 * Safely extracts the {@link TextEdit} instructions from an Eclipse {@link Change} object.
	 * <p>
	 * The JDT framework can sometimes wrap file changes inside a {@link CompositeChange} 
	 * depending on the complexity of the refactoring (e.g., updating imports alongside the extraction).
	 * This helper recursively searches the change tree for the actual compilation unit edit.
	 * </p>
	 *
	 * @param change The JDT change object generated by the refactoring framework.
	 * @return The core {@link TextEdit} representing the source code modifications, or {@code null} if not found.
	 */
	private static TextEdit extractTextEdit(Change change) {
	    if (change instanceof CompilationUnitChange) {
	        return ((CompilationUnitChange) change).getEdit();
	    } else if (change instanceof CompositeChange) {
	        for (Change child : ((CompositeChange) change).getChildren()) {
	            if (child instanceof CompilationUnitChange) {
	                return ((CompilationUnitChange) child).getEdit();
	            }
	        }
	    }
	    return null;
	}

	// =========================================================================
	// AST HELPERS & METRICS
	// =========================================================================

	/**
	 * Finds the outermost {@link MethodDeclaration} enclosing a specific text selection 
	 * within a compilation unit.
	 * <p>
	 * This method maps the given text selection to its tightest covering AST node and 
	 * traverses up the parent chain to the root of the AST. It is particularly useful 
	 * for identifying the top-level method context when a selection occurs inside 
	 * nested structures, such as anonymous inner classes, lambdas, or local types.
	 * </p>
	 *
	 * @param originalUnit    The parsed AST compilation unit representing the source file.
	 * @param selectionStart  The starting character offset of the text selection.
	 * @param selectionLength The number of characters in the text selection.
	 * @return The outermost {@link MethodDeclaration} containing the selection, or 
	 * {@code null} if the selection is not located within any method.
	 */
	public static MethodDeclaration getOutermostMethodDeclaration(CompilationUnit originalUnit, int selectionStart, int selectionLength) {
	    
	    // 1. Map the text selection to the tightest covering ASTNode
	    ASTNode node = NodeFinder.perform(originalUnit, selectionStart, selectionLength);
	    
	    MethodDeclaration outermostMethod = null;
	    
	    // 2. Traverse up the parent chain all the way to the root
	    while (node != null) {
	        if (node instanceof MethodDeclaration) {
	            // Update our reference every time we hit a method declaration
	            outermostMethod = (MethodDeclaration) node;
	        }
	        node = node.getParent();
	    }
	    
	    // 3. Return the last one we found (or null if the selection wasn't inside a method)
	    return outermostMethod;
	}
	
	/**
	 * Safely retrieves the {@link ICompilationUnit} (Java Element) from an AST
	 * {@link CompilationUnit}. * @param astRoot The AST root node.
	 * 
	 * @return The corresponding {@link ICompilationUnit}, or {@code null} if it was
	 *         created from a String or Binary file.
	 */
	public static ICompilationUnit getICompilationUnit(CompilationUnit astRoot) {
		if (astRoot == null)
			return null;
		if (astRoot.getTypeRoot() instanceof ICompilationUnit) {
			return (ICompilationUnit) astRoot.getTypeRoot();
		}
		return null;
	}

	/**
     * Helper to walk up the AST to find the nearest Statement.
     * Useful if the sequence nodes are expressions or fragments.
     */
    public static ASTNode getStatementOrParent(ASTNode node) {
        ASTNode current = node;
        while (current != null && !(current instanceof Statement)) {
            current = current.getParent();
        }
        // Fallback: if we hit null (root), return the original node
        return (current != null) ? current : node;
    }
    
	/**
	 * Safely retrieves the {@link ICompilationUnit} from an arbitrary ASTNode.
	 * * @param node The AST node.
	 * 
	 * @return The corresponding {@link ICompilationUnit}, or {@code null} if not
	 *         resolvable.
	 */
	public static ICompilationUnit getICompilationUnit(ASTNode node) {
		CompilationUnit cu = getCompilationUnit(node);
		if (cu.getTypeRoot() instanceof ICompilationUnit) {
			return (ICompilationUnit) cu.getTypeRoot();
		}
		return null;
	}

	/**
	 * Helper to traverse up the tree to find the root CompilationUnit. * @param
	 * node The node to start from.
	 * 
	 * @return The root {@link CompilationUnit}.
	 * @throws IllegalStateException if the node is not attached to a
	 *                               CompilationUnit.
	 */
	public static CompilationUnit getCompilationUnit(ASTNode node) {
		if (node == null)
			throw new IllegalArgumentException("Node cannot be null");

		if (node.getRoot() instanceof CompilationUnit) {
			return (CompilationUnit) node.getRoot();
		}
		throw new IllegalStateException("Node is not attached to a CompilationUnit");
	}

	/**
	 * Check if the compilation unit has any error-level problems.
	 *
	 * @param compilationUnit The compilation unit to check.
	 * @return true if compilation errors exist.
	 * @throws CoreException
	 */
	public static boolean builtWithCompilationErrors(CompilationUnit compilationUnit) throws CoreException {
		for (IProblem p : compilationUnit.getProblems()) {
			if (p.isError())
				return true;
		}
		return false;
	}

	/**
	 * Returns a string dump of problems in the unit (for debugging).
	 */
	public static String getCompilationUnitProblems(CompilationUnit compilationUnit) {
		StringJoiner result = new StringJoiner(System.lineSeparator());
		for (IProblem p : compilationUnit.getProblems()) {
			result.add("Error? " + p.isError() + ": " + p.toString());
		}
		return result.toString();
	}

	/**
	 * Find the innermost AST node at a specific line and offset.
	 *
	 * @param compilationUnit The AST root.
	 * @param startLine       The line number (1-based).
	 * @param startOffset     The character offset.
	 * @return The covering {@link ASTNode}.
	 */
	public static ASTNode findNode(CompilationUnit compilationUnit, int startLine, int startOffset) {
		NodeFinder finder = new NodeFinder(compilationUnit, compilationUnit.getPosition(startLine, startOffset), 0);
		return finder.getCoveringNode();
	}

	/**
	 * Finds all {@link MethodDeclaration} nodes within an AST matching a given method name.
	 *
	 * @param astRoot    The root {@link CompilationUnit} to search in.
	 * @param methodName The target method name.
	 * @return A list of matching {@link MethodDeclaration} nodes.
	 */
	public static List<MethodDeclaration> findMethodsByName(CompilationUnit astRoot, String methodName) {
	    List<MethodDeclaration> matchingMethods = new ArrayList<>();
	    if (astRoot == null || methodName == null) {
	        return matchingMethods;
	    }
	    
	    astRoot.accept(new ASTVisitor() {
	        @Override
	        public boolean visit(MethodDeclaration node) {
	            if (methodName.equals(node.getName().getIdentifier())) {
	                matchingMethods.add(node);
	            }
	            return true;
	        }
	    });
	    return matchingMethods;
	}
	
	/**
	 * Calculates number of lines of code for a selection range.
	 */
	public static int numberOfLinesOfCode(ICompilationUnit unit, int start, int length) {
		return numberOfLinesOfCode(parse(unit), start, length);
	}

	/**
	 * Compute lines of code for a specific AST Node.
	 */
	public static int numberOfLinesOfCode(CompilationUnit compilationUnit, ASTNode node) {
		int startLineNumber = compilationUnit.getLineNumber(node.getStartPosition());
		int endLineNumber = compilationUnit.getLineNumber(node.getStartPosition() + node.getLength() - 1);
		return (endLineNumber - startLineNumber) + 1;
	}

	/**
	 * Compute lines of code for a raw offset/length.
	 */
	public static int numberOfLinesOfCode(CompilationUnit compilationUnit, int startPosition, int length) {
		int startLineNumber = compilationUnit.getLineNumber(startPosition);
		int endLineNumber = compilationUnit.getLineNumber(startPosition + length - 1);
		return (endLineNumber - startLineNumber) + 1;
	}

	/**
	 * Extracts parameter types from a method signature.
	 *
	 * @param method The method declaration.
	 * @return Array of parameter type names.
	 */
	public static String[] getTypesInSignature(MethodDeclaration method) {
		String[] result = new String[method.parameters().size()];
		for (int i = 0; i < method.parameters().size(); i++) {
			result[i] = ((SingleVariableDeclaration) method.parameters().get(i)).getType().toString();
		}
		return result;
	}

	/**
	 * Get siblings of a given node (excluding itself). * @param node The node to
	 * find siblings for.
	 * 
	 * @return List of sibling ASTNodes.
	 */
	public static List<ASTNode> getSiblings(ASTNode node) {
		List<ASTNode> siblings = new ArrayList<>();
		ASTNode parent = node.getParent();

		if (parent != null) {
			List<?> list = parent.structuralPropertiesForType();
			for (Object prop : list) {
				Object child = parent.getStructuralProperty((StructuralPropertyDescriptor) prop);

				if (child instanceof ASTNode && !child.equals(node)) {
					siblings.add((ASTNode) child);
				} else if (child instanceof List<?>) {
					for (Object s : (List<?>) child) {
						if (s instanceof ASTNode && !s.equals(node)) {
							siblings.add((ASTNode) s);
						}
					}
				}
			}
		}
		return siblings;
	}

	/**
	 * Get all children of the parent node (siblings + itself). * @param node The
	 * reference node.
	 * 
	 * @return List of all ASTNodes sharing the same parent.
	 */
	public static List<ASTNode> getAllSiblings(ASTNode node) {
		List<ASTNode> siblings = new ArrayList<>();
		ASTNode parent = node.getParent();

		if (parent != null) {
			List<?> list = parent.structuralPropertiesForType();
			for (Object prop : list) {
				Object child = parent.getStructuralProperty((StructuralPropertyDescriptor) prop);

				if (child instanceof ASTNode) {
					siblings.add((ASTNode) child);
				} else if (child instanceof List<?>) {
					for (Object s : (List<?>) child) {
						if (s instanceof ASTNode) {
							siblings.add((ASTNode) s);
						}
					}
				}
			}
		}
		return siblings;
	}

	// =========================================================================
	// AST PROPERTIES & NAVIGATION
	// =========================================================================

	/**
	 * Get the integer value of the given property of a given node. * @param node
	 * The AST node to inspect.
	 * 
	 * @param property The key of the property (usually from Constants).
	 * @return Value of the given property, or 0 if null or not an Integer.
	 */
	public static int getIntegerPropertyOfNode(ASTNode node, String property) {
		Object value = node.getProperty(property);
		if (value instanceof Integer) {
			return (Integer) value;
		}
		return 0;
	}

	/**
	 * Get siblings found after the node (to the right in the source code). * @param
	 * node The reference node.
	 * 
	 * @return List of right-side siblings.
	 */
	public static List<ASTNode> getRightSiblings(ASTNode node) {
		List<ASTNode> siblings = new ArrayList<>();
		ASTNode parent = node.getParent();
		boolean foundNode = false;

		if (parent != null) {
			List<?> list = parent.structuralPropertiesForType();
			for (Object prop : list) {
				Object child = parent.getStructuralProperty((StructuralPropertyDescriptor) prop);

				// Handle single ASTNode children
				if (child instanceof ASTNode) {
					if (child.equals(node)) {
						foundNode = true;
					} else if (foundNode) {
						siblings.add((ASTNode) child);
					}
				}
				// Handle List of ASTNode children (e.g., statements in a block)
				else if (child instanceof List<?>) {
					for (Object s : (List<?>) child) {
						if (s.equals(node)) {
							foundNode = true;
						} else if (foundNode && s instanceof ASTNode) {
							siblings.add((ASTNode) s);
						}
					}
				}
			}
		}
		return siblings;
	}

	/**
	 * Get the {@link MethodDeclaration} associated with the given ASTNode.
	 * <p>
	 * This method traverses up the AST. It handles anonymous class declarations by
	 * skipping them to find the "true" containing method if applicable, though
	 * usually, it stops at the immediate enclosing method.
	 * </p>
	 * * @param node The node to find the parent method for.
	 * 
	 * @return The enclosing {@link MethodDeclaration}, or null if not found.
	 */
	public static MethodDeclaration getMethodDeclaration(ASTNode node) {
		ASTNode result = node;

		while (result != null && !(result instanceof MethodDeclaration)) {
			result = result.getParent();

			// Special handling: if we hit a method that is a member of an
			// anonymous class OR declared within a LOCAL class (a class
			// declared inside a method/block body), we look further upwards.
			//
			// Methods members of an anonymous OR declared within a local class
			// are NEVER analyzed on their own because their complexity is always
			// aggregated into whichever OUTER, independently-analyzed method's
			// AST walk reaches them. JDT's ExtractMethodRefactoring also
			// places newly extracted methods as siblings WITHIN that same
			// anonymous/local class (it stops at the nearest
			// AbstractTypeDeclaration or AnonymousClassDeclaration, and a
			// local class's TypeDeclaration IS an AbstractTypeDeclaration),
			// so the new method never escapes it either. For BOTH reasons,
			// the "true" method that owns this code's reported complexity is
			// the OUTER one, not the anonymous/local class's own method.
			// The same situation for both, so both must be skipped here
			// consistently.
			if (result instanceof MethodDeclaration) {
				if (isMemberOfAnonymousOrLocalClass((MethodDeclaration) result)) {
					result = result.getParent();
				}
			}
		}

		return (MethodDeclaration) result;
	}

	/**
	 * {@code true} if {@code method} is a direct member of an
	 * {@link AnonymousClassDeclaration}, OR declared within a local class
	 * (a {@link org.eclipse.jdt.core.dom.TypeDeclaration} reached via a
	 * {@code Block} or {@code MethodDeclaration} body, as opposed to a
	 * top-level or regular nested/member type).
	 */
	private static boolean isMemberOfAnonymousOrLocalClass(MethodDeclaration method) {
		if (method.getParent() instanceof AnonymousClassDeclaration) {
			return true;
		}
		ASTNode parent = method.getParent();
		while (parent != null) {
			if (parent instanceof org.eclipse.jdt.core.dom.TypeDeclaration
					&& (parent.getParent() instanceof org.eclipse.jdt.core.dom.Block
							|| parent.getParent() instanceof MethodDeclaration)) {
				return true;
			}
			parent = parent.getParent();
		}
		return false;
	}

	/**
	 * Gets the line number where the method name is declared.
	 */
	static int getMethodDeclarationLineNumber(MethodDeclaration method, CompilationUnit compilationUnit) {
		SimpleName methodName = method.getName();
		int declarationPosition = methodName.getStartPosition();
		return compilationUnit.getLineNumber(declarationPosition);
	}

	/**
	 * Get the {@link ExtractionTextRange} (start and end character positions) of the method
	 * body. * @param node The method declaration.
	 * 
	 * @return {@link ExtractionTextRange} covering the statements within the method body.
	 */
	public static ExtractionTextRange getMethodDeclarationAsPair(MethodDeclaration node) {
		if (node.getBody() == null || node.getBody().statements().isEmpty()) {
			// Fallback for empty methods or abstract methods: return the node's own range
			return new ExtractionTextRange(node.getStartPosition(), node.getStartPosition() + node.getLength());
		}

		int numberStatements = node.getBody().statements().size();
		Statement firstStatement = (Statement) node.getBody().statements().get(0);
		Statement lastStatement = (Statement) node.getBody().statements().get(numberStatements - 1);

		int initialOffset = firstStatement.getStartPosition();
		int finalOffset = lastStatement.getStartPosition() + lastStatement.getLength();

		return new ExtractionTextRange(initialOffset, finalOffset);
	}

	/**
	 * Retrieves all children of a given AST node using standard JDT structural
	 * properties. * @param node The parent node.
	 * 
	 * @return A list of all direct child ASTNodes.
	 */
	public static List<ASTNode> getChildren(ASTNode node) {
		List<ASTNode> children = new ArrayList<>();
		for (Object descriptor : node.structuralPropertiesForType()) {
			StructuralPropertyDescriptor prop = (StructuralPropertyDescriptor) descriptor;
			Object child = node.getStructuralProperty(prop);

			if (child instanceof ASTNode) {
				children.add((ASTNode) child);
			} else if (child instanceof List) {
				@SuppressWarnings("unchecked")
				List<ASTNode> childList = (List<ASTNode>) child;
				children.addAll(childList);
			}
		}
		return children;
	}

	// =========================================================================
	// COGNITIVE COMPLEXITY CALCULATIONS
	// =========================================================================

	/**
	 * Get all ancestors of the given node that contribute to cognitive complexity.
	 * * @param node The starting node.
	 * 
	 * @return List of contributing ancestors.
	 */
	public static List<ASTNode> getAllAncestorContributingToComplexity(ASTNode node) {
		List<ASTNode> ancestors = new ArrayList<>();
		ASTNode parent = node.getParent();

		// Traverse up until we hit the method boundary
		while (parent != null && !(parent instanceof MethodDeclaration
				&& !(parent.getParent() instanceof AnonymousClassDeclaration))) {

			if (parent.getProperty(Constants.CONTRIBUTION_TO_COGNITIVE_COMPLEXITY) != null) {
				ancestors.add(parent);
			}
			parent = parent.getParent();
		}

		return ancestors;
	}

	/**
	 * Populates maps relating nodes to their complexity-contributing ancestors and
	 * children. Useful for graph visualization or detailed impact analysis.
	 * * @param nodes The list of nodes to analyze.
	 * 
	 * @param ancestors Map to populate: Node -> List of Ancestors.
	 * @param children  Map to populate: Ancestor -> List of Children (inverse of
	 *                  ancestors).
	 */
	public static void fillAncestorsAndChildren(List<ASTNode> nodes, Map<ASTNode, List<ASTNode>> ancestors,
			Map<ASTNode, List<ASTNode>> children) {
		for (ASTNode node : nodes) {
			List<ASTNode> listAncestors = Utils.getAllAncestorContributingToComplexity(node);
			ancestors.put(node, listAncestors);
			fillChildren(node, listAncestors, children);
		}
	}

	private static void fillChildren(ASTNode node, List<ASTNode> listAncestors, Map<ASTNode, List<ASTNode>> children) {
		for (ASTNode ancestor : listAncestors) {
			children.computeIfAbsent(ancestor, k -> new ArrayList<>()).add(node);
		}
	}

	/**
	 * Compute the nesting component of cognitive complexity for the given node.
	 * <p>
	 * Walks up the AST from the node to the method declaration, counting how many
	 * nesting structures (loops, ifs, catches) envelop the node.
	 * </p>
	 * * @param method The method containing the node (optional, will be found if
	 * null).
	 * 
	 * @param node The node to check.
	 * @return The nesting depth.
	 */
	public static int computeNesting(MethodDeclaration method, ASTNode node) {
		int nesting = 0;
		ASTNode current = node;
		ASTNode child = null;

		if (method == null) {
			method = getMethodDeclaration(node);
		}

		while (current != null && !(current.equals(method))) {
			child = current;
			current = current.getParent();

			if (current == null)
				break;

			switch (current.getNodeType()) {
			case ASTNode.FOR_STATEMENT:
			case ASTNode.ENHANCED_FOR_STATEMENT:
			case ASTNode.WHILE_STATEMENT:
			case ASTNode.DO_STATEMENT:
			case ASTNode.CATCH_CLAUSE:
			case ASTNode.SWITCH_STATEMENT:
			case ASTNode.LAMBDA_EXPRESSION:
			case ASTNode.CONDITIONAL_EXPRESSION: // Ternary
				nesting++;
				break;
			case ASTNode.METHOD_DECLARATION:
				// Nested method (e.g. anonymous class) increases nesting
				if (!current.equals(method))
					nesting++;
				break;
			case ASTNode.IF_STATEMENT:
				// "else if" does not increase nesting, but "else" does.
				if (child.getLocationInParent().equals(IfStatement.THEN_STATEMENT_PROPERTY)) {
					nesting++;
				} else if (child.getLocationInParent().equals(IfStatement.ELSE_STATEMENT_PROPERTY)) {
					// Only increment if it's strictly an 'else', not an 'else if'
					if (!(child instanceof IfStatement)) {
						nesting++;
					}
				}
				break;
			}
		}

		return nesting;
	}

	/**
	 * Determines whether {@code node} occupies a structural position in its
	 * parent that this codebase's own nesting rules ({@link #computeNesting})
	 * treat as a "chaining continuation" of the parent's own construct, i.e. a
	 * position where the parent's own complexity computation did NOT attribute
	 * an extra, position-specific contribution to the parent itself, because it
	 * expected {@code node} to carry that contribution on its own (as is the
	 * case for an "else if", represented in the JDT AST as an {@link IfStatement}
	 * sitting in its parent's {@code ELSE_STATEMENT_PROPERTY} slot).
	 * <p>
	 * This matters for extraction metrics: if {@code node} is the ROOT of a
	 * code extraction and its parent is NOT included in that extraction, then
	 * after extraction the parent still has a non-empty branch at that exact
	 * slot (now containing a call to the newly extracted method instead of
	 * {@code node}'s original content). Under this codebase's own cognitive
	 * complexity rules, a non-empty slot at that position contributes the same
	 * flat, nesting-independent amount regardless of what fills it -so that
	 * contribution does NOT disappear from the original method just because
	 * {@code node} itself did. Extraction metrics must not count it as removed.
	 * </p>
	 */
	public static boolean isChainingContinuationOfParent(ASTNode node) {
		if (node == null) {
			return false;
		}
		ASTNode parent = node.getParent();
		if (parent == null) {
			return false;
		}
		StructuralPropertyDescriptor location = node.getLocationInParent();
		if (location == null) {
			return false;
		}

		if (parent instanceof IfStatement && location.equals(IfStatement.ELSE_STATEMENT_PROPERTY)) {
			return node instanceof IfStatement;
		}

		return false;
	}

	/**
	 * Simulates the Cognitive Complexity metrics that would result if the given AST
	 * node (and its children) were extracted into a separate method.
	 * <p>
	 * This method answers TWO distinct questions, which are NOT always the same:
	 * </p>
	 * <ol>
	 * <li><b>How complex would the new method be, on its own?</b> (the
	 * {@code accumulatedInherent}/{@code accumulatedNesting}/
	 * {@code cognitiveComplexityWhenExtractedAsNewMethod} fields), computed as if
	 * {@code node} became the top-level body of a brand new, independent method,
	 * with nesting reset to 0 relative to itself.</li>
	 * <li><b>How much does the ORIGINAL (true) method's own reported complexity
	 * actually decrease?</b> (the {@code cognitiveComplexityReduction} field).</li>
	 * </ol>
	 * <p>
	 * For a NORMAL extraction, {@code node} living directly in the body of the
	 * method being refactored, with no anonymous/local class boundary in
	 * between, both questions have the SAME answer: the new method becomes a
	 * fully independent, disconnected member of the class, which
	 * {@link CognitiveComplexityVisitor#methodComplexity(MethodDeclaration)}
	 * never visits again when (re-)analyzing the original method, so its full
	 * contribution truly disappears.
	 * </p>
	 * <p>
	 * However, when {@code node} lives inside a method that is ITSELF a member
	 * of an anonymous (or local) class, e.g. extracting code from inside a
	 * listener's callback method, {@link ExtractMethodRefactoring} always places
	 * the new method in the NEAREST enclosing TYPE, which is that SAME
	 * anonymous/local class, as a SIBLING of the method the code was extracted
	 * from. That sibling remains a descendant of the SAME AST subtree that the
	 * "true" method (see {@link #getMethodDeclaration(ASTNode)}) is rooted at,
	 * because the anonymous class expression itself lives inside the true
	 * method's own body. Since
	 * {@link CognitiveComplexityVisitor#visit(MethodDeclaration)} costs 0 and
	 * does not change the nesting level, and since
	 * {@link CognitiveComplexityVisitor} deliberately does NOT analyze methods
	 * that are members of an anonymous class on their own (see
	 * {@code shouldAnalyzeMethod}/{@code isMemberOfAnonymousClass}), the ONLY
	 * place this new sibling method's content is ever measured is by walking
	 * INTO it from the true method (exactly as the true method's analysis
	 * already walked into the method the code came from). So none of that
	 * content actually leaves the true method's aggregate score: it merely
	 * moves to a sibling position, which shares the SAME "ambient" nesting
	 * level that its neighbour already had (the level stamped on the immediate
	 * enclosing method itself, since entering a MethodDeclaration never adds
	 * nesting). Only the LOCAL nesting accumulated by control-flow structures
	 * STRICTLY BETWEEN the immediate enclosing method's own body and the
	 * extraction root, the ones that stay behind, no longer wrapping the
	 * (now relocated) code, truly disappears from the true method's score.
	 * </p>
	 * <p>
	 * <b>This also affects question (1) for the SAME nested case</b>: since the
	 * relocated code keeps sharing the ambient nesting level of its new
	 * siblings rather than resetting to a fresh, independent depth of 0, its
	 * own projected complexity as the new method (NMCC) must ALSO add back
	 * that ambient level for every node that pays a nesting penalty:
	 * {@code NMCC_nested = inherent + projectedNesting + ambientLevel * numberOfNestingContributors},
	 * instead of the plain {@code inherent + projectedNesting} that is correct
	 * only for an escaping extraction.
	 * </p>
	 *
	 * @param node The AST node (usually a Block or Statement) representing the
	 *             code to be extracted.
	 * @return A {@link CognitiveComplexityMetrics} object containing the projected
	 *         complexity stats for the hypothetical new method, and the TRUE
	 *         reduction achieved on the original method being refactored.
	 */
	public static CognitiveComplexityMetrics computeMetricsIfExtracted(ASTNode node) {
		if (node == null) {
			return new CognitiveComplexityMetrics(0, 0, 0, 0, 0, 0);
		}

		// 1. Base Level: the nesting level of the extraction root in the original
		// code, relative to the "true" method being refactored. Used to compute
		// how complex the new method would be ON ITS OWN (question (1) above);
		// for the nested-in-anonymous/local-class case this is only PART of the
		// answer (see comment further below).
		int baseLevel = getIntegerPropertyOfNode(node, Constants.NESTING_LEVEL);

		// 2. Ambient Level: only relevant for question (2) above. Determines
		// whether this extraction ESCAPES the true method's own AST subtree
		// (the normal case) or stays nested within it via an intervening
		// anonymous/local class (see the class-level javadoc above).
		MethodDeclaration immediateEnclosingMethod = getImmediateEnclosingMethodDeclaration(node);
		boolean extractionEscapesTrueMethod = isEscapingExtraction(node);

		int ambientLevel = extractionEscapesTrueMethod ? 0
				: computeAmbientLevelForMethod(immediateEnclosingMethod);
		// How much LOCAL nesting (above the ambient level shared with sibling
		// methods of the same anonymous/local class) truly disappears.
		int shift = Math.max(0, baseLevel - ambientLevel);

		List<ASTNode> complexityNodes = new ArrayList<>();
		collectComplexityNodes(node, complexityNodes);

		int totalInherent = 0;
		int totalNewComplexity = 0;
		int totalReduction = 0;
		int numberOfNestingContributors = 0;

		for (ASTNode cn : complexityNodes) {
			int contribution = getIntegerPropertyOfNode(cn, Constants.CONTRIBUTION_TO_COGNITIVE_COMPLEXITY);
			int currentLevel = getIntegerPropertyOfNode(cn, Constants.NESTING_LEVEL);
			boolean paysPenalty = paysNestingPenalty(cn);

			// 3. Calculate Inherent (Original) Complexity - unchanged.
			int currentNestingCost = paysPenalty ? currentLevel : 0;
			int inherent = contribution - currentNestingCost;

			// 4. Calculate Projected (New) Complexity, PER NODE, relative to the
			// extraction root's OWN level (reset to 0). This alone is only correct
			// for an ESCAPING extraction; for the nested-in-anonymous/local-class
			// case it is later corrected below (after this loop) by adding back
			// the ambient level shared by sibling methods of that class.
			int newDepth = Math.max(0, currentLevel - baseLevel);
			int projectedNestingCost = paysPenalty ? newDepth : 0;
			int projectedNodeComplexity = inherent + projectedNestingCost;

			// 5. Calculate the TRUE reduction of the ORIGINAL method's score.
			int reductionForThisNode;
			if (extractionEscapesTrueMethod) {
				// Normal case: the new method becomes fully independent and
				// disconnected from the true method's AST subtree, so its whole
				// original contribution truly disappears.
				reductionForThisNode = contribution;
			} else {
				// Nested-in-anonymous/local-class case: the inherent part and the
				// ambient nesting part remain (the sibling method is still walked
				// into when the true method is re-analyzed); only the LOCAL nesting
				// above the ambient level truly disappears, and only for nodes that
				// actually pay a nesting penalty.
				reductionForThisNode = paysPenalty ? shift : 0;
			}

			// 6. Update Totals
			totalReduction += reductionForThisNode;
			totalInherent += inherent;
			totalNewComplexity += projectedNodeComplexity;

			// 7. Update Nesting Contributor Count (unchanged: about the new
			// method's own internal nesting structure).
			if (paysPenalty) {
				if (newDepth > 0) {
					numberOfNestingContributors++;
				} else if (baseLevel > 0) {
					numberOfNestingContributors++;
				}
			}
		}

		int totalProjectedNesting = totalNewComplexity - totalInherent;

		// the new method does NOT actually reset to depth 0 when it
		// cannot escape the true method (anonymous/local class case). It
		// lands as a sibling starting at the SAME ambient level shared by
		// every other method of that class. Every node that pays a nesting
		// penalty therefore ALSO carries that ambient level as extra nesting
		// cost in the new method, on top of the "reset relative to baseLevel"
		// depth already summed above.
		if (!extractionEscapesTrueMethod) {
			int ambientBonus = ambientLevel * numberOfNestingContributors;
			totalProjectedNesting += ambientBonus;
			totalNewComplexity += ambientBonus;
		}

		return new CognitiveComplexityMetrics(totalInherent, totalProjectedNesting, totalNewComplexity, totalReduction,
				numberOfNestingContributors, baseLevel);
	}

	/**
	 * Finds the NEAREST enclosing {@link MethodDeclaration} of {@code node},
	 * WITHOUT the "skip anonymous class methods" special handling that
	 * {@link #getMethodDeclaration(ASTNode)} applies. This is the method whose
	 * structural position JDT's {@code ExtractMethodRefactoring} actually uses
	 * to decide where a newly extracted method would be placed (the nearest
	 * enclosing TYPE, reached by walking up from here).
	 */
	private static MethodDeclaration getImmediateEnclosingMethodDeclaration(ASTNode node) {
		ASTNode current = node.getParent();
		while (current != null && !(current instanceof MethodDeclaration)) {
			current = current.getParent();
		}
		return (MethodDeclaration) current;
	}

	/**
	 * Computes the "ambient" nesting level shared by ALL sibling statements
	 * directly inside {@code method}'s own body, i.e. the nesting level any
	 * statement in that method would have if it added nothing extra on top
	 * of the method's own position in the original source.
	 * <p>
	 * We cannot simply read a {@code NESTING_LEVEL} property off
	 * {@code method}'s own {@link MethodDeclaration} node:
	 * {@code CognitiveComplexityVisitor} never sets one there.
	 * This matters especially for methods that are
	 * members of an anonymous or local class, since those are never independently visited either - their
	 * content is measured only as part of the outer TRUE method's own walk,
	 * which is exactly why an "ambient" reference level is needed here in
	 * the first place.
	 * </p>
	 * <p>
	 * This is derived empirically: the MINIMUM
	 * {@code NESTING_LEVEL} among all complexity-contributing nodes found
	 * anywhere in {@code method}'s body. Since nesting levels only ever
	 * increase going deeper, the shallowest level actually recorded
	 * anywhere in the method necessarily belongs to a node at the method's
	 * own starting depth, correctly reflecting the method's own ambient
	 * level regardless of how deeply the anonymous/local class itself is
	 * nested in the true method.
	 * </p>
	 */
	private static int computeAmbientLevelForMethod(MethodDeclaration method) {
		if (method == null || method.getBody() == null) {
			return 0;
		}

		List<ASTNode> nodesInMethod = new ArrayList<>();
		collectComplexityNodes(method.getBody(), nodesInMethod);

		int min = Integer.MAX_VALUE;
		for (ASTNode n : nodesInMethod) {
			int level = getIntegerPropertyOfNode(n, Constants.NESTING_LEVEL);
			if (level < min) {
				min = level;
			}
		}

		// No complexity-contributing statements found in the method at all
		// (degenerate case, e.g. an empty or trivial anonymous method): no
		// "ambient" siblings to compete with, 0 is a safe, harmless default.
		return min == Integer.MAX_VALUE ? 0 : min;
	}

	/**
	 * {@code true} when extracting {@code node} would produce a method that
	 * becomes fully independent of the "true" method being refactored (the
	 * normal case), or {@code false} when it would remain nested inside an
	 * anonymous/local class that the true method's own AST subtree already
	 * contains.
	 * <p>
	 * Callers reasoning about combining or comparing the reduction figures of
	 * several sequences (e.g. {@code Solution}'s parent/child nesting
	 * adjustment) MUST consult this before assuming a sequence's
	 * {@code CodeExtractionMetrics.reductionOfCognitiveComplexity} behaves
	 * like a straightforward sum of its descendants' contributions: that is
	 * only true when this method returns {@code true} for the sequence's own
	 * root node. When it returns {@code false}, the reduction already
	 * reflects a uniform per-subtree "shift" that does NOT double-count
	 * anything a nested sibling sequence might also independently extract,
	 * and must NOT be further adjusted by subtracting a nested sequence's own
	 * reduction from it.
	 * </p>
	 */
	public static boolean isEscapingExtraction(ASTNode node) {
		if (node == null) {
			return true;
		}
		MethodDeclaration immediateEnclosingMethod = getImmediateEnclosingMethodDeclaration(node);
		MethodDeclaration trueMethod = getMethodDeclaration(node);
		return immediateEnclosingMethod == null || immediateEnclosingMethod.equals(trueMethod);
	}

	private static void collectComplexityNodes(ASTNode node, List<ASTNode> result) {
		if (node.getProperty(Constants.CONTRIBUTION_TO_COGNITIVE_COMPLEXITY) != null) {
			result.add(node);
		}

		// Use the safe generic getChildren method
		List<ASTNode> children = getChildren(node);
		for (ASTNode child : children) {
			collectComplexityNodes(child, result);
		}
	}

	private static boolean paysNestingPenalty(ASTNode node) {
		if (node instanceof IfStatement) {
			// "else if" does not pay a penalty
			if (node.getParent() instanceof IfStatement
					&& node.getLocationInParent() == IfStatement.ELSE_STATEMENT_PROPERTY) {
				return false;
			}
			return true;
		}

		return node instanceof ForStatement || node instanceof EnhancedForStatement || node instanceof WhileStatement
				|| node instanceof DoStatement || node instanceof CatchClause || node instanceof SwitchStatement
				|| node instanceof ConditionalExpression || node instanceof LambdaExpression;
	}

	public static int computeEffort(int cognitiveComplexity, int threshold, int initialEffort,
			int linearEffortIncrement) {
		if (cognitiveComplexity > threshold) {
			return initialEffort + ((cognitiveComplexity - threshold) * linearEffortIncrement);
		}
		return 0;
	}

	// =========================================================================
	// GENERAL HELPERS & VISITORS
	// =========================================================================

	/**
	 * Gets the fully qualified class name from an ICompilationUnit.
	 */
	public static String getFullyQualifiedClassName(ICompilationUnit compilationUnit) {
		IType primaryType = compilationUnit.findPrimaryType();
		return (primaryType != null) ? primaryType.getFullyQualifiedName() : null;
	}

	/**
	 * Calculates the total length (in characters) of all import declarations.
	 */
	public static int lengthImportDeclaration(CompilationUnit compilationUnit) {
		int result = 0;
		for (Object importDeclaration : compilationUnit.imports()) {
			result += ((ImportDeclaration) importDeclaration).getLength();
		}
		return result;
	}

	/**
	 * Perform undo changes (reversed order).
	 * 
	 * @param changes List of changes to undo.
	 */
	public static void undoChanges(List<Change> changes) {
		for (int i = changes.size() - 1; i >= 0; i--) {
			try {
				changes.get(i).perform(null);
			} catch (CoreException e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * Perform changes (sequential order).
	 * 
	 * @param changes List of changes to apply.
	 */
	public static void performChanges(List<Change> changes) {
		for (Change change : changes) {
			try {
				change.perform(null);
			} catch (CoreException e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * Visitor that locates AST nodes within a specific editor selection range.
	 */
	public static class NodeFinderVisitorForGivenSelection extends ASTVisitor {
		private List<ASTNode> nodes;
		private ASTNode parent;
		private int fStart;
		private int fEnd;
		private ASTNode fCoveringNode;
		private ASTNode fCoveredNode;

		public NodeFinderVisitorForGivenSelection(ASTNode root, int offset, int length) {
			super(true);
			nodes = new ArrayList<>();
			this.fStart = offset;
			this.fEnd = offset + length;
			root.accept(this);
		}

		public List<ASTNode> getNodes() {
			return nodes;
		}

		@Override
		public boolean preVisit2(ASTNode node) {
			int nodeStart = node.getStartPosition();
			int nodeEnd = nodeStart + node.getLength();

			if (this.fStart <= nodeStart && nodeEnd <= this.fEnd) {
				if (nodes.isEmpty()) {
					parent = node.getParent();
					nodes.add(node);
				} else if (node.getParent() == parent) {
					nodes.add(node);
				}
			}

			if (nodeEnd < this.fStart || this.fEnd < nodeStart) {
				return false;
			}
			if (nodeStart <= this.fStart && this.fEnd <= nodeEnd) {
				this.fCoveringNode = node;
			}
			if (this.fStart <= nodeStart && nodeEnd <= this.fEnd) {
				if (this.fCoveringNode == node) {
					this.fCoveredNode = node;
					return true;
				} else if (this.fCoveredNode == null) {
					this.fCoveredNode = node;
				}
				return false;
			}
			return true;
		}
	}

	/**
	 * Visitor that finds a MethodDeclaration based on name and parameter string
	 * representation.
	 */
	public static class MethodDeclarationFinderVisitor extends ASTVisitor {
		private String methodLookingFor;
		private List<SingleVariableDeclaration> methodParameters;
		private boolean found;
		private MethodDeclaration methodDeclaration;

		public MethodDeclarationFinderVisitor(ASTNode root, String methodName,
				List<SingleVariableDeclaration> parameters) {
			super(true);
			this.methodLookingFor = methodName;
			this.methodParameters = parameters;
			this.methodDeclaration = null;
			this.found = false;
			root.accept(this);
		}

		public boolean found() {
			return this.found;
		}

		public MethodDeclaration getMethodDeclaration() {
			return methodDeclaration;
		}

		@Override
		public boolean visit(MethodDeclaration method) {
			// If methodParameters is null, we only match by name (first overload found).
			// Otherwise, we do the strict parameter toString() match.
			if (method.getName().getIdentifier().equals(this.methodLookingFor)) {
				if (this.methodParameters == null || 
					method.parameters().toString().equals(methodParameters.toString())) {
					
					methodDeclaration = method;
					found = true;
					return false; // Stop visiting
				}
			}
			return true;
		}
	}
}