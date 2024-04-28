package com.bergerkiller.mountiplex.reflection.util.asm.javassist;

import java.util.Map;

import com.bergerkiller.mountiplex.MountiplexUtil;
import com.bergerkiller.mountiplex.reflection.SafeField;
import com.bergerkiller.mountiplex.reflection.declarations.Remapping;
import com.bergerkiller.mountiplex.reflection.resolver.ResolvedClassPool;
import com.bergerkiller.mountiplex.reflection.resolver.Resolver;
import com.bergerkiller.mountiplex.reflection.util.ArrayHelper;
import com.bergerkiller.mountiplex.reflection.util.IgnoresRemapping;
import com.bergerkiller.mountiplex.reflection.util.asm.MPLType;
import com.bergerkiller.mountiplex.reflection.util.fast.GeneratedCodeInvoker;

import com.bergerkiller.mountiplex.reflection.util.signature.FieldSignature;
import com.bergerkiller.mountiplex.reflection.util.signature.MethodSignature;
import javassist.CtClass;
import javassist.CtField;
import javassist.NotFoundException;
import javassist.bytecode.MethodInfo;
import javassist.compiler.CompileError;
import javassist.compiler.MemberResolver;
import javassist.compiler.ast.ASTree;
import javassist.compiler.ast.Member;
import javassist.compiler.ast.Symbol;

/**
 * Custom {@link MemberResolver} that overrides some methods to provide
 * field and method remapping functionality. Class remapping is already
 * handled by the ClassPool and is not handled here.
 */
public final class MPLMemberResolver extends MemberResolver {
    /**
     * Field, method and class names with this prefix are ignored when doing name remapping.
     * The prefix is removed prior to compiling.
     */
    public static final String IGNORE_PREFIX = "MPL_NOREMAP$";
    /**
     * This class is the super class of the type being generated. The class extending this
     * can often not be found because it is being generated. Checking that the declaring
     * class superclass is this type helps performance a little bit.
     */
    private static final String GENERATED_CODE_INVOKER_NAME = GeneratedCodeInvoker.class.getName();
    /**
     * Last local field lookup that failed. Used to find more meaningful debug information
     * when resolving a field fails
     */
    private final ResolvedClassPool resolvedClassPool;
    private FailedLocalFieldLookup lastFailedLocalFieldLookup = null;

    public MPLMemberResolver(ResolvedClassPool cp) {
        super(cp);
        resolvedClassPool = cp;
    }

    @Override
    public ResolvedClassPool getClassPool() {
        return resolvedClassPool;
    }

    // Remaps the methodName symbol of a local or static method call
    @Override
    public Method lookupMethod(CtClass clazz, CtClass currentClass, MethodInfo current,
            String methodName,
            int[] argTypes, int[] argDims,
            String[] argClassNames) throws CompileError
    {
        // Resolve 'true' name of the method
        String actualMethodName = preprocessMethodName(clazz, methodName, argTypes, argDims, argClassNames);

        // While looking up method, do not resolve any more types
        // After all, all types are already resolved, including the CtClass super classes and such
        // TODO: Should we resolve the input argClassNames? It looks like the types are inferred,
        //       so there should be no further need to resolve.
        try (ResolvedClassPool.IgnoreToken t = this.resolvedClassPool.ignoreResolver()) {
            return super.lookupMethod(clazz, currentClass, current, actualMethodName, argTypes, argDims, argClassNames);
        }
    }

    // Remaps the fieldName symbol of a local field
    @Override
    public CtField lookupField(String className, final Symbol fieldNameSymbol)
            throws CompileError
    {
        CtClass cc;
        try (ResolvedClassPool.IgnoreToken t = this.resolvedClassPool.ignoreResolver()) {
            cc = lookupClass(className, false);
        }
        String fieldName = fieldNameSymbol.get();

        // Remapping happens here
        if (fieldNameSymbol instanceof Member) {
            fieldName = preprocessFieldName(cc, fieldName);
        }

        try {
            // No remapping, in case fields are queried of super classes this can cause a resolving loop!
            try (ResolvedClassPool.IgnoreToken t = this.resolvedClassPool.ignoreResolver()) {
                return cc.getField(fieldName);
            }
        }
        catch (NotFoundException e) {}

        this.lastFailedLocalFieldLookup = new FailedLocalFieldLookup(cc.getName(), fieldNameSymbol.get());
        throw new CompileError("no such field: " + cc.getName() + " -> " + fieldNameSymbol.get());
    }

