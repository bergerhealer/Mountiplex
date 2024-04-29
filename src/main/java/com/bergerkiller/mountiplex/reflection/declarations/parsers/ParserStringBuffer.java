package com.bergerkiller.mountiplex.reflection.declarations.parsers;

import com.bergerkiller.mountiplex.MountiplexUtil;
import com.bergerkiller.mountiplex.reflection.declarations.ClassResolver;
import com.bergerkiller.mountiplex.reflection.declarations.Declaration;
import com.bergerkiller.mountiplex.reflection.util.StringBuffer;

/**
 * Wraps an ordinary {@link StringBuffer} to make it mutable. Adds some helper
 * methods for parsing templates.
 */
public final class ParserStringBuffer {
    public static final char[] WHITESPACE_CHARACTERS = new char[] {
            ' ', '\n', '\r'
    };
    public static final char[] INVALID_NAME_CHARACTERS = new char[] {
            ' ', '\n', '\r', '<', '>', ',', '(', ')', '{', '}', ';', '='
    };

    private StringBuffer _text = null;

    public StringBuffer get() {
        return this._text;
    }

    public void set(StringBuffer text) {
        this._text = text;
    }

    /**
     * Gets whether this text was set to null, to indicate no text remains
     *
     * @return True if text is null
     */
    public boolean isNull() {
        return _text == null;
    }

    @Override
    public String toString() {
        return _text.toString();
    }

    public boolean startsWith(String text) {
        return _text.startsWith(text);
    }

    /**
     * Removes all whitespace characters from the start of the current postfix
     *
     * @param start index
     */
    public void trimWhitespace(int start) {
        if (this._text == null) {
            return;
        }
        for (int cidx = start; cidx < this._text.length(); cidx++) {
            char c = this._text.charAt(cidx);
            if (MountiplexUtil.containsChar(c, WHITESPACE_CHARACTERS)) {
                continue;
            }
            this._text = this._text.substring(cidx);
            return;
        }
        this._text = StringBuffer.EMPTY;
    }

    /**
     * Removes everything up until the next newline
     *
     * @return contents that were trimmed, excluding the newline character
     */
    public String trimLine() {
        if (this.isNull()) {
            return "";
        }

        int firstNewLineIdx = -1;
        for (int cidx = 0; cidx < this._text.length(); cidx++) {
            char c = this._text.charAt(cidx);
            if (c == '\r' || c == '\n') {
                if (firstNewLineIdx == -1) {
                    firstNewLineIdx = cidx;
                }
                continue;
            }
            if (c != ' ' && firstNewLineIdx != -1) {
                String remainder = this._text.substringToString(0, firstNewLineIdx);
                this._text = this._text.substring(cidx);
                return remainder;
            }
        }

        String remainder = this._text.toString();
        this._text = StringBuffer.EMPTY;
        return remainder;
    }

    /**
     * Skips type variables defined for the current string, up until an invalid name character
     * is encountered such as a space. This removes generic type information of a type.
     */
    public void trimGenericTypes() {
        // Skip type variables, they may exist. For now do a simple replace between < > portions
        //TODO: Make this better? It makes it overly complicated.
        StringBuffer postfix = get();
        if (postfix != null && postfix.length() > 0 && postfix.charAt(0) == '<') {
            boolean foundEnd = false;
            for (int cidx = 1; cidx < postfix.length(); cidx++) {
                char c = postfix.charAt(cidx);
                if (c == '>') {
                    foundEnd = true;
                } else if (foundEnd && !MountiplexUtil.containsChar(c, INVALID_NAME_CHARACTERS)) {
                    set(postfix.substring(cidx));
                    break;
                }
            }
        }
    }

    /**
     * Attempts to parse a method, constructor or field at the current text buffer
     * position. If a declaration is parsed, the buffer is updated to beyond
     * the information of this new declaration.
     *
     * @param resolver ClassResolver to use for the new declaration, if found
     * @return Parsed declaration, or null if none was parsed
     */
    public Declaration detectMemberDeclaration(ClassResolver resolver) {
        StringBuffer postfix = get();
        Declaration dec = Declaration.parseDeclaration(resolver, postfix);
        if (dec != null) {
            this.set(dec.getPostfix());
            this.trimLine();
            return dec;
        }
        return null;
    }
}
