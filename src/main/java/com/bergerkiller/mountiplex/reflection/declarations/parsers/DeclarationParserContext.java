package com.bergerkiller.mountiplex.reflection.declarations.parsers;

import com.bergerkiller.mountiplex.reflection.declarations.ClassResolver;

/**
 * Provides context about the current parsing situation. Results can be written
 * to this context.
 */
public interface DeclarationParserContext {
    ClassResolver getResolver();
    ParserStringBuffer getBuffer();
    void addWarning(String warning);
    void addError(String error);

    /**
     * Performs a single parsing step at the current buffer position
     * using the parsers specified.
     *
     * @param parsers Parsers that parse the current context
     * @return True if a parser identified the current buffer and processed it
     */
    default boolean runParsers(DeclarationParser[] parsers) {
        ParserStringBuffer buffer = getBuffer();
        if (buffer.isNull()) {
            return false;
        }

        for (DeclarationParser parser : parsers) {
            if (parser.detect(buffer, this)) {
                parser.parse(buffer, this);
                return true;
            }
        }

        return false;
    }
}
