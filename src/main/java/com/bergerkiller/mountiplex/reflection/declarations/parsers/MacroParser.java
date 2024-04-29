package com.bergerkiller.mountiplex.reflection.declarations.parsers;

/**
 * Identifies a line starting with a #macro followed by a space
 */
abstract class MacroParser implements DeclarationParser {
    private final String prefix;

    public MacroParser(String macro) {
        this.prefix = macro + " ";
    }

    @Override
    public boolean detect(ParserStringBuffer buffer, DeclarationParserContext context) {
        return buffer.startsWith(prefix);
    }

    @Override
    public void parse(ParserStringBuffer buffer, DeclarationParserContext context) {
        buffer.trimWhitespace(prefix.length());
        // Note: must call super.parse()!
    }
}
