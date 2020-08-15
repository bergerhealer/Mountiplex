package com.bergerkiller.mountiplex;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

import org.junit.Test;

import com.bergerkiller.mountiplex.reflection.declarations.ClassDeclaration;
import com.bergerkiller.mountiplex.reflection.declarations.ClassResolver;
import com.bergerkiller.mountiplex.reflection.declarations.Declaration;
import com.bergerkiller.mountiplex.reflection.declarations.FieldDeclaration;
import com.bergerkiller.mountiplex.reflection.declarations.SourceDeclaration;
import com.bergerkiller.mountiplex.types.SimilarityTestType;

public class SimilarityTest {
	private static ClassDeclaration simDec = new ClassDeclaration(ClassResolver.DEFAULT, SimilarityTestType.class);

	@Test
	public void testFieldSimilarity() {
		assertFieldSorted("public Double intNumber", "public Integer intNumber");
		assertFieldSorted("public String intNumber", "public String name");
		assertFieldSorted("private int m", "public int x");
		assertFieldSorted("public Runnable r", "public Runnable runnable");
	}

	//@Test
	public void testTemplateAlternatives() {
	    // Shows template alternatives. Purely for debug while making changes to it.
	    String declaration = "package com.bergerkiller.mountiplex.types;\n" +
	                         "class SimilarityTestType {\n" +
	                         "    public int ak;\n" +
	                         "}";

	    SourceDeclaration.parse(declaration);
	}
	
	public static void assertFieldSorted(String declaration, String expected) {
		List<FieldDeclaration> fields = new ArrayList<FieldDeclaration>();
		fields.addAll(Arrays.asList(simDec.fields));
		FieldDeclaration fdec = new FieldDeclaration(simDec.getResolver(), declaration);
		Declaration.sortSimilarity(fdec, fields);
		FieldDeclaration exp = new FieldDeclaration(simDec.getResolver(), expected);
		if (!fields.get(0).match(exp)) {
			for (FieldDeclaration f : fields) {
				System.out.println("- " + f.toString());
			}
			fail("Field was not sorted to the top: " + expected);
		}
	}

	public static void logSortedFields(String declaration) {
		List<FieldDeclaration> fields = new ArrayList<FieldDeclaration>();
		fields.addAll(Arrays.asList(simDec.fields));
		FieldDeclaration fdec = new FieldDeclaration(simDec.getResolver(), declaration);
		Declaration.sortSimilarity(fdec, fields);
		for (FieldDeclaration f : fields) {
			System.out.println("- " + f.toString());
		}
	}
}
