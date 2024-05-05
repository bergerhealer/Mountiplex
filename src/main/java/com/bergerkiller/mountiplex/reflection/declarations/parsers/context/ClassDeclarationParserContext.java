package com.bergerkiller.mountiplex.reflection.declarations.parsers.context;

public interface ClassDeclarationParserContext extends DeclarationParserContext {
    void appendCode(String code);
    void addCodeImport(String importPath);
}
