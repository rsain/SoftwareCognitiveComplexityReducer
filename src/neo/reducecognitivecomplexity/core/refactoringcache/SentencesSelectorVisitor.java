package neo.reducecognitivecomplexity.core.refactoringcache;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.SwitchCase;
import org.eclipse.jdt.core.dom.SwitchStatement;

import neo.reducecognitivecomplexity.app.Constants;
import neo.reducecognitivecomplexity.core.Sequence;

/**
 * Visitor that traverses the AST to identify valid "sentences" (sequences of
 * statements) that are candidates for extraction.
 * <p>
 * It identifies:
 * <ul>
 * <li>Statements within Blocks.</li>
 * <li>Single-statement bodies of control flow structures (e.g., one-line
 * if/while).</li>
 * <li>Groups of statements within Switch cases.</li>
 * </ul>
 * </p>
 */
public class SentencesSelectorVisitor extends ASTVisitor {

	private final CompilationUnit compilationUnit;
	private final List<Sequence> sentencesToIterate;

	public SentencesSelectorVisitor(CompilationUnit cu) {
		this.compilationUnit = cu;
		this.sentencesToIterate = new ArrayList<>();
	}

	@Override
	public void preVisit(ASTNode node) {
		// 1. Handle Blocks: Treat the entire list of statements in a block as a
		// potential sequence source.
		if (node instanceof Block) {
			Block block = (Block) node;
			// specific cast handling for JDT generic lists
			List<ASTNode> statements = new ArrayList<>(block.statements());
			Sequence sequence = new Sequence(this.compilationUnit, statements);
			getSentencesToIterate().add(sequence);
		}
		// 2. Handle Single Statements in Control Flow (e.g., "if (cond) statement;")
		// Note: Blocks are technically Statements, but they are caught by the 'if'
		// above.
		else if (node instanceof Statement) {
			ASTNode parent = node.getParent();
			if (parent != null) {
				switch (parent.getNodeType()) {
				case ASTNode.DO_STATEMENT:
				case ASTNode.ENHANCED_FOR_STATEMENT:
				case ASTNode.FOR_STATEMENT:
				case ASTNode.IF_STATEMENT:
				case ASTNode.WHILE_STATEMENT:
					Integer acc = (Integer) node
							.getProperty(Constants.ACCUMULATED_CONTRIBUTION_TO_COGNITIVE_COMPLEXITY);
					if (acc != null && acc > 0) {
						getSentencesToIterate().add(new Sequence(this.compilationUnit, Arrays.asList(node)));
					}
					break;
				}
			}
		}

		// 3. Handle Switch Statements: Group statements between cases.
		if (node instanceof SwitchStatement) {
			SwitchStatement switchStmt = (SwitchStatement) node;
			List<ASTNode> currentSequenceNodes = new ArrayList<>();

			for (Object obj : switchStmt.statements()) {
				Statement stmt = (Statement) obj;

				if (stmt instanceof SwitchCase) {
					// Case label found: seal the previous sequence if it exists
					if (!currentSequenceNodes.isEmpty()) {
						getSentencesToIterate().add(new Sequence(this.compilationUnit, currentSequenceNodes));
						currentSequenceNodes = new ArrayList<>();
					}
					// Note: We do not add the SwitchCase (label) itself to the sequence
					// because we extract the logic *between* cases.
				} else {
					currentSequenceNodes.add(stmt);
				}
			}

			// Add any remaining statements after the last case
			if (!currentSequenceNodes.isEmpty()) {
				getSentencesToIterate().add(new Sequence(this.compilationUnit, currentSequenceNodes));
			}
		}

		super.preVisit(node);
	}

	public List<Sequence> getSentencesToIterate() {
		return sentencesToIterate;
	}
}