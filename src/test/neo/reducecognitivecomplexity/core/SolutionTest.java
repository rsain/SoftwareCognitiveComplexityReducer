package test.neo.reducecognitivecomplexity.core;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;

import org.eclipse.jdt.core.dom.CompilationUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import neo.reducecognitivecomplexity.core.Solution;
import neo.reducecognitivecomplexity.core.jdt.Utils;
import neo.reducecognitivecomplexity.core.refactoringcache.RefactoringCache;

/**
 * Unit tests for {@link Solution}.
 * <p>
 * Verifies initialization, feasibility evaluation, and extraction count calculations 
 * for refactoring solutions using sample compilation units and refactoring caches.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SolutionTest {
	static ArrayList<Solution> s = new ArrayList<>();
	static ArrayList<Double> extractions = new ArrayList<>();
	
	@BeforeAll
	static void setUp() throws Exception {
		Path resourceDirectory = Paths.get("src","test","resources");
		String absolutePath = resourceDirectory.toFile().getAbsolutePath() + File.separatorChar;
		
		ArrayList<RefactoringCache> rc = new ArrayList<>();
		ArrayList<String> javaFileName = new ArrayList<>();
		ArrayList<String> refactoringCacheFileName = new ArrayList<>();
		ArrayList<CompilationUnit> cu = new ArrayList<>();
		ArrayList<Integer> cognitiveComplexity = new ArrayList<>();
		ArrayList<String> solutionStringFormat = new ArrayList<>();
		
		//First test case
		javaFileName.add("EZInjection.java");
		refactoringCacheFileName.add("bytecode-viewer@src.main.java-the.bytecode.club.bytecodeviewer.plugin.preinstalled-EZInjection.java-execute-140.csv");
		cognitiveComplexity.add(131);
		solutionStringFormat.add("[[5789, 5863, 6082, 6169, 6345, 11006, 11252, 11288], [6529], [7582], [7657, 7704], [12133]]");
		extractions.add(5.0);
		
		//Second test case
		javaFileName.add("ResourceDecompiling.java");
		refactoringCacheFileName.add("bytecode-viewer@src.main.java-the.bytecode.club.bytecodeviewer.resources-ResourceDecompiling.java-decompileSaveAll-47.csv");
		cognitiveComplexity.add(79);
		solutionStringFormat.add("[[3637 3738 3786 3886 3940 4042 5614 5948 6278 6620 6960], [4042 5614 5948 6278], [5614 5948 6278]]");
		extractions.add(3.0);
		
		//Third test case
		javaFileName.add("ResourceDecompiling.java");
		refactoringCacheFileName.add("bytecode-viewer@src.main.java-the.bytecode.club.bytecodeviewer.resources-ResourceDecompiling.java-decompileSaveOpenedOnly-199.csv");
		cognitiveComplexity.add(140);
		solutionStringFormat.add("[[8466 8487 8588 10272], [8466 8487 8588 10272 11054], [8487 8588], [8662 8712], [8662 8712 8998 9266 9526 9800], [11893], [12628]]");
		extractions.add(7.0);
		
		//Fourth test case
		javaFileName.add("Ebes.java");
		refactoringCacheFileName.add("jmetal-problem@src.main.java-org.uma.jmetal.problem.multiobjective.ebes-Ebes.java-Variable_Position-5360.csv");
		cognitiveComplexity.add(16);
		solutionStringFormat.add("[[186548], [186867], [187824], [188776], [189093], [189410]]");
		extractions.add(6.0);
		
		//Fifth test case
		javaFileName.add("Ebes.java");
		refactoringCacheFileName.add("jmetal-problem@src.main.java-org.uma.jmetal.problem.multiobjective.ebes-Ebes.java-Variable_Position-5360.csv");
		cognitiveComplexity.add(16);
		solutionStringFormat.add("[[186227], [186867], [187824], [188776], [189093]]");
		extractions.add(5.0);
		
		//Sixth test case
		javaFileName.add("LZ09.java");
		refactoringCacheFileName.add("jmetal-problem@src.main.java-org.uma.jmetal.problem.multiobjective.lz09-LZ09.java-objective-223.csv");
		cognitiveComplexity.add(42);
		solutionStringFormat.add("[[5810 6638], [5810]]");
		extractions.add(2.0);
		
		//Seventh test case
		javaFileName.add("ASMResourceUtil.java");
		refactoringCacheFileName.add("bytecode-viewer@src.main.java-the.bytecode.club.bytecodeviewer.api-ASMResourceUtil.java-renameClassNode-146.csv");
		cognitiveComplexity.add(69);
		solutionStringFormat.add("[[6327, 6763, 6870, 6967], [6327, 6763, 6870, 6967, 7160], [7846, 8119, 8451]]");
		extractions.add(3.0);
		
		for (int i = 0; i < extractions.size(); i++)
		{
			cu.add(Utils.createCompilationUnitFromFile(absolutePath + javaFileName.get(i)));
			rc.add(new RefactoringCache(absolutePath, refactoringCacheFileName.get(i), cu.get(i)));
			s.add(new Solution(cu.get(i), solutionStringFormat.get(i), cognitiveComplexity.get(i)));
			s.get(i).evaluate(rc.get(i));
		}
	}

	@Test
	@Order(1)
	@DisplayName ("Checking Solution initialization")
	void initialization() {
		for (int i = 0; i < s.size(); i++)
		{
			assertNotNull(s.get(i), "The solution is NULL! (check resources files path)");
		}
	}

	@Test
	@DisplayName ("Checking Solution feasibility")
	@Order(2)
	void checkFeasibility() {
		for (int i = 0; i < s.size(); i++)
		{
			assertTrue(s.get(i).isFeasible(), "The solution is not feasible!");
		}
	}
	
	@Test
	@DisplayName ("Checking extractions value")
	@Order(3)
	void checkExtractions() {
		for (int i = 0; i < s.size(); i++)
		{
			System.out.println("Processing solution " + (i+1) + " over " + s.size());
			assertEquals(extractions.get(i), s.get(i).getSize(), "Extractions is not properly computed for " + s.toString() + "!");
			System.out.println("Done solution " + (i+1) + " over " + s.size());
			
		}
	}
}
