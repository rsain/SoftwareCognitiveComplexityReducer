package neo.reducecognitivecomplexity.core.jdt;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

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
import org.eclipse.jdt.internal.corext.refactoring.code.ExtractMethodRefactoring;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;

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
	static CompilationUnit parse(ICompilationUnit sourceFile) {
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
	 * Refactor a compilation unit extracting the given source code as a new method.
	 * <p>
	 * This method uses the Eclipse JDT {@link ExtractMethodRefactoring} class to
	 * perform the heavy lifting. It handles checking initial and final conditions
	 * (pre-conditions).
	 * </p>
	 *
	 * @param originalUnit        The compilation unit under processing.
	 * @param selectionStart      The offset of the start of the code to refactor.
	 * @param selectionLength     The length of the code to refactor.
	 * @param extractedMethodName Name of the new method.
	 * @param simulation          If true, performs the refactoring to gather
	 *                            metrics but undoes it immediately.
	 * @return {@link CodeExtractionMetrics} containing feasibility, LOC, and
	 *         parameters of the extracted method.
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

		System.out.println("Processing Method Extraction: " + extractedMethodName);
		long startTime = System.currentTimeMillis();
		long runtime = startTime;

		try {
			// Convert AST to Java Model (Working Copy) needed for Refactoring
			workingCopy = getICompilationUnit(originalUnit).getWorkingCopy(npm);

			System.out.println(">> Creating ExtractMethodRefactoring...");
			ExtractMethodRefactoring refactoring = new ExtractMethodRefactoring(workingCopy, selectionStart,
					selectionLength);
			refactoring.setMethodName(extractedMethodName);

			System.out.println(">> Checking Initial Conditions...");
			RefactoringStatus status = refactoring.checkInitialConditions(npm);

			if (status.isOK()) {
				System.out.println(">> Initial Conditions OK. Checking Final Conditions...");
				status = refactoring.checkFinalConditions(npm);

				if (status.isOK()) {
					System.out.println(">> Final Conditions OK. Performing Change...");
					resultOfRefactoring = "OK";

					numberOfExtractedLinesOfCode = numberOfLinesOfCode(originalUnit, selectionStart, selectionLength);
					numberOfParametersInExtractedMethod = refactoring.getParameterInfos().size();
					refactoring.setReplaceDuplicates(false);

					Change c = refactoring.createChange(npm);
					Change undo = c.perform(npm);
					runtime = System.currentTimeMillis() - startTime;

					// Verify compilation after change
					CompilationUnit astAfter = parse(workingCopy);
					boolean compilationErrors = builtWithCompilationErrors(astAfter);

					if (compilationErrors) {
						System.out.println(">> Refactoring failed compilation check.");
						resultOfRefactoring = "Compilation unit does not compile after method extraction.";
						feasible = false;
						undo.perform(npm); // Revert
					} else {
						System.out.println(">> Refactoring SUCCESS.");
						changes.add(c);
						undoChanges.add(undo);
						if (simulation) {
							undo.perform(npm); // Revert if simulation
						}
					}
					refactoringApplied = !compilationErrors && !simulation;
				} else {
					feasible = false;
					runtime = System.currentTimeMillis() - startTime;
					resultOfRefactoring = status.getEntryAt(0).getMessage();
					System.out.println(">> Final Conditions Failed: " + resultOfRefactoring);
				}
			} else {
				feasible = false;
				runtime = System.currentTimeMillis() - startTime;
				resultOfRefactoring = status.getEntryAt(0).getMessage();
				System.out.println(">> Initial Conditions Failed: " + resultOfRefactoring);
			}

		} catch (IllegalArgumentException | CoreException e) {
			System.err.println("ERROR while refactoring: " + e.getMessage());
			e.printStackTrace();
			feasible = false;
			runtime = System.currentTimeMillis() - startTime;
			resultOfRefactoring = "Exception: " + e.getMessage();
			// Note: System.exit(1) removed for library safety; throw exception or log
			// instead in production
		} finally {
			if (workingCopy != null) {
				try {
					workingCopy.discardWorkingCopy();
				} catch (Exception e) {
					/* ignore */ }
			}
		}

		return new CodeExtractionMetrics(feasible, resultOfRefactoring, refactoringApplied,
				numberOfExtractedLinesOfCode, numberOfParametersInExtractedMethod, changes, undoChanges, runtime);
	}

	// =========================================================================
	// AST HELPERS & METRICS
	// =========================================================================

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

			// Special handling: if we hit a method inside an anonymous class,
			// we look upwards.
			if (result instanceof MethodDeclaration) {
				if (result.getParent() instanceof AnonymousClassDeclaration) {
					result = result.getParent();
				}
			}
		}

		return (MethodDeclaration) result;
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
	 * Simulates the Cognitive Complexity metrics that would result if the given AST
	 * node (and its children) were extracted into a separate method.
	 * <p>
	 * This method calculates the complexity of the code block <i>as if</i> it were
	 * at the top level of a new method. It achieves this by:
	 * <ol>
	 * <li>Determining the current base nesting level of the node.</li>
	 * <li>Iterating through all complexity-contributing descendants.</li>
	 * <li>Removing the penalty of the current base nesting level from their
	 * scores.</li>
	 * </ol>
	 * * @param node The AST node (usually a Block or Statement) representing the
	 * code to be extracted.
	 * 
	 * @return A {@link CognitiveComplexityMetrics} object containing the projected
	 *         complexity stats for the hypothetical new method.
	 */
	public static CognitiveComplexityMetrics computeMetricsIfExtracted(ASTNode node) {
		if (node == null) {
			return new CognitiveComplexityMetrics(0, 0, 0, 0, 0, 0);
		}

		// 1. Base Level: The nesting level of the extraction root in the original code.
		int baseLevel = getIntegerPropertyOfNode(node, Constants.NESTING_LEVEL);

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

			// 2. Calculate Inherent (Original) Complexity
			int currentNestingCost = paysPenalty ? currentLevel : 0;
			int inherent = contribution - currentNestingCost;

			// 3. Calculate Projected (New) Complexity
			// Calculate depth relative to the new method root (0-based)
			int newDepth = Math.max(0, currentLevel - baseLevel);
			int projectedNestingCost = paysPenalty ? newDepth : 0;
			int projectedNodeComplexity = inherent + projectedNestingCost;

			// 4. Update Totals
			totalReduction += contribution;
			totalInherent += inherent;
			totalNewComplexity += projectedNodeComplexity;

			// 5. Update Nesting Contributor Count
			if (paysPenalty) {
				// CASE A: Deep nodes (Children/Inner Loops)
				// These always pay a nesting cost in the new method.
				if (newDepth > 0) {
					numberOfNestingContributors++;
				}
				// CASE B: Surface nodes (Root, Catch, Else, etc.)
				// These are at the top level of the new method (Depth 0).
				// They only contribute to the nesting count if the *original* context was
				// nested.
				else if (baseLevel > 0) {
					numberOfNestingContributors++;
				}
			}
		}

		int totalProjectedNesting = totalNewComplexity - totalInherent;

		return new CognitiveComplexityMetrics(totalInherent, totalProjectedNesting, totalNewComplexity, totalReduction,
				numberOfNestingContributors, baseLevel);
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
			// Note: toString() comparison on parameters is fragile but
			// often necessary without full binding resolution.
			if (method.getName().getIdentifier().equals(this.methodLookingFor)
					&& method.parameters().toString().equals(methodParameters.toString())) {
				methodDeclaration = method;
				found = true;
				return false; // Stop visiting
			}
			return true;
		}
	}
}
