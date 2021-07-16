package com.bergerkiller.mountiplex;

import static org.junit.Assert.*;

import org.junit.Test;

import com.bergerkiller.mountiplex.reflection.declarations.SourceDeclaration;

public class SourceDeclarationTest {

    @Test
    public void testPreprocessConditionals() {
        String sourceDec = "#set test 1.23.55\n" +
                           "#set dummy 12\n" +
                           "#if classexists this.class.does.not.exist\n" +
                             "THIS SHOULD NOT EVALUATE BECAUSE CLASS DOES NOT EXIST\n" +
                           "#elseif classexists com.bergerkiller.mountiplex.types.TestObject\n" +
                             "THIS SHOULD EVALUATE BECAUSE THE CLASS EXISTS\n" +
                           "#else\n" +
                             "THIS SHOULD NOT EVALUATE, PREVIOUS CLASS EXISTED\n" +
                           "#endif\n" +
                           "#if exists java.lang.String public String toString(); && !exists java.lang.MissingType;\n" +
                             "THIS SHOULD EVALUATE BECAUSE BOTH EXPRESSIONS ARE TRUE\n" +
                           "#endif\n" +
                           "#if test >= 1.22.0\n" +
                             "THIS SHOULD EVALUATE\n" +
                             "#if dummy == 11\n" +
                               "DUMMY==11 SHOULD NOT EVALUATE\n" +
                             "#elseif dummy == 12\n" +
                               "DUMMY==12 SHOULD EVALUATE\n" +
                             "#endif\n" +
                           "#else\n" +
                             "THIS SHOULD NOT EVALUATE\n" +
                             "#if dummy == 12\n" +
                               "THIS SHOULD ALSO NOT EVALUATE\n" +
                             "#elseif dummy >= 10\n" +
                               "NEITHER SHOULD THIS!\n" +
                             "#endif\n" +
                           "#endif\n" +
                           "AFTER ENDIF SHOULD EVALUATE\n" +
                           "#if dummy >= 5\n" +
                           "THIS SHOULD MATCH BECAUSE FIRST IF\n" +
                           "#elseif dummy >= 4\n" +
                           "THIS ONE SHOULD NOT\n" +
                           "#else\n" +
                           "DEFINITELY NOT\n" +
                           "#endif\n" +
                           "#if methodexists java.lang.String public static String copyValueOf(char[] arg)\n" +
                           "COPYVALUEOF SHOULD EXIST\n" +
                           "#else\n" +
                           "METHODEXISTS SHOULD NOT FAIL\n" +
                           "#endif\n" +
                           "#if exists java.util.ArrayList public java.util.ArrayList(int capacity);\n" +
                           "PLEASE BE THERE\n" +
                           "#else\n" +
                           "THIS SHOULD NOT BE THERE BRO\n" +
                           "#endif\n" +
                           "/*\n" +
                           "#if dummy >= 5\n" +
                           "THIS SHOULD BE IGNORED, BLOCK COMMENT\n" +
                           "#endif\n" +
                           "*/\n" +
                           "#set casevarA 1.15\n" +
                           "#set casevarB 1.12\n" +
                           "#select casevarA\n" +
                           "#case >= 1.16: CASE_VAR_A_WRONG1\n" +
                           "#case >= 1.15: CASE_VAR_A_CORRECT\n" +
                           "    #select casevarB >=\n" +
                           "    #case 1.13: CASE_VAR_B_WRONG1\n" +
                           "    #case 1.12: CASE_VAR_B_CORRECT\n" +
                           "    #case else: CASE_VAR_B_WRONG2\n" +
                           "    #endselect\n" +
                           "#case >= 1.14: CASE_VAR_A_WRONG2\n" +
                           "#case else   : CASE_VAR_A_WRONG3\n" +
                           "#endselect";

        String expected = "#set test 1.23.55\n" +
                          "#set dummy 12\n" +
                          "THIS SHOULD EVALUATE BECAUSE THE CLASS EXISTS\n" +
                          "THIS SHOULD EVALUATE BECAUSE BOTH EXPRESSIONS ARE TRUE\n" +
                          "THIS SHOULD EVALUATE\n" +
                          "DUMMY==12 SHOULD EVALUATE\n" +
                          "AFTER ENDIF SHOULD EVALUATE\n" +
                          "THIS SHOULD MATCH BECAUSE FIRST IF\n" +
                          "COPYVALUEOF SHOULD EXIST\n" +
                          "PLEASE BE THERE\n" +
                          "\n" +
                          "#set casevarA 1.15\n" + 
                          "#set casevarB 1.12\n" + 
                          "               CASE_VAR_A_CORRECT\n" + 
                          "                CASE_VAR_B_CORRECT\n";

        String result = SourceDeclaration.preprocess(sourceDec);
        if (!result.equals(expected)) {
            System.out.println("== EXPECTED ==");
            System.out.println(expected);
            System.out.println("== BUT GOT ==");
            System.out.println(result);
            fail("Source declaration was not correctly parsed");
        }
    }

}
