package com.bergerkiller.mountiplex.reflection.declarations.parsers;

import com.bergerkiller.mountiplex.reflection.declarations.parsers.context.DeclarationParserContext;

/**
 * This parser receives information about the textual contents of template code being parsed,
 * and can then make changes to the underlying declaration.
 */
public interface DeclarationParser {
    /**
     * Attempts to detect whether this parser should run right now. If this method
     * returns true, then {@link #parse(ParserStringBuffer, DeclarationParserContext)} will be called
     * to process the contents. Afterwards, other parsers will be called again
     * for the next text.
     *
     * @param buffer Mutable String Buffer of the current text being parsed
     * @param context Parsing context
     * @return True if this declaration parser should run right now
     */
    boolean detect(ParserStringBuffer buffer, DeclarationParserContext context);

    /**
     * Parses the current text position. Processes any macros or logic encountered in
     * the text.<br>
     * <br>
     * Should advance the text position beyond where
     * {@link #detect(ParserStringBuffer, DeclarationParserContext)} can find this statement to avoid an
     * infinite loop.
     *
     * @param buffer Mutable String Buffer of the current text being parsed
     * @param context Parsing context
     */
    void parse(ParserStringBuffer buffer, DeclarationParserContext context);
}
