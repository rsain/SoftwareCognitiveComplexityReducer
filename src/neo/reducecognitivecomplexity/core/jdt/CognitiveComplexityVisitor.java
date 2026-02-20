package neo.reducecognitivecomplexity.core.jdt;

import org.eclipse.jdt.core.dom.*;
import neo.reducecognitivecomplexity.app.Constants;
import java.util.*;

/**
 * A visitor that computes the Cognitive Complexity of a Java method using the
 * Eclipse JDT AST.
 * <p>
 * This visitor traverses the AST of a {@link MethodDeclaration} and calculates
 * complexity based on:
 * <ul>
 * <li><b>Inherent Complexity:</b> Control flow structures (if, while, for,
 * catch) and logical operators (&&, ||).</li>
 * <li><b>Nesting Complexity:</b> Penalties applied to control flow structures
 * deeply nested within others.</li>
 * </ul>
 * <p>
 * It computes accumulated metrics for each node to analyze which specific
 * blocks of code contribute most to the total complexity.
 */
public class CognitiveComplexityVisitor extends ASTVisitor {

	// =========================================================================
	// INNER CLASSES: RESULT & LOCATION
	// =========================================================================

	/**
	 * Represents the final result of the cognitive complexity analysis.
	 */
	public static class Result {
		public final int complexity;
		public final List<Location> locations;

		public Result(int complexity, List<Location> locations) {
			this.complexity = complexity;
			this.locations = locations;
		}

		public static Result empty() {
			return new Result(0, Collections.emptyList());
		}
	}

	/**
	 * Represents a specific point in the code that contributes to complexity.
	 */
	public static class Location {
		public final String message;
		public final ASTNode node;
		public final String nodeType;

		// Metrics for this specific node
		public final int contributionToCognitiveComplexity;
		public final int inherentCognitiveComplexity;
		public final int nestingCognitiveComplexity;
		public final int nestingLevel;

		// Accumulated metrics (Node + Descendants)
		public final int accumulatedContributionToCognitiveComplexity;
		public final int accumulatedInherentCognitiveComplexity;
		public final int accumulatedNestingCognitiveComplexity;
		public final int numberOfNestingContributors;

		// Position info
		public final int startPosition;
		public final int length;
		public final int lineNumber;

		public Location(String message, ASTNode node, String specificType, int contribution, int inherent,
				int nestingCost, int nestingLevel, int accContribution, int accInherent, int accNesting,
				int nestingCount) {
			this.message = message;
			this.node = node;
			this.nodeType = (specificType != null) ? specificType : node.getClass().getSimpleName();

			this.contributionToCognitiveComplexity = contribution;
			this.inherentCognitiveComplexity = inherent;
			this.nestingCognitiveComplexity = nestingCost;
			this.nestingLevel = nestingLevel;

			this.accumulatedContributionToCognitiveComplexity = accContribution;
			this.accumulatedInherentCognitiveComplexity = accInherent;
			this.accumulatedNestingCognitiveComplexity = accNesting;
			this.numberOfNestingContributors = nestingCount;

			this.startPosition = node.getStartPosition();
			this.length = node.getLength();

			ASTNode root = node.getRoot();
			this.lineNumber = (root instanceof CompilationUnit)
					? ((CompilationUnit) root).getLineNumber(this.startPosition)
					: -1;
		}
	}

	/**
	 * Internal context object used to track the state of a node on the visitation
	 * stack.
	 */
	private static class NodeContext {
		final ASTNode node;
		final String specificType;
		final String message;

		// Immediate properties
		int contribution;
		int inherent;
		final int nestingCost;
		final int nestingLevel;

		// Accumulators for children
		int accContribution;
		int accInherent;
		int accRawNesting;
		int nestingCount;

		public NodeContext(ASTNode node, String specificType, String message, int contribution, int inherent,
				int nestingCost, int nestingLevel) {
			this.node = node;
			this.specificType = specificType;
			this.message = message;
			this.contribution = contribution;
			this.inherent = inherent;
			this.nestingCost = nestingCost;
			this.nestingLevel = nestingLevel;

			// Initialize accumulators
			this.accContribution = contribution;
			this.accInherent = inherent;
			this.accRawNesting = nestingCost;
			this.nestingCount = (nestingCost > 0) ? 1 : 0;
		}
	}

