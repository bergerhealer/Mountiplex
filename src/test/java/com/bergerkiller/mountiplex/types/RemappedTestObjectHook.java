package com.bergerkiller.mountiplex.types;

import com.bergerkiller.mountiplex.reflection.ClassHook;

@ClassHook.HookLoadVariables("com.bergerkiller.mountiplex.types.TestClassDeclarationResolver.INSTANCE")
public class RemappedTestObjectHook extends ClassHook<RemappedTestObjectHook> {

    @HookMethod("public String remappedMethod(String input)")
    public String onRemappedMethod(String input) {
        return input + ":remapped";
    }
}
