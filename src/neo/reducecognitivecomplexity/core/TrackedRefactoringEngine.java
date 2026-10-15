package neo.reducecognitivecomplexity.core;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import java.util.logging.Logger;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.CompositeChange;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.TextFileChange;

import neo.reducecognitivecomplexity.core.jdt.Utils;
import neo.reducecognitivecomplexity.core.jdt.Utils.MethodDeclarationFinderVisitor;

/**
 * Service orchestrator responsible for running live Extract Method refactorings
 * on an input Optimization Solution plan.
 */
public class TrackedRefactoringEngine {
	private static final Logger LOGGER = Logger.getLogger(TrackedRefactoringEngine.class.getName());

	@FunctionalInterface
	private interface PostExtractionAction {
		void execute(int extractionIndex, CompilationUnit cu) throws IOException, CoreException;
	}

	public boolean applyExtractMethodsRefactoring(Solution solution, boolean printDebuggingInformation)
			throws CoreException {
		try {
			return applyExtractionsInternal(solution, printDebuggingInformation, (index, cu) -> {
			});
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
	}

	public boolean applyExtractMethodsRefactoringWritingExtractionsInFiles(Solution solution,
			boolean printDebuggingInformation, String folder) throws IOException, CoreException {
		CompilationUnit originalCu = solution.getCompilationUnit();
		String outputFile = originalCu.getJavaElement().getPath().toString();
		outputFile = outputFile.substring(1, outputFile.length() - (".java").length());
		outputFile = outputFile.replace(File.separatorChar, '.');
		outputFile += "." + solution.getMethodName() + ".Original.java";
		outputFile = folder + File.separatorChar + outputFile;

		try (FileWriter fw = new FileWriter(outputFile)) {
			fw.write(solution.getMethodDeclaration().toString());
		}

		PostExtractionAction writeAction = (index, cu) -> {
			String stepFile = cu.getJavaElement().getPath().toString();
			stepFile = stepFile.substring(1, stepFile.length() - (".java").length());
			stepFile = stepFile.replace(File.separatorChar, '.');

			int stepNumber = solution.getRanges().size() - index;
			stepFile += "." + solution.getMethodName() + ".Extraction" + stepNumber + ".java";
			stepFile = folder + File.separatorChar + stepFile;

			try (FileWriter fwStep = new FileWriter(stepFile)) {
				MethodDeclarationFinderVisitor methodFinderVisitor = new MethodDeclarationFinderVisitor(cu.getRoot(),
						solution.getMethodName(), solution.getMethodDeclaration().parameters());

				if (methodFinderVisitor.getMethodDeclaration() != null) {
					fwStep.write(methodFinderVisitor.getMethodDeclaration().toString());
				} else {
					LOGGER.severe(
							"Error: Could not find method " + solution.getMethodName() + " in the extracted source.");
				}
			}
		};

		return applyExtractionsInternal(solution, printDebuggingInformation, writeAction);
	}

	private boolean applyExtractionsInternal(Solution solution, boolean printDebuggingInformation,
			PostExtractionAction postAction) throws CoreException, IOException {
		boolean extractionsApplied = true;
		List<ExtractionTextRange> rangesForExtractions = solution.getRanges();
		Stack<Change> undoStack = new Stack<>();

		if (printDebuggingInformation) {
			LOGGER.info("Extracting code " + (solution.toStringForFileFormat()) + " ...");
		}

		ICompilationUnit icu = (ICompilationUnit) solution.getCompilationUnit().getJavaElement();
		org.eclipse.jface.text.IDocument document = new org.eclipse.jface.text.Document(icu.getSource());

		List<org.eclipse.jface.text.Position> managedPositions = new ArrayList<>();
		for (ExtractionTextRange range : rangesForExtractions) {
			org.eclipse.jface.text.Position pos = new org.eclipse.jface.text.Position(range.getStart(),
					range.getEnd() - range.getStart());
			try {
				document.addPosition(pos);
			} catch (BadLocationException e) {
				LOGGER.log(java.util.logging.Level.SEVERE, "Initial position placement failed out of bounds", e);
			}
			managedPositions.add(pos);
		}

		int indexOfExtraction = rangesForExtractions.size() - 1;

		while (indexOfExtraction >= 0 && extractionsApplied) {
			org.eclipse.jface.text.Position currentPos = managedPositions.get(indexOfExtraction);

			if (currentPos.isDeleted()) {
				LOGGER.warning("Extraction " + (indexOfExtraction + 1)
						+ " was swallowed or deleted by a prior operation. Skipping.");
				indexOfExtraction--;
				continue;
			}

			int currentStart = currentPos.getOffset();
			int currentLength = currentPos.getLength();

			String currentMethodName = solution.getMethodName() + "_extraction_" + (indexOfExtraction + 1);
			currentMethodName = currentMethodName.replaceFirst("^.", currentMethodName.substring(0, 1).toLowerCase());

			if (printDebuggingInformation) {
				LOGGER.info("Applying extraction " + (indexOfExtraction + 1) + " (there are "
						+ rangesForExtractions.size() + ") ...");
				LOGGER.info("Live coordinates: Start=" + currentStart + ", Length=" + currentLength);
				LOGGER.info("New method name = " + currentMethodName);
			}

			org.eclipse.jdt.internal.corext.refactoring.code.ExtractMethodRefactoring refactoring = new org.eclipse.jdt.internal.corext.refactoring.code.ExtractMethodRefactoring(
					solution.getCompilationUnit(), currentStart, currentLength);
			refactoring.setMethodName(currentMethodName);
			refactoring.setReplaceDuplicates(false);

			RefactoringStatus status = refactoring.checkAllConditions(new NullProgressMonitor());

			if (!status.isOK()) {
				LOGGER.severe(
						"ERROR checking conditions for sequence " + solution.getSequenceList().get(indexOfExtraction));
				extractionsApplied = false;
			} else {
				Change change = refactoring.createChange(new NullProgressMonitor());
				Change undoChange = change.perform(new NullProgressMonitor());
				undoStack.push(undoChange);

				try {
					if (change instanceof TextFileChange) {
						((TextFileChange) change).getEdit().apply(document);
					} else if (change instanceof CompositeChange) {
						for (Change childChange : ((CompositeChange) change).getChildren()) {
							if (childChange instanceof TextFileChange) {
								((TextFileChange) childChange).getEdit().apply(document);
							}
						}
					}
				} catch (Exception e) {
					LOGGER.log(java.util.logging.Level.SEVERE,
							"Failed syncing tracking positions across document updates", e);
					extractionsApplied = false;
					break;
				}

				if (printDebuggingInformation) {
					LOGGER.info("Extraction applied successfully! Reloading CompilationUnit.");
				}

				CompilationUnit freshCu = Utils.createCompilationUnitFromFileInWorkspace(
						solution.getCompilationUnit().getJavaElement().getPath().toOSString());

				MethodDeclaration parentMethod = Utils.getMethodDeclaration(freshCu);
				if (parentMethod != null) {
					solution.updateCompilationUnitState(freshCu, parentMethod);
				}

				if (postAction != null) {
					postAction.execute(indexOfExtraction, solution.getCompilationUnit());
				}
			}

			indexOfExtraction--;
		}

		if (!extractionsApplied) {
			while (!undoStack.isEmpty()) {
				undoStack.pop().perform(new NullProgressMonitor());
			}
		}

		return extractionsApplied;
	}
}