    // Remaps the fieldName symbol of a static field
    @Override
    public CtField lookupFieldByJvmName2(String jvmClassName, Symbol fieldSym,
            ASTree expr) throws javassist.compiler.NoFieldException
    {
        String field = fieldSym.get();

        CtClass cc = null;
        String javaName = jvmToJavaName(jvmClassName);
        try {
            cc = lookupClass(javaName, true);
        }
        catch (CompileError e) {
            // Avoids confusion
            if (field.startsWith(IGNORE_PREFIX)) {
                field = field.substring(IGNORE_PREFIX.length());
            }

            // Log this as well, as this context helps explain missing fields better
            if (lastFailedLocalFieldLookup != null && lastFailedLocalFieldLookup.fieldName.equals(field)) {
                MountiplexUtil.LOGGER.severe("Local field lookup also failed: "
                        + lastFailedLocalFieldLookup.className + " -> "
                        + lastFailedLocalFieldLookup.fieldName);
                lastFailedLocalFieldLookup = null;
            }

            // EXPR might be part of a qualified class name.
            throw new NoFieldException(
                    jvmClassName + "/" + field,
                    "??" + javaName + "??." + field,
                    expr);
        }

        // Do field renaming here
        field = preprocessFieldName(cc, field);

        try {
            return cc.getField(field);
        }
        catch (NotFoundException e) {
            // maybe an inner class.
            jvmClassName = javaToJvmName(cc.getName());
            throw new NoFieldException(
                    jvmClassName + "$" + field,
                    cc.getName() + "." + field,
                    expr);
        }
    }

    // Used by lookupClass()
    private static final String INVALID;
    private static final java.lang.reflect.Method getInvalidNamesMethod;
    static {
        try {
            INVALID = SafeField.get(MemberResolver.class, "INVALID", String.class);
            getInvalidNamesMethod = MemberResolver.class.getDeclaredMethod("getInvalidNames");
            getInvalidNamesMethod.setAccessible(true);
        } catch (Throwable t) {
            throw MountiplexUtil.uncheckedRethrow(t);
        }
    }

    // The default method calls classPool.get() with an already-resolved (cached)
    // class name. We have to override it and retrieve the CtClass object without
    // resolving a second time, as this can cause double-resolving bugs.
    // If absent or incorrect in the cache, call the default method with cache
    // cleared.
    @Override
    @SuppressWarnings("unchecked")
    public CtClass lookupClass(String name, boolean notCheckInner)
            throws CompileError
    {
        Map<String,String> cache;
        try {
            cache = (Map<String,String>) getInvalidNamesMethod.invoke(this);
        } catch (Throwable t) {
            throw MountiplexUtil.uncheckedRethrow(t);
        }

        String found = cache.get(name);
        if (found == INVALID) {
            throw new CompileError("no such class: " + name);
        } else if (found != null) {
            try {
                return resolvedClassPool.getWithoutResolving(found);
            } catch (NotFoundException e) {
                // Remove from cache to prevent a classPool.get()
                cache.remove(name);
            }
        }

        // Fallback to default lookupClass0 / searchImports
        return super.lookupClass(name, notCheckInner);
    }

    /**
     * Same as {@link #lookupClassByJvmName(String)} but won't attempt to use
     * a class resolver to remap the input name
     *
     * @param jvmName
     * @return found class
     * @throws CompileError
     */
    public CtClass lookupClassByJvmNameIgnoreResolver(String jvmName) throws CompileError {
        String name = jvmToJavaName(jvmName);

        // If name could potentially clash, slap an IGNORE_PREFIX in front
        if (!name.startsWith(IGNORE_PREFIX) && !Resolver.resolveClassPath(name).equals(name)) {
            name = IGNORE_PREFIX + name;
        }

        return lookupClass(name, false);
    }

    @Override
    public CtClass lookupClassByJvmName(String jvmName) throws CompileError {
        return lookupClass(jvmToJavaName(jvmName), false);
    }

    @Override
    public CtClass lookupClass(int type, int dim, String classname) throws CompileError
    {
        if (type == CLASS && dim > 0) {
            // For Class type arrays we must prevent double-resolving issues
            CtClass clazz = lookupClassByJvmName(classname);
            String cname = clazz.getName();
            while (dim-- > 0)
                cname += "[]";
            return lookupClass(IGNORE_PREFIX + cname, false);
        } else {
            return super.lookupClass(type, dim, classname);
        }
    }

    // Remaps the name of a field
    // If it starts with the IGNORE_PREFIX, then the original field name
    // without the IGNORE_PREFIX is returned.
    // Otherwise, the remapper is asked to remap the field name,
    // returning the new name if found. If the class the field is declared in
    // could not be found, then no remapping is performed.
    private String preprocessFieldName(CtClass clazz, String fieldName) {
        // If field name uses the ignore prefix, remove prefix and skip the rest
        if (fieldName.startsWith(IGNORE_PREFIX)) {
            return fieldName.substring(IGNORE_PREFIX.length());
        }

        // Check for GeneratedCodeInvoker first, for better performance, since this one occurs all the time
        try {
            CtClass superClazz;
            try (ResolvedClassPool.IgnoreToken t = this.resolvedClassPool.ignoreResolver()) {
                superClazz = clazz.getSuperclass();
            }
            if (superClazz != null && superClazz.getName().equals(GENERATED_CODE_INVOKER_NAME)) {
                return fieldName;
            }
        } catch (NotFoundException e1) { /* ignored */ }

        // Resolve the class name first, only do remapping if we can identify this type
        Class<?> declaringClass;
        try {
            declaringClass = Resolver.getClassByExactName(clazz.getName());
        } catch (ClassNotFoundException e) {
            return fieldName;
        }

        // If the field declared is something that is generated by Mountiplex itself, skip.
        // This offers a slight performance improvement, avoiding extra Resolver lookups
        if (IgnoresRemapping.class.isAssignableFrom(declaringClass)) {
            return fieldName;
        }

        // Ask remappings first. These store the already-resolved field instances
        {
            Remapping.FieldRemapping remappedField = getClassPool().getRemappings().find(
                    declaringClass, new FieldSignature(fieldName));
            if (remappedField != null) {
                return MPLType.getName(remappedField.field);
            }
        }

        // Ask resolver
        String newName = Resolver.resolveFieldName(declaringClass, fieldName);
        return (newName != null) ? newName : fieldName;
    }

