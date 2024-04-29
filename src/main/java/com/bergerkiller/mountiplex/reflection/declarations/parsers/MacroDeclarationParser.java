package com.bergerkiller.mountiplex.reflection.declarations.parsers;

import com.bergerkiller.mountiplex.MountiplexUtil;
import com.bergerkiller.mountiplex.reflection.declarations.ClassResolver;
import com.bergerkiller.mountiplex.reflection.declarations.Declaration;
import com.bergerkiller.mountiplex.reflection.util.StringBuffer;

import java.util.logging.Level;

/**
 * Base class for parsing the #require and #remap declarations.
 * This base class handles the parsing of the declaring class and the
 * method/constructor/field declaration.
 */
abstract class MacroDeclarationParser extends MacroParser {
    private final String typeName;

    public MacroDeclarationParser(String macro, String typeName) {
        super(macro); // #require / #remap
        this.typeName = typeName; // requirement / remapping
    }

    public abstract void runMacro(Declaration declaration, DeclarationParserContext context);

    @Override
    public void parse(ParserStringBuffer buffer, DeclarationParserContext context) {
        super.parse(buffer, context);

        // Get class name in which this is defined
        int declaringClassEnd = buffer.get().indexOf(' ');
        if (declaringClassEnd == -1) {
            buffer.set(StringBuffer.EMPTY);
            return;
        }

        String declaringClassName = buffer.get().substringToString(0, declaringClassEnd);

        // Trim class name from start of declaration
        buffer.trimWhitespace(declaringClassEnd);

        // What remains now is a declaration for a field, method or constructor
        ClassResolver resolver = context.getResolver().clone();
        resolver.setDeclaredClassName(declaringClassName);
        Declaration dec = buffer.detectMemberDeclaration(resolver);
        if (dec == null) {
            // Trim to end of line
            String remainder = buffer.trimLine();

            // Log this
            if (context.getResolver().getLogErrors()) {
                MountiplexUtil.LOGGER.log(Level.SEVERE, "Declaration invalid for " + typeName + ": " + declaringClassName);
                MountiplexUtil.LOGGER.log(Level.SEVERE, "Declaration: " + remainder);
            }

            return;
        }

        // Skip actually using/parsing this when generating templates
        // This avoids needless error logging
        if (context.getResolver().isGenerating()) {
            return;
        }

        runMacro(dec, context);
    }
}
