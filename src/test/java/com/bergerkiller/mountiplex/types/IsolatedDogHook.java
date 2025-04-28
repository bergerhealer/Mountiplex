package com.bergerkiller.mountiplex.types;

import com.bergerkiller.mountiplex.ClassHookTest;
import com.bergerkiller.mountiplex.reflection.ClassHook;

/**
 * In its own class because nested classes trip up ASM and I cant be arsed
 */
public class IsolatedDogHook extends ClassHook<IsolatedDogHook> {
    @HookMethod("public String woof()")
    public String theWoofMethod() {
        return "IsolatedDogHook::theIsolatedWoofMethod()";
    }
}
