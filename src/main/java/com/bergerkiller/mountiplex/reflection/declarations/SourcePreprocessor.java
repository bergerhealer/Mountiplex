package com.bergerkiller.mountiplex.reflection.declarations;

import java.util.LinkedList;
import java.util.Locale;

/**
 * Pre-processes template source code to resolve if,
 * else,elseif,select case and variable macros.
 */
public class SourcePreprocessor {
    private final ClassResolver resolver;
    private final StringBuilder result = new StringBuilder();
    private int disabledIfLevel;
    private boolean disabledIfExpression;
    private final LinkedList<String> selectStack = new LinkedList<String>();
    private boolean isFirstSelectCase;
    private boolean inBlockComment;
    private boolean inCodeBlock;
    private boolean inCodeBlockComment;

    public SourcePreprocessor(ClassResolver resolver) {
        this.resolver = resolver;
        reset();
    }

    public String preprocess(String declaration) {
        reset();

        for (String line : declaration.split("\\r?\\n")) {
            preprocessLine(line);
        }

        return result.toString();
    }

    private void reset() {
        result.setLength(0);
        disabledIfLevel = 0;
        disabledIfExpression = false;
        selectStack.clear();
        isFirstSelectCase = false;
        inBlockComment = false;
        inCodeBlock = false;
        inCodeBlockComment = false;
    }

