package com.bergerkiller.mountiplex.reflection.declarations;

/**
 * Requirement added using #require that must be included during code generation
 */
public class Requirement {
    public final String name;
    public final Declaration declaration;

    public Requirement(String name, Declaration declaration) {
        this.name = name;
        this.declaration = declaration;
    }
}
