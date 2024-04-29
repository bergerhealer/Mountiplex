package com.bergerkiller.mountiplex.reflection.declarations.parsers;

import com.bergerkiller.mountiplex.reflection.util.StringBuffer;

import java.io.File;

public interface SourceDeclarationParserContext extends DeclarationParserContext {
    ClassLoader getClassLoader();
    File getCurrentDirectory();
    void includeSource(StringBuffer subSource);

    // #setpath logic
    void setCurrentTemplateFile(String path);
    String getCurrentTemplateFile();
}