	// =========================================================================
	// VISITOR STATE
	// =========================================================================

	private final List<Location> locations = new ArrayList<>();
	private final Set<ASTNode> ignored = new HashSet<>();
	private final Stack<NodeContext> stack = new Stack<>();
	private int complexity = 0;
	private int currentNestingLevel = 0;

	private CognitiveComplexityVisitor() {
	}

	/**
	 * Entry point for calculating the complexity of a method.
	 */
	public static Result methodComplexity(MethodDeclaration method) {
		if (shouldAnalyzeMethod(method)) {
			CognitiveComplexityVisitor visitor = new CognitiveComplexityVisitor();
			method.accept(visitor);

			method.setProperty(Constants.CONTRIBUTION_TO_COGNITIVE_COMPLEXITY, visitor.complexity);
			return new Result(visitor.complexity, visitor.locations);
		}
		return Result.empty();
	}

	// =========================================================================
	// CORE LOGIC (STACK & ACCOUNTING)
	// =========================================================================

	private void startNodeByNesting(ASTNode node) {
		int nestingCost = currentNestingLevel;
		int inherent = 1;
		int contribution = inherent + nestingCost;
		startNode(node, contribution, inherent, nestingCost, currentNestingLevel, null);
	}

	private void startNodeByOne(ASTNode node) {
		startNode(node, 1, 1, 0, currentNestingLevel, "operator");
	}

	private void startContainerNode(ASTNode node) {
		// Container nodes cost 0 but accumulate children's complexity
		startNode(node, 0, 0, 0, currentNestingLevel, "container");
	}

	private void startNode(ASTNode node, int contribution, int inherent, int nestingCost, int level,
			String specificType) {
		complexity += contribution;
		String msg = "+" + contribution;
		if (nestingCost > 0) {
			msg += " (incl " + nestingCost + " for nesting)";
		}
		stack.push(new NodeContext(node, specificType, msg, contribution, inherent, nestingCost, level));
	}

	private void endNode() {
		if (stack.isEmpty())
			return;

		NodeContext ctx = stack.pop();

		// 1. Calculate Accumulated Metrics
		// descendantsRawNestingSum - (descendantsCount * currentLevel)
		// calculates the nesting specifically introduced by children relative to this
		// node.
		int descendantsRawNestingSum = ctx.accRawNesting - ctx.nestingCost;
		int selfCount = (ctx.nestingCost > 0) ? 1 : 0;
		int descendantsCount = ctx.nestingCount - selfCount;

		int accumulatedComplexityByNesting = descendantsRawNestingSum - (descendantsCount * ctx.nestingLevel);
		if (accumulatedComplexityByNesting < 0)
			accumulatedComplexityByNesting = 0;

		// 2. Set Standard Properties on the AST Node
		ctx.node.setProperty(Constants.CONTRIBUTION_TO_COGNITIVE_COMPLEXITY, ctx.contribution);
		ctx.node.setProperty(Constants.INHERENT_COGNITIVE_COMPLEXITY, ctx.inherent);
		ctx.node.setProperty(Constants.NESTING_COGNITIVE_COMPLEXITY, ctx.nestingCost);
		ctx.node.setProperty(Constants.NESTING_LEVEL, ctx.nestingLevel);

		ctx.node.setProperty(Constants.ACCUMULATED_CONTRIBUTION_TO_COGNITIVE_COMPLEXITY, ctx.accContribution);
		ctx.node.setProperty(Constants.ACCUMULATED_INHERENT_COGNITIVE_COMPLEXITY, ctx.accInherent);
		ctx.node.setProperty(Constants.ACCUMULATED_NESTING_COGNITIVE_COMPLEXITY, accumulatedComplexityByNesting);
		ctx.node.setProperty(Constants.NUMBER_OF_NESTING_CONTRIBUTORS, ctx.nestingCount);

		// 3. Set properties for transparent containers
		if (ctx.contribution == 0) {
			ctx.node.setProperty(Constants.COGNITIVE_COMPLEXITY_OF_NESTED_CODE, ctx.accContribution);
		}

		// 4. Create Location entry
		if (ctx.contribution > 0) {
			locations.add(new Location(ctx.message, ctx.node, ctx.specificType, ctx.contribution, ctx.inherent,
					ctx.nestingCost, ctx.nestingLevel, ctx.accContribution, ctx.accInherent,
					accumulatedComplexityByNesting, ctx.nestingCount));
		}

		// 5. Propagate totals to parent
		if (!stack.isEmpty()) {
			NodeContext parent = stack.peek();
			parent.accContribution += ctx.accContribution;
			parent.accInherent += ctx.accInherent;
			parent.accRawNesting += ctx.accRawNesting;
			parent.nestingCount += ctx.nestingCount;
		}
	}