    // Remaps the name of a method
    // If it starts with the IGNORE_PREFIX, then the original method name
    // without the IGNORE_PREFIX is returned.
    // Otherwise, the remapper is asked to remap the method name,
    // returning the new name if found. If the signature
    // could not be decoded into Class types, then no remapping is performed.
    private String preprocessMethodName(CtClass clazz, String methodName, int[] argTypes, int[] argDims, String[] argClassNames) {
        // If method name uses the ignore prefix, remove prefix and skip the rest
        if (methodName.startsWith(IGNORE_PREFIX)) {
            return methodName.substring(IGNORE_PREFIX.length());
        }

        // If initializer, skip, those don't get remapped
        if (methodName.equals(MethodInfo.nameInit)) {
            return methodName;
        }

        // Performance: skip all java.** types
        if (clazz.getName().startsWith("java.")) {
            return methodName;
        }

        // Check for GeneratedCodeInvoker first, for better performance, since this one occurs all the time
        try {
            CtClass superClazz = clazz.getSuperclass();
            if (superClazz != null && superClazz.getName().equals(GENERATED_CODE_INVOKER_NAME)) {
                return methodName;
            }
        } catch (NotFoundException e1) { /* ignored */ }

        // Figure out the actual Class. If not found, skip.
        Class<?> declaringClass;
        try {
            declaringClass = Resolver.getClassByExactName(clazz.getName());
        } catch (ClassNotFoundException e) {
            return methodName;
        }

        // If the method declared is something that is generated by Mountiplex itself, skip.
        // This offers a slight performance improvement, avoiding extra Resolver lookups
        if (IgnoresRemapping.class.isAssignableFrom(declaringClass)) {
            return methodName;
        }

        // Resolve parameter types
        Class<?>[] parameterTypes = new Class[argTypes.length];
        for (int i = 0; i < parameterTypes.length; i++) {
            parameterTypes[i] = translateType(argTypes[i], argClassNames[i]);
            if (parameterTypes[i] == null) {
                return methodName; // Failed to resolve
            }
            parameterTypes[i] = ArrayHelper.getArrayType(parameterTypes[i], argDims[i]);
        }

        // Ask remappings first. These store the already-resolved method instances
        {
            Remapping.MethodRemapping remappedMethod = getClassPool().getRemappings().find(
                    declaringClass, new MethodSignature(methodName, parameterTypes));
            if (remappedMethod != null) {
                return MPLType.getName(remappedMethod.method);
            }
        }

        // Ask resolver
        return Resolver.resolveMethodName(declaringClass, methodName, parameterTypes);
    }

    /**
     * Translates type information to the appropriate Class. If the input type
     * is of type CLASS, then the altName is decoded. If null is returned, then
     * the type could not be deduced.
     * 
     * @param type Binary type code
     * @param altName Alternative class name to decode
     * @return type, null if not found
     */
    private static Class<?> translateType(int type, String altName) {
        //TODO: Is this really not found builtin somewhere? :(
        switch (type) {
        case BOOLEAN:
            return boolean.class;
        case CHAR:
            return char.class;
        case BYTE:
            return byte.class;
        case SHORT:
            return short.class;
        case INT:
            return int.class;
        case LONG:
            return long.class;
        case FLOAT:
            return float.class;
        case DOUBLE:
            return double.class;
        case CLASS:
            try {
                return Resolver.getClassByExactName(altName.replace('/', '.'));
            } catch (ClassNotFoundException e) {
                return null;
            }
        default:
            return null;
        }
    }

    // Allows changing the printed name to include extra context
    public static class NoFieldException extends javassist.compiler.NoFieldException {
        private static final long serialVersionUID = -3755584273114392309L;
        private final String replFieldName;

        public NoFieldException(String symbol, String printed, ASTree e) {
            super(printed, e);
            this.replFieldName = symbol;
        }

        /* 
         * The returned name should be JVM-internal representation.
         */
        @Override
        public String getField() {
            return this.replFieldName;
        }
    }

    private static class FailedLocalFieldLookup {
        public final String className;
        public final String fieldName;

        public FailedLocalFieldLookup(String className, String fieldName) {
            this.className = className;
            this.fieldName = fieldName;
        }
    }
}
