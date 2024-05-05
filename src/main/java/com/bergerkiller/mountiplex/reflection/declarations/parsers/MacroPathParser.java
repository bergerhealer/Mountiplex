package com.bergerkiller.mountiplex.reflection.declarations.parsers;

import com.bergerkiller.mountiplex.MountiplexUtil;
import com.bergerkiller.mountiplex.reflection.declarations.parsers.context.DeclarationParserContext;
import com.bergerkiller.mountiplex.reflection.util.StringBuffer;

/**
 * Parses a macro, followed by a single path. Used for things like includes,
 * imports and packages.
 */
abstract class MacroPathParser extends MacroParser {

    public MacroPathParser(String macro) {
        super(macro);
    }

    public abstract void runMacro(String path, DeclarationParserContext context);

    @Override
    public void parse(ParserStringBuffer buffer, DeclarationParserContext context) {
        super.parse(buffer, context);

        String path = null;
        StringBuffer postfix = buffer.get();
        for (int cidx = 0; cidx < postfix.length(); cidx++) {
            char c = postfix.charAt(cidx);
            if (MountiplexUtil.containsChar(c, ParserStringBuffer.INVALID_NAME_CHARACTERS)) {
                path = postfix.substringToString(0, cidx);
                break;
            }
        }
        if (path == null) {
            path = postfix.toString();
        }

        buffer.trimLine();

        runMacro(path, context);
    }
}