	// =========================================================================
	// VISIT METHODS: CONTROL FLOW
	// =========================================================================

	@Override
	public boolean visit(IfStatement node) {
		boolean isElseIf = false;
		if (node.getParent() instanceof IfStatement) {
			IfStatement parent = (IfStatement) node.getParent();
			if (parent.getElseStatement() == node) {
				isElseIf = true;
			}
		}

		if (isElseIf) {
			startNode(node, 1, 1, 0, currentNestingLevel, "else if");
		} else {
			startNodeByNesting(node);
		}

		node.getExpression().accept(this);

		currentNestingLevel++;
		node.getThenStatement().accept(this);
		currentNestingLevel--;

		Statement elseStmt = node.getElseStatement();
		if (elseStmt != null) {
			if (elseStmt instanceof IfStatement) {
				// "else if" -> Child handles itself
				elseStmt.accept(this);
			} else {
				// Pure "else" -> Adds +1 to THIS IfStatement
				complexity++;

				if (!stack.isEmpty()) {
					NodeContext ifContext = stack.peek();

					// 1. Update the LOCAL cost of the IfStatement node
					ifContext.inherent += 1;
					ifContext.contribution += 1;

					// 2. Update the ACCUMULATORS
					ifContext.accInherent += 1;
					ifContext.accContribution += 1;

					// 3. Add a location entry for the 'else' keyword
					locations.add(new Location("else", node, "else", 1, 1, 0, currentNestingLevel, 0, 0, 0, 0));
				}

				currentNestingLevel++;
				elseStmt.accept(this);
				currentNestingLevel--;
			}
		}

		endNode();
		return false;
	}

	@Override
	public boolean visit(SwitchStatement node) {
		startNodeByNesting(node);
		currentNestingLevel++;
		return true;
	}

	@Override
	public void endVisit(SwitchStatement node) {
		currentNestingLevel--;
		endNode();
	}

	@Override
	public boolean visit(ForStatement node) {
		startNodeByNesting(node);
		currentNestingLevel++;
		return true;
	}

	@Override
	public void endVisit(ForStatement node) {
		currentNestingLevel--;
		endNode();
	}

	@Override
	public boolean visit(EnhancedForStatement node) {
		startNodeByNesting(node);
		currentNestingLevel++;
		return true;
	}

	@Override
	public void endVisit(EnhancedForStatement node) {
		currentNestingLevel--;
		endNode();
	}

	@Override
	public boolean visit(WhileStatement node) {
		startNodeByNesting(node);
		currentNestingLevel++;
		return true;
	}

	@Override
	public void endVisit(WhileStatement node) {
		currentNestingLevel--;
		endNode();
	}

	@Override
	public boolean visit(DoStatement node) {
		startNodeByNesting(node);
		currentNestingLevel++;
		return true;
	}

	@Override
	public void endVisit(DoStatement node) {
		currentNestingLevel--;
		endNode();
	}

	@Override
	public boolean visit(CatchClause node) {
		startNodeByNesting(node);
		currentNestingLevel++;
		return true;
	}

	@Override
	public void endVisit(CatchClause node) {
		currentNestingLevel--;
		endNode();
	}

