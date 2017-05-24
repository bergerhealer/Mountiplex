package com.bergerkiller.mountiplex.reflection.resolver;

import java.lang.reflect.MalformedParameterizedTypeException;
import java.util.HashMap;

import com.bergerkiller.mountiplex.reflection.declarations.TypeDeclaration;

/**
 * Stores cached metadata information for Class types
 */
public class ClassMetadata {
    private static final HashMap<Class<?>, Metadata> _meta = new HashMap<Class<?>, Metadata>();

    public static Metadata get(Class<?> type) {
        synchronized (_meta) {
            Metadata m = _meta.get(type);
            if (m == null) {
                m = new Metadata(type);
                _meta.put(type, m);
            }
            return m;
        }
    }

    /// some bad class was found and we have to figure out what type variable is used <>
    /// we can do this by inspecting the base interface/class methods and expecting generic type from that
    private static TypeDeclaration fixResolveGenericTypes(TypeDeclaration type, TypeDeclaration base) {
        //TODO: Somehow implement this? It's a little tricky. Probably requires ASM Class Reader.
        return base;
    }

    private static TypeDeclaration findSuperType(TypeDeclaration type) {
        try {
            java.lang.reflect.Type s = type.type.getGenericSuperclass();
            return (s == null) ? null : TypeDeclaration.fromType(s);
        } catch (MalformedParameterizedTypeException ex) {
            Class<?> s = type.type.getSuperclass();
            return (s == null) ? null : fixResolveGenericTypes(type, TypeDeclaration.fromClass(s));
        }
    }

    private static TypeDeclaration[] findInterfaces(TypeDeclaration type) {
        try {
            java.lang.reflect.Type[] interfaces = type.type.getGenericInterfaces();
            TypeDeclaration[] result = new TypeDeclaration[interfaces.length];
            for (int i = 0; i < result.length; i++) {
                result[i] = TypeDeclaration.fromType(interfaces[i]);
            }
            return result;
        } catch (MalformedParameterizedTypeException ex) {
            Class<?>[] interfaces = type.type.getInterfaces();
            TypeDeclaration[] result = new TypeDeclaration[interfaces.length];
            for (int i = 0; i < result.length; i++) {
                result[i] = fixResolveGenericTypes(type, TypeDeclaration.fromClass(interfaces[i]));
            }
            return result;
        }
    }

    public static class Metadata {
        public final Class<?> type;
        public final TypeDeclaration typeDec;
        public final TypeDeclaration[] interfaces;
        public final TypeDeclaration superType;

        private Metadata(Class<?> type) {
            this.type = type;
            this.typeDec = TypeDeclaration.fromClass(type);
            this.superType = findSuperType(this.typeDec);
            this.interfaces = findInterfaces(this.typeDec);
        }
    }
}
