package neo.reducecognitivecomplexity.core.jdt;

import java.util.ArrayList;
import java.util.List;
import org.eclipse.ltk.core.refactoring.Change;

/**
 * Container for metrics and status of a specific code extraction (Refactoring).
 * <p>
 * This class tracks whether an extraction is feasible, the complexity
 * reductions achieved, the specific changes (LTK Change objects) required to
 * perform it, and the performance cost of calculating it.
 * </p>
 */
public class CodeExtractionMetrics {

	/** Indicates if the code extraction is syntactically and semantically valid. */
	private boolean feasible;

	/**
	 * The reason why an extraction is invalid (if feasible is false). "OK" if
	 * valid.
	 */
	private String reason;

	/** Indicates if this specific extraction has been applied to the AST/Source. */
	private boolean applied;

	/** The number of lines of code moved to the new method. */
	private int numberOfExtractedLinesOfCode;

	/** The number of parameters required by the new extracted method. */
	private int numberOfParametersInExtractedMethod;

	/** The net reduction in Cognitive Complexity achieved by this extraction. */
	private int reductionOfCognitiveComplexity;

	/** The inherent complexity component of the extracted code. */
	private int accumulatedInherentComponent;

	/** The nesting complexity component of the extracted code. */
	private int accumulatedNestingComponent;

	/** The number of structures contributing to nesting in the extracted code. */
	private int numberNestingContributors;

	/** The nesting level of the extracted code. */
	private int nesting;

	/**
	 * The ordered list of changes required to perform this extraction. Typically
	 * contains one change, but can support a sequence.
	 */
	private List<Change> changes = new ArrayList<>();

	/**
	 * The ordered list of changes required to UNDO this extraction. Used for
	 * backtracking solvers or rollbacks.
	 */
	private List<Change> undoChanges = new ArrayList<>();

	/** Time taken to calculate this extraction (in milliseconds). */
	private long runtime;

	// =========================================================================
	// CONSTRUCTORS
	// =========================================================================

	public CodeExtractionMetrics(boolean feasible, String reason, boolean applied, int numberOfExtractedLinesOfCode,
			int numberOfParametersInExtractedMethod, List<Change> changes, List<Change> undoChanges, long runtime) {
		this(feasible, reason, applied, numberOfExtractedLinesOfCode, numberOfParametersInExtractedMethod, changes,
				undoChanges, 0, 0, 0, 0, 0, runtime);
	}

	public CodeExtractionMetrics(boolean feasible, String reason, boolean applied, int numberOfExtractedLinesOfCode,
			int numberOfParametersInExtractedMethod, List<Change> changes, List<Change> undoChanges,
			int reductionOfCognitiveComplexity, int accumulatedInherentComponent, int accumulatedNestingComponent,
			int numberNestingContributors, int nesting, long runtime) {
		this.feasible = feasible;
		this.reason = reason;
		this.applied = applied;
		this.numberOfExtractedLinesOfCode = numberOfExtractedLinesOfCode;
		this.numberOfParametersInExtractedMethod = numberOfParametersInExtractedMethod;
		this.changes = changes;
		this.undoChanges = undoChanges;
		this.reductionOfCognitiveComplexity = reductionOfCognitiveComplexity;
		this.accumulatedInherentComponent = accumulatedInherentComponent;
		this.accumulatedNestingComponent = accumulatedNestingComponent;
		this.numberNestingContributors = numberNestingContributors;
		this.nesting = nesting;
		this.runtime = runtime;
	}

	/**
	 * Copy constructor. * @param metrics the metrics object to copy.
	 */
	public CodeExtractionMetrics(CodeExtractionMetrics metrics) {
		this.feasible = metrics.feasible;
		this.reason = metrics.reason;
		this.applied = metrics.applied;
		this.numberOfExtractedLinesOfCode = metrics.numberOfExtractedLinesOfCode;
		this.numberOfParametersInExtractedMethod = metrics.numberOfParametersInExtractedMethod;
		this.changes = new ArrayList<>(metrics.getChanges());
		this.undoChanges = new ArrayList<>(metrics.getUndoChanges());
		this.reductionOfCognitiveComplexity = metrics.reductionOfCognitiveComplexity;
		this.accumulatedInherentComponent = metrics.accumulatedInherentComponent;
		this.accumulatedNestingComponent = metrics.accumulatedNestingComponent;
		this.numberNestingContributors = metrics.numberNestingContributors;
		this.nesting = metrics.nesting;
		this.runtime = metrics.runtime;
	}

