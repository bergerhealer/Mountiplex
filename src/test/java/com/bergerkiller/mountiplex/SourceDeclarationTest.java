package com.bergerkiller.mountiplex;

import static org.junit.Assert.*;

import org.junit.Test;

import com.bergerkiller.mountiplex.reflection.declarations.SourceDeclaration;

public class SourceDeclarationTest {

    @Test
    public void testPreprocess() {
        String sourceDec = "#set test 1.23.55\n" +
                           "#set dummy 12\n" +
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
                           "/*\n" +
                           "#if dummy >= 5\n" +
                           "THIS SHOULD BE IGNORED, BLOCK COMMENT\n" +
                           "#endif\n" +
                           "*/";

        String expected = "#set test 1.23.55\n" +
                          "#set dummy 12\n" +
                          "THIS SHOULD EVALUATE\n" +
                          "DUMMY==12 SHOULD EVALUATE\n" +
                          "AFTER ENDIF SHOULD EVALUATE\n";

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
