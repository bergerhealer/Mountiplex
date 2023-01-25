package com.bergerkiller.mountiplex.reflection.util.asm;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

/**
 * Parses multi-line variables stored in block comments
 */
public class SourceFileProcessor {
    /**
     * These variable names are ignored
     */
    private static final Set<String> IGNORED_NAMES = new HashSet<>(Arrays.asList(
            "u", "i", "b", "p", "pre", "code"));

    public SourceFileProcessor() {
    }

    public Map<String, String> process(File sourceFile) throws IOException {
        Map<String, String> variables = new HashMap<>();
        String content = new String(Files.readAllBytes(sourceFile.toPath()), StandardCharsets.UTF_8);

        // State keeping
        char curr_char = ' ';
        char prev_char = ' ';
        boolean in_string = false;
        boolean in_block_comment = false;
        boolean in_block_comment_contents = false;
        int start_indent = 0;
        int min_start_indent = Integer.MAX_VALUE;
        StringBuilder block_comment = new StringBuilder();

        // State processing loop
        for (int i = 0; i < content.length(); i++) {
            prev_char = curr_char;
            curr_char = content.charAt(i);

            // Ew.
            if (curr_char == '\r') {
                continue;
            }

            if (!in_block_comment) {
                // Ignore all String contents
                if (curr_char == '\n') {
                    start_indent = 0;
                } else if (curr_char == ' ') {
                    start_indent++;
                } else if (curr_char == '\t') {
                    start_indent += 4;
                } else if (in_string && prev_char != '\\' && curr_char == '\"') {
                    in_string = false;
                } else if (!in_string && curr_char == '\"') {
                    in_string = true;
                } else if (!in_string && prev_char == '/' && curr_char == '*') {
                    in_block_comment = true;
                    in_block_comment_contents = false;
                    start_indent += 2;
                    min_start_indent = Integer.MAX_VALUE;
                }
                continue;
            }

            // Skip whitespace preceeding it, deal with newlines properly
            if (!in_block_comment_contents) {
                if (curr_char == '\n') {
                    block_comment.append('\n');
                    start_indent = 0;
                    continue;
                } else if (curr_char == ' ' || curr_char == '*') {
                    start_indent++;
                    continue;
                } else if (curr_char == '\t') {
                    start_indent += 4;
                    continue;
                }
                in_block_comment_contents = true;
                appendSpaces(block_comment, start_indent);
            } else if (curr_char == '\n') {
                in_block_comment_contents = false;
                start_indent = 0;
                block_comment.append('\n');
                continue;
            }

            // Detect end of a /* block comment */
            if (prev_char == '*' && curr_char == '/') {
                in_block_comment = false;
                in_block_comment_contents = false;
                cleanupSpaces(block_comment, min_start_indent);
                processBlockComment(block_comment, variables);

                // Reuse
                block_comment.setLength(0);
                continue;
            }

            // Build up the contents of the block comment
            min_start_indent = Math.min(min_start_indent, start_indent);
            block_comment.append(curr_char);
        }
        return variables;
    }

    private void appendSpaces(StringBuilder str, int n) {
        while (--n >= 0) {
            str.append(' ');
        }
    }

    private void cleanupPreceedingSpaces(StringBuilder str) {
        for (int i = 0; i < str.length(); ) {
            char c = str.charAt(i);
            if (c == '\n') {
                str.replace(0, i + 1, "");
                i = 0;
            } else if (c == ' ') {
                i++;
            } else {
                break;
            }
        }
    }

    private void cleanupSpaces(StringBuilder str, int indent) {
        // Remove unused spaces from the start
        cleanupPreceedingSpaces(str);

        // Remove unused spaces at the end
        for (int i = str.length() - 1; i >= 0; ) {
            char c = str.charAt(i);
            if (c == ' ' || c == '\n') {
                i--;
            } else {
                str.replace(i + 1, str.length(), "");
                break;
            }
        }

        // Trim indent spaces from the start of each line, if possible
        int remainingSpaces = indent;
        int numSkipped = 0;
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (c == ' ' && remainingSpaces > 0) {
                remainingSpaces--;
                numSkipped++;
            } else if (numSkipped > 0) {
                str.replace(i - numSkipped, i, "");
                numSkipped = 0;
            }
            if (c == '\n') {
                remainingSpaces = indent;
            }
        }
    }

    public void processBlockComment(StringBuilder str, Map<String, String> variables) {
        // Check for <start_marker>
        if (str.length() < 2 || str.charAt(0) != '<') {
            return;
        }

        // Find end of marker
        int endIndex = str.indexOf(">", 1);
        if (endIndex == -1) {
            return;
        }
        int newLineIndex = str.indexOf("\n", 1);
        if (newLineIndex == -1 || newLineIndex < endIndex) {
            return;
        }

        String variableName = str.substring(1, endIndex).trim();

        // Some variable names are used for javadoc formatting, ignore those
        if (IGNORED_NAMES.contains(variableName)) {
            return;
        }

        str.replace(0, endIndex + 1, "");
        cleanupPreceedingSpaces(str);
        String variableValue = str.toString();

        variables.put(variableName, variableValue);
    }
}