	@Override
	public boolean visit(ConditionalExpression node) {
		startNodeByNesting(node);
		currentNestingLevel++;
		return true;
	}

	@Override
	public void endVisit(ConditionalExpression node) {
		currentNestingLevel--;
		endNode();
	}

	@Override
	public boolean visit(BreakStatement node) {
		if (node.getLabel() != null) {
			startNodeByOne(node);
			endNode();
		}
		return true;
	}

	@Override
	public boolean visit(ContinueStatement node) {
		if (node.getLabel() != null) {
			startNodeByOne(node);
			endNode();
		}
		return true;
	}

	// =========================================================================
	// VISIT METHODS: EXPRESSIONS & OPERATORS
	// =========================================================================

	@Override
	public boolean visit(InfixExpression node) {
		boolean isLogical = node.getOperator() == InfixExpression.Operator.CONDITIONAL_AND
				|| node.getOperator() == InfixExpression.Operator.CONDITIONAL_OR;

		if (isLogical && !ignored.contains(node)) {
			List<InfixExpression> flattened = flattenLogicalExpression(node);

			Map<InfixExpression, Integer> nodeTotals = new IdentityHashMap<>();
			InfixExpression previous = null;

			// 1. Calculate costs for sequence of operators
			for (InfixExpression current : flattened) {
				boolean isNewSequence = (previous == null || !previous.getOperator().equals(current.getOperator()));

				if (isNewSequence) {
					locations.add(
							new Location("operator", current, "operator", 1, 1, 0, currentNestingLevel, 0, 0, 0, 0));
					nodeTotals.merge(current, 1, Integer::sum);
				}
				previous = current;
			}

			// 2. Apply properties AND update Stack manually
			for (Map.Entry<InfixExpression, Integer> entry : nodeTotals.entrySet()) {
				InfixExpression expr = entry.getKey();
				int totalCost = entry.getValue();

				complexity += totalCost;

				expr.setProperty(Constants.CONTRIBUTION_TO_COGNITIVE_COMPLEXITY, totalCost);
				expr.setProperty(Constants.INHERENT_COGNITIVE_COMPLEXITY, totalCost);
				expr.setProperty(Constants.NESTING_COGNITIVE_COMPLEXITY, 0);
				expr.setProperty(Constants.NESTING_LEVEL, currentNestingLevel);

				expr.setProperty(Constants.ACCUMULATED_CONTRIBUTION_TO_COGNITIVE_COMPLEXITY, 0);
				expr.setProperty(Constants.ACCUMULATED_INHERENT_COGNITIVE_COMPLEXITY, 0);
				expr.setProperty(Constants.ACCUMULATED_NESTING_COGNITIVE_COMPLEXITY, 0);
				expr.setProperty(Constants.NUMBER_OF_NESTING_CONTRIBUTORS, 0);

				if (!stack.isEmpty()) {
					NodeContext parentCtx = stack.peek();
					parentCtx.accContribution += totalCost;
					parentCtx.accInherent += totalCost;
				}
			}
		}
		return true;
	}

	@Override
	public boolean visit(LambdaExpression node) {
		currentNestingLevel++;
		return true;
	}

	@Override
	public void endVisit(LambdaExpression node) {
		currentNestingLevel--;
	}

	// =========================================================================
	// VISIT METHODS: CONTAINERS & TRANSPARENT NODES
	// =========================================================================

	@Override
	public boolean visit(MethodDeclaration node) {
		startContainerNode(node);
		return true;
	}

	@Override
	public void endVisit(MethodDeclaration node) {
		endNode();
	}

	@Override
	public boolean visit(ClassInstanceCreation node) {
		startContainerNode(node);
		return true;
	}

	@Override
	public void endVisit(ClassInstanceCreation node) {
		endNode();
	}

	@Override
	public boolean visit(LabeledStatement node) {
		startContainerNode(node);
		return true;
	}

	@Override
	public void endVisit(LabeledStatement node) {
		endNode();
	}

	@Override
	public boolean visit(ExpressionStatement node) {
		startContainerNode(node);
		return true;
	}

