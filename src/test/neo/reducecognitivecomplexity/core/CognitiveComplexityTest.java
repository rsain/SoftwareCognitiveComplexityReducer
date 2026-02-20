package test.neo.reducecognitivecomplexity.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import neo.reducecognitivecomplexity.core.jdt.JavaMethodProcessor;
import neo.reducecognitivecomplexity.core.jdt.JavaMethodProcessor.MethodComplexityRecord;

/**
 * Test class to validate Cognitive Complexity calculations against a gold-standard Java file.
 * It parses a test file and compares calculated metrics with values defined in source comments.
 */
public class CognitiveComplexityTest {

    // Stores the processed results: Key is the AST CompilationUnit, Value is the list of method records
    private static Map<CompilationUnit, List<MethodComplexityRecord>> results;
    
    // Crucial: Holds the raw text of the file to preserve comments that JDT AST might otherwise ignore
    private static String globalSourceCode; 
   
    @BeforeAll
    public static void setup() throws IOException {
        // Resolve the path to the test resource file
        Path resourceDirectory = Paths.get("src","test","resources");
        String absolutePath = resourceDirectory.toFile().getAbsolutePath() + File.separatorChar;
        String javaFileName = "CognitiveComplexityCheck.java";
        
        // Read raw file content into a String to ensure we have access to original formatting and comments
        globalSourceCode = new String(Files.readAllBytes(Paths.get(absolutePath + javaFileName)));
        
        // Initialize JDT AST Parser for the latest Java version supported
        ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
        parser.setSource(globalSourceCode.toCharArray());
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        
        // Attempt to recover statements even if there are syntax errors in the test file
        parser.setStatementsRecovery(true);
        
        // Generate the Abstract Syntax Tree (AST)
        CompilationUnit cu = (CompilationUnit) parser.createAST(null);
        
        // Process the AST using our custom logic to calculate cognitive complexity for every method
        JavaMethodProcessor processor = new JavaMethodProcessor();
        results = new HashMap<>();
        processor.processCompilationUnit(cu, new java.util.ArrayList<>(), results);
    }
    
    /**
     * Generates individual tests for each method found in the source code.
     * This provides a granular view in the JUnit runner UI.
     */
    @TestFactory
    public Stream<DynamicTest> verifyAllMethodsDynamic() {
        // Retrieve the first (and only) processed unit from our results map
        List<MethodComplexityRecord> records = results.values().iterator().next();

        return records.stream()
            .map(record -> {
                // Determine the expected value from the method's comments
                int expected = extractExpectedComplexity(record);
                
                // If no "Noncompliant" comment is found, create a "Skip" placeholder test
                if (expected == -1) {
                    return DynamicTest.dynamicTest("Skip: " + record.methodName, 
                        () -> System.out.println("No complexity comment for " + record.methodName));
                }

                // Create a dynamic test case with a descriptive name showing Expected vs Actual
                String displayName = String.format("Method: %s | Expected: %d | Actual: %d", 
                                     record.methodName, expected, record.complexity);

                return DynamicTest.dynamicTest(displayName, () -> {
                    assertEquals(expected, record.complexity, 
                        "Complexity calculation error in " + record.methodName);
                });
            });
    }

    /**
     * Standard JUnit test that loops through all methods. 
     * Useful for a quick pass/fail result in the console.
     */
    @Test
    public void verifyAllMethodsComplexity() {
        List<MethodComplexityRecord> records = results.values().iterator().next();

        for (MethodComplexityRecord record : records) {
            int expected = extractExpectedComplexity(record);
            
            // Ignore methods that don't specify a complexity expectation
            if (expected == -1) continue;

            assertEquals(expected, record.complexity, 
                String.format("Method '%s' at line %d failed!", record.methodName, record.lineNumber));
        }
    }

    /**
     * Extracts the expected complexity from the method's raw source code.
     * * Logic:
     * 1. JDT's record.methodDeclaration.toString() often strips comments.
     * 2. We use getStartPosition() and getLength() to slice the RAW file content.
     * 3. We use Regex to find the number between "from" and "to" in the Sonar-style comment.
     */
    private int extractExpectedComplexity(MethodComplexityRecord record) {
        // Get character offsets of the method declaration within the original file
        int start = record.methodDeclaration.getStartPosition();
        int length = record.methodDeclaration.getLength();
        
        // Extract the literal text from the file (guaranteed to include comments)
        String rawSource = globalSourceCode.substring(start, start + length);
        
        // Regex: Matches "Complexity from [digits] to"
        // \\s+ handles any number of spaces or tabs
        Pattern pattern = Pattern.compile("Complexity from\\s+(\\d+)\\s+to");
        Matcher matcher = pattern.matcher(rawSource);
        
        if (matcher.find()) {
            // Group 1 captures the digits found immediately after "from"
            return Integer.parseInt(matcher.group(1));
        }
        
        return -1; // Indicator that no expected complexity comment was found
    }
}