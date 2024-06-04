package com.bergerkiller.mountiplex.reflection.declarations;

import com.bergerkiller.mountiplex.reflection.util.InputTypeMap;
import com.bergerkiller.mountiplex.reflection.util.signature.FieldSignature;
import com.bergerkiller.mountiplex.reflection.util.signature.MethodSignature;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Defines a remapping rule for a method or field name. This remapping rule
 * is performed before any sort of global Resolve remapper is.
 */
public abstract class Remapping {
    public final Class<?> declaringClass;

    public static Lookup createLookup() {
        return new Lookup(new LookupTable());
    }

    public Remapping(Class<?> declaringClass) {
        this.declaringClass = declaringClass;
    }

    /**
     * Gets the Class where this remapping is declared. This remapping is active
     * for this class, and all classes derived from it.
     *
     * @return Declaring class
     */
    public final Class<?> getDeclaringClass() {
        return declaringClass;
    }

    /**
     * Lookup key for this remapping. This is what identifies this unique remapping
     * when resolving a method or field call in the code. For fields this is the
     * {@link FieldSignature}, for methods {@link MethodSignature}.
     *
     * @return Key
     */
    public abstract Object getKey();

    /**
     * Stores a mutable lookup table of all the remappings gathered so far
     */
    public static class Lookup implements Cloneable {
        private LookupTable table;

        private Lookup(LookupTable table) {
            this.table = table;
        }

        private void makeMutable() {
            if (table.isReadOnly()) {
                table = table.copy(false);
            }
        }

        public void assign(Lookup lookup) {
            this.table = lookup.table;
        }

        public void addRemapping(Remapping remapping) {
            makeMutable();
            table.addRemapping(remapping);
        }

        public FieldRemapping find(FieldDeclaration declaration) {
            return find(declaration.getResolver().getDeclaredClass(),
                    new FieldSignature(declaration.name.value()));
        }

        public FieldRemapping find(Class<?> type, FieldSignature signature) {
            if (type == null) {
                return null;
            }
            for (ClassRemapping remapping : table.getAll(type)) {
                FieldRemapping field = remapping.find(signature);
                if (field != null) {
                    return field;
                }
            }
            return null;
        }

        public MethodRemapping find(MethodDeclaration declaration) {
            return find(declaration.getDeclaringClass(), new MethodSignature(
                    declaration.name.value(), declaration.parameters.toParamArray()));
        }

        public MethodRemapping find(Class<?> type, MethodSignature signature) {
            if (type == null) {
                return null;
            }
            for (ClassRemapping remapping : table.getAll(type)) {
                MethodRemapping method = remapping.find(signature);
                if (method != null) {
                    return method;
                }
            }
            return null;
        }

        @Override
        public Lookup clone() {
            // First clone is a read-only copy for performance reasons
            // If changes are made, the object is cloned again making it not read-only
            return new Lookup(this.table.copy(true));
        }
    }

    // Adds read-only mode and locking logic around the input type map
    // This is required because a get(Type) internally modifies a data structure
    // It cannot be used multithreaded, and with remappings they will be used multithreaded potentially
    private static final class LookupTable {
        private final Object lock;
        private final boolean readOnly;
        private final InputTypeMap<ClassRemapping> remappingsByDeclaringClass;

        public LookupTable() {
            this.lock = new Object();
            this.readOnly = true;
            this.remappingsByDeclaringClass = new InputTypeMap<>();
        }

        private LookupTable(LookupTable original, boolean readOnly) {
            this.readOnly = readOnly;

            if (original.readOnly && readOnly) {
                // Both will be read-only, so there is no need to clone
                this.lock = original.lock;
                this.remappingsByDeclaringClass = original.remappingsByDeclaringClass;
            } else {
                // Making changes to a writable copy, need to clone
                this.lock = new Object();
                synchronized (original.lock) {
                    this.remappingsByDeclaringClass = original.remappingsByDeclaringClass.clone();
                }
            }
        }

        public boolean isReadOnly() {
            return readOnly;
        }

        public Collection<ClassRemapping> getAll(Class<?> type) {
            synchronized (lock) {
                return remappingsByDeclaringClass.getAll(type);
            }
        }

        public void addRemapping(Remapping remapping) {
            synchronized (lock) {
                ClassRemapping classRemapping = remappingsByDeclaringClass.get(remapping.getDeclaringClass());
                if (classRemapping == null) {
                    classRemapping = new ClassRemapping(remapping.getDeclaringClass());
                }
                classRemapping = classRemapping.withRemapping(remapping);
                remappingsByDeclaringClass.put(classRemapping.getDeclaringClass(), classRemapping);
            }
        }

        public LookupTable copy(boolean readOnly) {
            return new LookupTable(this, readOnly);
        }
    }

    /**
     * Remapping information for a Class.
     * TODO: Include remapping functionality for class names
     */
    public static class ClassRemapping extends Remapping {
        private final Map<Object, Remapping> remappings;

        public ClassRemapping(Class<?> declaringClass) {
            super(declaringClass);
            this.remappings = Collections.emptyMap();
        }

        private ClassRemapping(ClassRemapping base, Remapping newRemapping) {
            super(base.getDeclaringClass());
            if (base.remappings.isEmpty()) {
                this.remappings = Collections.singletonMap(newRemapping.getKey(), newRemapping);
            } else {
                this.remappings = new HashMap<>(base.remappings);
                this.remappings.put(newRemapping.getKey(), newRemapping);
            }
        }

        /**
         * Creates a copy of this immutable class remapping, including a new remapping entry.
         *
         * @param newRemapping New Remapping to add
         * @return new ClassRemapping with remapping added
         */
        public ClassRemapping withRemapping(Remapping newRemapping) {
            return new ClassRemapping(this, newRemapping);
        }

        @Override
        public Object getKey() {
            return getDeclaringClass();
        }

        public FieldRemapping find(FieldSignature signature) {
            return (FieldRemapping) remappings.get(signature);
        }

        public MethodRemapping find(MethodSignature signature) {
            return (MethodRemapping) remappings.get(signature);
        }
    }

    /**
     * Remaps the name of a field
     */
    public static class FieldRemapping extends Remapping {
        public final FieldSignature signature;
        public final Field field;
        public final FieldDeclaration declaration;

        public FieldRemapping(FieldDeclaration declaration) {
            super(declaration.getResolver().getDeclaredClass());
            this.signature = new FieldSignature(declaration.name.firstReal());
            this.field = declaration.field;
            this.declaration = declaration;
            if (field == null) {
                throw new IllegalStateException("Field for remapping is not resolved");
            }
        }

        @Override
        public Object getKey() {
            return signature;
        }
    }

    /**
     * Remaps the name of a method
     */
    public static class MethodRemapping extends Remapping {
        public final MethodSignature signature;
        public final Method method;
        public final MethodDeclaration declaration;

        public MethodRemapping(MethodDeclaration declaration) {
            super(declaration.getResolver().getDeclaredClass());
            this.signature = new MethodSignature(declaration.name.firstReal(), declaration.parameters.toParamArray());
            this.method = declaration.method;
            this.declaration = declaration;
            if (method == null) {
                throw new IllegalStateException("Method for remapping is not resolved");
            }
        }

        @Override
        public Object getKey() {
            return signature;
        }
    }
}
