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

import neo.reducecognitivecomplexity.core.ExtractionTextRange;
import neo.reducecognitivecomplexity.core.Sequence;
import neo.reducecognitivecomplexity.core.jdt.Utils;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SequenceTest {
	static ArrayList<Sequence> sequence = new ArrayList<>();
	static ArrayList<String> expectedCode = new ArrayList<>();
	
	@BeforeAll
	static void setUp() throws Exception {
		Path resourceDirectory = Paths.get("src","test","resources");
		String absolutePath = resourceDirectory.toFile().getAbsolutePath() + File.separatorChar;
		
		ArrayList<String> javaFileName = new ArrayList<>();
		ArrayList<CompilationUnit> cu = new ArrayList<>();
		ArrayList<ExtractionTextRange> pair = new ArrayList<>();
		
		//First test case (comments remove from source code, because they are not in the AST)
		javaFileName.add("EZInjection.java");
		pair.add(new ExtractionTextRange(4948,5392));
		expectedCode.add(
				  "boolean print = all;\n"
				+ "if (!all && debugClasses.length >= 1)\n"
				+ "{\n"
				+ "    for (String s : debugClasses)\n"
				+ "    {\n"
				+ "        if (info.split(\"\\\\.\")[0].equals(s.replaceAll(\"\\\\.\", \"/\")))\n"
				+ "        {\n"
				+ "            print = true;\n"
				+ "            break;\n"
				+ "        }\n"
				+ "    }\n"
				+ "}\n"
				+ "if (print)\n"
				+ "    print(\"Method call: \" + info);");
		
		//Second test case (comments remove from source code, because they are not in the AST)
		javaFileName.add("EZInjection.java");
		pair.add(new ExtractionTextRange(11006,11302));
		expectedCode.add(
				  "if (useProxy)\n"
				  + "{\n"
				  + "    try\n"
				  + "    {\n"
				  + "        String[] split = proxy.split(\":\");\n"
				  + "        setProxy(split[0], split[1]);\n"
				  + "    } catch (Exception e) {\n"
				  + "      \n"
				  + "    }\n"
				  + "}\n"
				  + "print(\"Done setting up.\");\n"
				  + "setFinished();");
		
		//Third test case (comments remove from source code, because they are not in the AST)
		javaFileName.add("FileDrop.java");
		pair.add(new ExtractionTextRange(22739,23373));
		expectedCode.add("c.addHierarchyListener(evt -> {\n"
				+ "    log(out, \"FileDrop: Hierarchy changed.\");\n"
				+ "    final Component parent = c.getParent();\n"
				+ "    if (parent == null) {\n"
				+ "        c.setDropTarget(null);\n"
				+ "        log(out, \"FileDrop: Drop target cleared from component.\");\n"
				+ "    } \n"
				+ "    else {\n"
				+ "        new DropTarget(c, dropListener);\n"
				+ "        log(out, \"FileDrop: Drop target added to component.\");\n"
				+ "    } \n"
				+ "}); \n"
				+ "if (c.getParent() != null) {\n"
				+ "    new DropTarget(c, dropListener);\n"
				+ "}");
				
		for (int i = 0; i < expectedCode.size(); i++)
		{
			cu.add(Utils.createCompilationUnitFromFile(absolutePath + javaFileName.get(i)));
			sequence.add(new Sequence(cu.get(i), pair.get(i)));
		}
	}

	@Test
	@Order(1)
	@DisplayName ("Checking Sequence initialization")
	void initialization() {
		//Comparing code after removing spaces, \n and \t
		for (int i = 0; i < sequence.size(); i++)
		{
			String a = sequence.get(i).toSourceCodeString();
			a = a.substring(1, a.length()-1); //removing [ and ] from the original string
			a = a.replace(",", ""); // removing the char used to split lines of code
			a = a.replace(" ", "");
			a = a.replace("\n", "");
			a = a.replace("\t", "");
			
			String b = expectedCode.get(i);
			b = b.replace(" ", "");
			b = b.replace(",", "");
			b = b.replace("\n", "");
			b = b.replace("\t", "");
			
			assertEquals(a, b);
		}
	}
}
