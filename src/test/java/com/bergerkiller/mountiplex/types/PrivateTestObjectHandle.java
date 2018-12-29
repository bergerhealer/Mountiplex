package com.bergerkiller.mountiplex.types;

import com.bergerkiller.mountiplex.reflection.declarations.Template;
import com.bergerkiller.mountiplex.reflection.util.StaticInitHelper;

public abstract class PrivateTestObjectHandle extends Template.Handle {
    public static final PrivateTestObjectClass T = new PrivateTestObjectClass();
    protected static final StaticInitHelper _init_helper = new StaticInitHelper(PrivateTestObjectHandle.class, "com.bergerkiller.mountiplex.types.PrivateTestObject", com.bergerkiller.mountiplex.types.TestClassDeclarationResolver.INSTANCE);

    /* ============================================================================== */

    public static PrivateTestObjectHandle createHandle(Object handleInstance) {
        return T.createHandle(handleInstance);
    }

    /* ============================================================================== */

    public abstract String getField();
    public abstract void setField(String value);
    public abstract String method();

    public static class PrivateTestObjectClass extends Template.Class<PrivateTestObjectHandle> {
        public final Template.Field<String> field = new Template.Field<String>();
        public final Template.Method<String> method = new Template.Method<String>();
    }
}