	/**
	 * Aggregates another metrics object into this one.
	 * <p>
	 * Used when combining multiple extractions into a single result. Note that
	 * specific complexity components (inherent, nesting) are reset to 0 as they
	 * cannot be simply summed for a sequence.
	 * </p>
	 * * @param metrics the metrics to add to the current state.
	 */
	public void joinMetrics(CodeExtractionMetrics metrics) {
		this.feasible = (this.feasible && metrics.isFeasible());

		if (this.reason == null)
			this.reason = "";
		this.reason += "\n " + metrics.getReason();

		this.applied = (this.applied && metrics.isApplied());
		this.numberOfExtractedLinesOfCode += metrics.getNumberOfExtractedLinesOfCode();
		this.numberOfParametersInExtractedMethod += metrics.getNumberOfParametersInExtractedMethod();

		this.changes.addAll(metrics.getChanges());
		this.undoChanges.addAll(metrics.getUndoChanges());

		this.reductionOfCognitiveComplexity += metrics.reductionOfCognitiveComplexity;

		// Reset detailed components as they are no longer valid for the aggregate
		this.accumulatedInherentComponent = 0;
		this.accumulatedNestingComponent = 0;
		this.numberNestingContributors = 0;
		this.nesting = 0;

		this.runtime += metrics.runtime;
	}

	// =========================================================================
	// GETTERS & SETTERS
	// =========================================================================

	public boolean isFeasible() {
		return feasible;
	}

	public void setFeasible(boolean feasible) {
		this.feasible = feasible;
	}

	public String getReason() {
		return reason;
	}

	public void setReason(String reason) {
		this.reason = reason;
	}

	public boolean isApplied() {
		return applied;
	}

	public void setApplied(boolean applied) {
		this.applied = applied;
	}

	public int getNumberOfExtractedLinesOfCode() {
		return numberOfExtractedLinesOfCode;
	}

	public void setNumberOfExtractedLinesOfCode(int numberOfExtractedLinesOfCode) {
		this.numberOfExtractedLinesOfCode = numberOfExtractedLinesOfCode;
	}

	public int getReductionOfCognitiveComplexity() {
		return reductionOfCognitiveComplexity;
	}

	public void setReductionOfCognitiveComplexity(int reductionOfCognitiveComplexity) {
		this.reductionOfCognitiveComplexity = reductionOfCognitiveComplexity;
	}

	public int getCognitiveComplexityOfNewExtractedMethod() {
		return accumulatedInherentComponent + accumulatedNestingComponent;
	}

	public int getNumberOfParametersInExtractedMethod() {
		return numberOfParametersInExtractedMethod;
	}

	public void setNumberOfParametersInExtractedMethod(int numberOfParametersInExtractedMethod) {
		this.numberOfParametersInExtractedMethod = numberOfParametersInExtractedMethod;
	}

	public int getAccumulatedInherentComponent() {
		return accumulatedInherentComponent;
	}

	public void setAccumulatedInherentComponent(int accumulatedInherentComponent) {
		this.accumulatedInherentComponent = accumulatedInherentComponent;
	}

	public int getAccumulatedNestingComponent() {
		return accumulatedNestingComponent;
	}

	public void setAccumulatedNestingComponent(int accumulatedNestingComponent) {
		this.accumulatedNestingComponent = accumulatedNestingComponent;
	}

	public int getNumberNestingContributors() {
		return numberNestingContributors;
	}

	public void setNumberNestingContributors(int numberNestingContributors) {
		this.numberNestingContributors = numberNestingContributors;
	}

	public List<Change> getChanges() {
		return changes;
	}

	public void setChanges(List<Change> changes) {
		this.changes = changes;
	}

	public List<Change> getUndoChanges() {
		return undoChanges;
	}

	public void setUndoChanges(List<Change> undoChanges) {
		this.undoChanges = undoChanges;
	}

	public long getRuntime() {
		return runtime;
	}

	public void setRuntime(long runtime) {
		this.runtime = runtime;
	}

	public int getNesting() {
		return nesting;
	}

	public void setNesting(int nesting) {
		this.nesting = nesting;
	}

	@Override
	public String toString() {
		return "Metrics [feasible=" + feasible + ", applied=" + applied + ", reason=" + reason + ", lines="
				+ numberOfExtractedLinesOfCode + ", params=" + numberOfParametersInExtractedMethod + ", complexityRed="
				+ reductionOfCognitiveComplexity + ", runtime=" + runtime + "ms]";
	}
}