package test.neo.reducecognitivecomplexity.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.junit.jupiter.api.Test;

import neo.reducecognitivecomplexity.core.jdt.CognitiveComplexityMetrics;
import neo.reducecognitivecomplexity.core.jdt.CognitiveComplexityVisitor;
import neo.reducecognitivecomplexity.core.jdt.Utils;

/**
 * Regression test for the ambient-level NMCC in
 * {@link Utils#computeMetricsIfExtracted(ASTNode)}.
 * <p>
 * Builds a synthetic method whose complexity mostly lives inside an anonymous
 * class member method.
 * </p>
 */
public class ComputeMetricsIfExtractedNestedClassTest {

	private static final String SOURCE = "package test;\n" + "import java.util.List;\n"
			+ "import java.util.function.Consumer;\n" + "public class NestedComplexityTest {\n"
			+ "    public void process(List<String> items) {\n" + "        if (items != null) {\n" + // level 0
			"            if (!items.isEmpty()) {\n" + // level 1
			"                items.forEach(new Consumer<String>() {\n" + "                    @Override\n"
			+ "                    public void accept(String item) {\n"
			+ "                        if (item.startsWith(\"a\")) {\n" + // level 3 (ambient)
			"                            if (item.length() > 3) {\n" + // level 4 <- extraction root
			"                                if (item.contains(\"x\")) {\n" + // level 5
			"                                    if (item.endsWith(\"z\")) {\n" + // level 6
			"                                        if (item.indexOf('q') > 0) {\n" + // level 7
			"                                            System.out.println(item);\n"
			+ "                                        }\n" + "                                    }\n"
			+ "                                }\n" + "                            }\n" + "                        }\n"
			+ "                    }\n" + "                });\n" + "            }\n" + "        }\n" + "    }\n"
			+ "}\n";

	@Test
	public void nestedExtractionNmccIncludesAmbientLevelBonus() {
		// 1. Parse the synthetic source (no bindings needed, the visitor and
		// computeMetricsIfExtracted only rely on AST structure/node types).
		ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setSource(SOURCE.toCharArray());
		parser.setResolveBindings(false);
		CompilationUnit cu = (CompilationUnit) parser.createAST(null);

		// 2. Find the "true" method (process) and run the SAME visitor entry
		// point the real pipeline uses. This walks all the way into accept()
		// and its nested ifs too, since shouldAnalyzeMethod() only gates the
		// ENTRY point, not the traversal into nested MethodDeclarations.
		MethodDeclaration processMethod = findMethodByName(cu, "process");
		assertNotNull(processMethod, "Could not find method 'process' in the synthetic source");
		CognitiveComplexityVisitor.methodComplexity(processMethod);

		// 3. Locate the extraction candidate: if (item.length() > 3) { ... }
		ASTNode candidateRoot = findIfContaining(cu, "item.length() > 3");
		assertNotNull(candidateRoot, "Could not find the target 'if (item.length() > 3)' node");

		// Sanity check: this candidate must be recognized as NESTED (non-escaping),
		// i.e. living inside a method that is itself a member of an anonymous class.
		assertFalse(Utils.isEscapingExtraction(candidateRoot),
				"Expected candidate to be a NON-escaping (nested) extraction");

		// 4. Compute the metrics and verify the ambient-level bonus is applied.
		CognitiveComplexityMetrics metrics = Utils.computeMetricsIfExtracted(candidateRoot);

		int inherent = metrics.getAccumulatedInherentCognitiveComplexity();
		int nesting = metrics.getAccumulatedNestingCognitiveComplexity();
		int nmcc = inherent + nesting; // = ExtractionVertex#getComplexityWhenExtracted()
		int contributors = metrics.getNumberOfNestingContributors();

		assertEquals(4, inherent, "Inherent component should be unaffected");
		assertEquals(4, contributors, "Number of nesting contributors should be unaffected");

		// Code adds ambientLevel(3) * contributors(4) = 12,
		// giving 22, correctly reflecting that this new method, as a sibling
		// inside the SAME anonymous class, will actually be measured starting
		// at ambient depth 3, not depth 0.
		assertEquals(22, nmcc, "NMCC must include the ambient-level bonus for a nested extraction");
	}

	private static MethodDeclaration findMethodByName(CompilationUnit cu, String name) {
		final MethodDeclaration[] found = new MethodDeclaration[1];
		cu.accept(new ASTVisitor() {
			@Override
			public boolean visit(MethodDeclaration node) {
				if (node.getName().getIdentifier().equals(name)) {
					found[0] = node;
				}
				return true;
			}
		});
		return found[0];
	}

	private static ASTNode findIfContaining(CompilationUnit cu, String expressionSourceSnippet) {
		final ASTNode[] found = new ASTNode[1];
		cu.accept(new ASTVisitor() {
			@Override
			public boolean visit(IfStatement node) {
				if (node.getExpression() != null && node.getExpression().toString().equals(expressionSourceSnippet)) {
					found[0] = node;
				}
				return true;
			}
		});
		return found[0];
	}
}