	@Override
	public void endVisit(ExpressionStatement node) {
		endNode();
	}

	@Override
	public boolean visit(ReturnStatement node) {
		startContainerNode(node);
		return true;
	}

	@Override
	public void endVisit(ReturnStatement node) {
		endNode();
	}

	@Override
	public boolean visit(VariableDeclarationStatement node) {
		startContainerNode(node);
		return true;
	}

	@Override
	public void endVisit(VariableDeclarationStatement node) {
		endNode();
	}

	@Override
	public boolean visit(Block node) {
		startContainerNode(node);
		return true;
	}

	@Override
	public void endVisit(Block node) {
		endNode();
	}

	@Override
	public boolean visit(TryStatement node) {
		startContainerNode(node);
		return true;
	}

	@Override
	public void endVisit(TryStatement node) {
		endNode();
	}

	@Override
	public boolean visit(SynchronizedStatement node) {
		startContainerNode(node);
		return true;
	}

	@Override
	public void endVisit(SynchronizedStatement node) {
		endNode();
	}

	@Override
	public boolean visit(AnonymousClassDeclaration node) {
		currentNestingLevel++;
		startContainerNode(node);
		return true;
	}

	@Override
	public void endVisit(AnonymousClassDeclaration node) {
		currentNestingLevel--;
		endNode();
	}

	@Override
	public boolean visit(TypeDeclaration node) {
		currentNestingLevel++;
		return true;
	}

	@Override
	public void endVisit(TypeDeclaration node) {
		currentNestingLevel--;
	}

	// =========================================================================
	// HELPER METHODS
	// =========================================================================

	/**
	 * Flattens a binary expression tree to correctly calculate the "sequence"
	 * bonus.
	 */
	private List<InfixExpression> flattenLogicalExpression(Expression expression) {
		List<InfixExpression> list = new ArrayList<>();
		Expression cleanExpr = skipParentheses(expression);
		if (cleanExpr instanceof InfixExpression) {
			InfixExpression infix = (InfixExpression) cleanExpr;
			boolean isLogical = infix.getOperator() == InfixExpression.Operator.CONDITIONAL_AND
					|| infix.getOperator() == InfixExpression.Operator.CONDITIONAL_OR;
			if (isLogical) {
				ignored.add(infix);
				list.addAll(flattenLogicalExpression(infix.getLeftOperand()));
				list.add(infix);
				list.addAll(flattenLogicalExpression(infix.getRightOperand()));
				if (infix.hasExtendedOperands()) {
					for (Object obj : infix.extendedOperands()) {
						list.add(infix);
						list.addAll(flattenLogicalExpression((Expression) obj));
					}
				}
				return list;
			}
		}
		return list;
	}

	private static Expression skipParentheses(Expression expression) {
		while (expression instanceof ParenthesizedExpression) {
			expression = ((ParenthesizedExpression) expression).getExpression();
		}
		return expression;
	}

	private static boolean shouldAnalyzeMethod(MethodDeclaration method) {
		if (method.getBody() == null)
			return false;
		if (isMemberOfAnonymousClass(method))
			return false;
		if (isWithinLocalClass(method))
			return false;
		if (isIgnoredSignature(method))
			return false;
		return true;
	}

	private static boolean isIgnoredSignature(MethodDeclaration method) {
		String name = method.getName().getIdentifier();
		int paramCount = method.parameters().size();
		if ("equals".equals(name) && paramCount == 1)
			return true;
		if ("hashCode".equals(name) && paramCount == 0)
			return true;
		return false;
	}

	private static boolean isMemberOfAnonymousClass(MethodDeclaration method) {
		return method.getParent() instanceof AnonymousClassDeclaration;
	}

	private static boolean isWithinLocalClass(MethodDeclaration method) {
		ASTNode parent = method.getParent();
		while (parent != null) {
			if (parent instanceof TypeDeclaration
					&& (parent.getParent() instanceof Block || parent.getParent() instanceof MethodDeclaration))
				return true;
			parent = parent.getParent();
		}
		return false;
	}
}