    public void preprocessLine(String line) {
        String trimmedLine = line.trim();

        if (inCodeBlockComment) {
            if ("*/".equals(trimmedLine)) {
                inBlockComment = false;
            } else if ("</code>".equals(trimmedLine)) {
                inCodeBlockComment = false;
            }
            return;
        }

        if (inBlockComment) {
            if ("<code>".equals(trimmedLine)) {
                inCodeBlockComment = true;
            } else if ("*/".equals(trimmedLine)) {
                inBlockComment = false;
            }
            return;
        }

        if (inCodeBlock) {
            if ("</code>".equals(trimmedLine)) {
                inCodeBlock = false;
                result.append(line).append('\n');
                return;
            }
            result.append(line).append('\n');
            return;
        }

        if ("<code>".equals(trimmedLine)) {
            result.append(line).append('\n');
            inCodeBlock = true;
            return;
        }

        StringBuilder filteredLine = new StringBuilder(line.length());
        boolean lineStartsBlockComment = false;
        boolean lineHadTextOutsideComment = false;
        boolean startedInsideComment = inBlockComment;
        int index = 0;

        while (index < line.length()) {
            if (inBlockComment) {
                int endIndex = line.indexOf("*/", index);
                if (endIndex == -1) {
                    break;
                }
                index = endIndex + 2;
                inBlockComment = false;
            } else {
                int startIndex = line.indexOf("/*", index);
                if (startIndex == -1) {
                    filteredLine.append(line.substring(index));
                    lineHadTextOutsideComment = true;
                    break;
                }

                if (startIndex > index) {
                    filteredLine.append(line, index, startIndex);
                    lineHadTextOutsideComment = true;
                }

                lineStartsBlockComment = true;
                index = startIndex + 2;
                inBlockComment = true;
            }
        }

        line = filteredLine.toString();
        if (line.isEmpty() && lineStartsBlockComment && !startedInsideComment) {
            line = "";
        } else if (line.isEmpty() && !lineHadTextOutsideComment) {
            return;
        }

        String lineTrimmed = line.trim();
        String lineLower = lineTrimmed.toLowerCase(Locale.ENGLISH);

        // Translate select case statements into if-elseif-else-endif blocks
        int selectCaseIndent = -1;
        if (lineLower.startsWith("#select ")) {
            int start = line.indexOf('#');
            if (start != -1) {
                selectStack.offer(line.substring(start + 8));
                isFirstSelectCase = true;
            }
            return;
        } else if (lineLower.startsWith("#endselect")) {
            selectStack.pollLast();
            if (isFirstSelectCase) {
                isFirstSelectCase = false;
                return;
            }
            int start = line.indexOf('#');
            if (start == -1) {
                return;
            }
            line = line.substring(0, start) + "#endif";
            lineTrimmed = line.trim();
            lineLower = lineTrimmed.toLowerCase(Locale.ENGLISH);
        } else if (lineLower.startsWith("#case")) {
            int start = line.indexOf('#');
            if (start == -1 || selectStack.isEmpty()) {
                return;
            }
            String statement = line.substring(start + 5);

            // Check whether the statement is "default"
            String afterElse = null;
            boolean isFinalElse;
            {
                String statementTrimmed = statement;
                int statementEnd = statement.indexOf(':');
                if (statementEnd != -1) {
                    afterElse = statement.substring(statementEnd+1);
                    statementTrimmed = statement.substring(0, statementEnd);
                    selectCaseIndent = start + 5 + statementEnd;
                }
                isFinalElse = statementTrimmed.trim().equalsIgnoreCase("else");
            }

            StringBuilder replacement = new StringBuilder();
            replacement.append(line, 0, start);
            if (!isFirstSelectCase && isFinalElse) {
                replacement.append("#else");
                if (afterElse != null) {
                    replacement.append(':').append(afterElse);
                }
            } else {
                replacement.append(isFirstSelectCase ? "#if " : "#elseif ");
                replacement.append(selectStack.peekLast()).append(statement);
            }
            line = replacement.toString();
            lineTrimmed = line.trim();
            lineLower = lineTrimmed.toLowerCase(Locale.ENGLISH);
            isFirstSelectCase = false;
        }

        // For if/elseif/else statements, check if they have a statement on the same line following a :
        // If so, correct indentation and process the statement on a new line
        if (lineLower.startsWith("#if") || lineLower.startsWith("#elseif") || lineLower.startsWith("#else")) {
            int statementEnd = line.indexOf(':');
            if (statementEnd != -1) {
                // Line with #if/#elseif/#else
                preprocessLine(line.substring(0, statementEnd));

                // Line with the first line of statement code
                StringBuilder statementStr = new StringBuilder();

                // Select case translated, use original indent in select case block
                int numSpacesIndent = (selectCaseIndent != -1) ? selectCaseIndent : statementEnd;
                for (int n = 0; n <= numSpacesIndent; n++) {
                    statementStr.append(' ');
                }

                statementStr.append(line.substring(statementEnd + 1));
                preprocessLine(statementStr.toString());
                return;
            }
        }

        if (disabledIfLevel > 1) {
            // At this level, #elseif and #else have no effect, only switch levels
            if (lineLower.startsWith("#if")) {
                disabledIfLevel++;
            } else if (lineLower.startsWith("#endif")) {
                disabledIfLevel--;
            }
            return;
        }
        if (disabledIfLevel == 1) {
            // At this level, #elseif or #else can toggle modes
            if (lineLower.startsWith("#if")) {
                disabledIfLevel++;
            } else if (lineLower.startsWith("#endif")) {
                disabledIfLevel--;
            } else if (lineLower.startsWith("#else")) {
                int ifIdx = lineTrimmed.indexOf("if", 5);
                boolean evaluates = true;
                if (ifIdx != -1) {
                    // Else if - evaluate expression to decide whether to allow
                    String expr = lineTrimmed.substring(ifIdx + 2).trim();
                    evaluates = resolver.evaluateExpression(expr);
                }
                if (!disabledIfExpression && evaluates) {
                    // Evaluates - enter this if-block
                    disabledIfLevel--;
                }
            }
            return;
        }

        // Over here all lines are allowed to be included
        // Parse if-statements in case we go a level deeper
        // All else-evaluations fail here
        disabledIfExpression = false;
        if (lineLower.startsWith("#if")) {
            String expr = lineTrimmed.substring(3).trim();
            if (!resolver.evaluateExpression(expr)) {
                disabledIfLevel++;
            }
            return;
        }
        if (lineLower.startsWith("#else")) {
            disabledIfLevel++;
            disabledIfExpression = true;
            return;
        }
        if (lineLower.startsWith("#endif")) {
            return; // ignore
        }

        // Ignore comments
        if (lineLower.startsWith("//")) {
            return;
        }

        // The below statements are all included in the source
        result.append(line).append('\n');
        if (lineLower.startsWith("#set ")) {
            lineTrimmed = lineTrimmed.substring(5).trim();
            int nameEndIdx = lineTrimmed.indexOf(' ');
            if (nameEndIdx == -1) {
                return;
            }
            String varName = lineTrimmed.substring(0, nameEndIdx);
            String varValue = lineTrimmed.substring(nameEndIdx + 1);
            while (!varValue.isEmpty() && varValue.charAt(0) == ' ') {
                varValue = varValue.substring(1);
            }
            resolver.setVariable(varName, varValue);
            return;
        }
    }
}
