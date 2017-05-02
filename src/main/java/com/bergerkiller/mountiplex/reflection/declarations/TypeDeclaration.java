package com.bergerkiller.mountiplex.reflection.declarations;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.bergerkiller.mountiplex.MountiplexUtil;

/**
 * Represents a (generic) Type declaration and allows for type matching.
 * The {@link ClassResolver} passed in will be used to parse type declarations into runtime types.
 * Examples of declarations supported by this class:
 * <ul>
 * <li>Player</li>
 * <li>List&lt;String&gt;</li>
 * <li>Map&lt;Integer, Object&gt;</li>
 * </ul>
 */
public class TypeDeclaration extends Declaration {
    private static final Map<Class<?>, TypeDeclaration> byClass = new ConcurrentHashMap<Class<?>, TypeDeclaration>();
    public static final TypeDeclaration INVALID = new TypeDeclaration(ClassResolver.DEFAULT, (Type) null);
    public static final TypeDeclaration OBJECT = fromClass(Object.class);
    public static final TypeDeclaration ENUM = fromClass(Enum.class);
    public final boolean isWildcard;
    public final String typeName;
    public final String typePath;
    public final Class<?> type;
    public final TypeDeclaration[] genericTypes;
    private TypeDeclaration[] superTypes = null;

    /**
     * Turns a {@link Type} into a TypeDeclaration
     * 
     * @param resolver that is used with this type
     * @param type to read the declaration from
     */
    public TypeDeclaration(ClassResolver resolver, Type type) {
        super(resolver);

        // Null types are invalid
        if (type == null) {
            this.isWildcard = false;
            this.type = null;
            this.typeName = "NULL";
            this.typePath = "NULL";
            this.genericTypes = new TypeDeclaration[0];
            this.setInvalid();
            return;
        }

        // Handle wildcard types, only support one upper bound for now ( ? extends <type> )
        this.isWildcard = (type instanceof WildcardType);
        if (this.isWildcard) {
            type = ((WildcardType) type).getUpperBounds()[0];
        }

        // Arrays
        int arrayLevels = 0;
        while (type instanceof GenericArrayType) {
            type = ((GenericArrayType) type).getGenericComponentType();
            arrayLevels++;
        }

        // Process the type itself into a raw type + optional generic types
        if (type instanceof ParameterizedType) {
            // Example: Map<K, V>
            ParameterizedType ptype = (ParameterizedType) type;
            Type[] params = ptype.getActualTypeArguments();
            this.type = MountiplexUtil.getArrayType((Class<?>) ptype.getRawType(), arrayLevels);
            this.typePath = resolver.resolvePath(this.type);
            this.typeName = resolver.resolveName(this.type);
            this.genericTypes = new TypeDeclaration[params.length];
            for (int i = 0; i < params.length; i++) {
                this.genericTypes[i] = new TypeDeclaration(resolver, params[i]);
            }
        } else if (type instanceof Class) {
            // Example: Entity
            this.type = MountiplexUtil.getArrayType((Class<?>) type, arrayLevels);
            this.typePath = resolver.resolvePath(this.type);
            this.typeName = resolver.resolveName(this.type);
            this.genericTypes = new TypeDeclaration[0];
        } else if (type instanceof TypeVariable) {
            // Example: T
            TypeVariable<?> vtype = (TypeVariable<?>) type;
            this.type = MountiplexUtil.getArrayType(Object.class, arrayLevels);
            this.typePath = vtype.getName();
            this.typeName = vtype.getName();
            this.genericTypes = new TypeDeclaration[0];
        } else {
            // ???
            MountiplexUtil.LOGGER.warning("Unsupported type in TypeDeclaration: " + type.getClass());
            this.type = null;
            this.typePath = "";
            this.typeName = "";
            this.genericTypes = new TypeDeclaration[0];
            this.setInvalid();
        }
    }

    public TypeDeclaration(ClassResolver resolver, String declaration) {
        super(resolver, declaration);

        // Invalid declarations are forced by passing null
        if (declaration == null) {
            this.typeName = "";
            this.typePath = "";
            this.type = null;
            this.genericTypes = new TypeDeclaration[0];
            this.isWildcard = false;
            this.setInvalid();
            return;
        }

        // Find the end of the raw Class type in the declaration
        // This is the first '<' we find, or otherwise the first open space
        // We also allow types like List <String>, where a space preceeds the <
        String rawType = null;
        String postfix = "";
        int startIdx = -1;
        boolean anyType = false;
        boolean foundExtends = false;
        for (int cidx = 0; cidx < declaration.length(); cidx++) {
            char c = declaration.charAt(cidx);

            // Ignore spaces and anytype (?) at the start
            if (startIdx == -1) {
                if (c == ' ') {
                    continue;
                }
                if (c == '?') {
                    anyType = true;
                    continue;
                }
            }

            boolean validNameChar = !MountiplexUtil.containsChar(c, invalid_name_chars);

            // Verify the first character of the name is valid, and set it
            if (startIdx == -1) {
                if (validNameChar) {
                    startIdx = cidx; 
                } else {
                    postfix = declaration.substring(cidx);
                    break; // not a valid start of the name
                }
            }

            // The first invalid character finishes the raw type declaration
            if (!validNameChar && rawType == null) {
                rawType = declaration.substring(startIdx, cidx);
                if (anyType && !foundExtends) {
                    startIdx = -1;
                    if (rawType.equals("extends")) {
                        foundExtends = true;
                        rawType = null;
                    } else {
                        // Invalid!
                        break;
                    }
                }
            }

            // The first non-space starts the postfix part of this type declaration
            if (rawType != null && c != ' ') {
                postfix = declaration.substring(cidx);
                break;
            }
        }

        // Types that start with [? extends] are 'any types'
        this.isWildcard = anyType;

        // Raw type name not found? Invalid!
        if (startIdx == -1) {
            if (this.isWildcard) {
                this.setPostfix(postfix);
                this.typeName = "";
                this.typePath = "java.lang.Object";
                this.type = Object.class;
                this.genericTypes = new TypeDeclaration[0];
            } else {
                this.setInvalid();
                this.typeName = "";
                this.typePath = "";
                this.type = null;
                this.genericTypes = new TypeDeclaration[0];
            }
            return;
        }

        // No contents after raw type?
        if (rawType == null) {
            rawType = declaration.substring(startIdx);
            postfix = "";
        }

        if (postfix.length() > 0 && postfix.charAt(0) == '<') {

            // Go down the list of generic types, parsing them as TypeDeclaration recursively
            LinkedList<TypeDeclaration> types = new LinkedList<TypeDeclaration>();
            do {
                TypeDeclaration gen = new TypeDeclaration(resolver, postfix.substring(1));

                // If one of the generic types is invalid, set the entire type declaration invalid
                if (!gen.isValid()) {
                    this.setInvalid();
                    this.typeName = "";
                    this.typePath = "";
                    this.type = null;
                    this.genericTypes = new TypeDeclaration[0];
                    return;
                }

                types.add(gen);
                postfix = gen.getPostfix();
            } while (postfix.length() > 0 && postfix.charAt(0) == ',');

            // Trim starting spaces and single > from postfix
            for (int cidx = 0; cidx < postfix.length(); cidx++) {
                char c = postfix.charAt(cidx);
                if (c != ' ') {
                    if (c == '>') {
                        postfix = postfix.substring(cidx + 1);
                    } else {
                        postfix = postfix.substring(cidx);
                    }
                    break;
                }
            }

            // To array
            this.genericTypes = types.toArray(new TypeDeclaration[types.size()]);
        } else {
            // No generic types
            this.genericTypes = new TypeDeclaration[0];
        }

        // Check for array type declarations (put after the <> or type name)
        int arrayEnd = -1;
        for (int cidx = 0; cidx < postfix.length(); cidx++) {
            char c = postfix.charAt(cidx);
            if (c == '[' || c == ']') {
                rawType += c;
            } else if (c != ' ') {
                arrayEnd = cidx;
                break;
            }
        }
        if (arrayEnd == -1) {
            this.setPostfix("");
        } else {
            this.setPostfix(postfix.substring(arrayEnd));
        }

        // Resolve the raw type
        this.type = resolver.resolveClass(rawType);
        if (this.type == null) {
            this.typePath = resolver.resolvePath(rawType);
            this.typeName = rawType;
        } else {
            this.typePath = resolver.resolvePath(this.type);
            this.typeName = rawType;
        }
    }

    private TypeDeclaration(TypeDeclaration mainType, TypeDeclaration[] genericTypes) {
        super(mainType.getResolver());
        this.isWildcard = mainType.isWildcard;
        this.typeName = mainType.typeName;
        this.typePath = mainType.typePath;
        this.type = mainType.type;
        this.genericTypes = genericTypes;
    }

    /**
     * Gets the Type Declaration of the superclass of this type. If this type has no superclass,
     * this function returns null. Generic type information is resolved for the super type.<br>
     * <br>
     * For example, the Type Declaration for <b>HashMap&lt;String, Integer&gt;</b>
     * will have the super Type Declaration:<br>
     * <ul><li>AbstractMap&lt;String, Integer&gt;</li></ul>
     * 
     * @return Superclass type information
     */
    public TypeDeclaration getSuperType() {
        Type superClass = this.type.getGenericSuperclass();
        return (superClass == null) ? null : resolveSuperType(superClass);
    }

    /**
     * Gets all super classes and interfaces extended/implemented by this Type.
     * All Type Declarations returned can be assigned with object of this Type.
     * Generic type information is resolved for all super types.<br>
     * <br>
     * For example, the Type Declaration for <b>HashMap&lt;String, Integer&gt;</b>
     * will have the super Type Declarations:<br>
     * <ul>
     * <li>AbstractMap&lt;String, Integer&gt;</li>
     * <li>Object</li>
     * <li>Map&lt;String, Integer&gt;</li>
     * <li>Cloneable</li>
     * <li>java.io.Serializable</li>
     * </ul>
     * 
     * @return super types (classes and interfaces)
     */
    public TypeDeclaration[] getSuperTypes() {
        if (this.superTypes == null) {
            ArrayList<TypeDeclaration> types = new ArrayList<TypeDeclaration>();
            TypeDeclaration superType = this.getSuperType();
            if (superType != null) {
                types.add(superType);
                types.addAll(Arrays.asList(superType.getSuperTypes()));
            }
            for (Type iif : this.type.getGenericInterfaces()) {
                TypeDeclaration iifType = resolveSuperType(iif);
                if (!types.contains(iifType)) {
                    types.add(iifType);
                }
            }
            this.superTypes = types.toArray(new TypeDeclaration[types.size()]);
        }
        return this.superTypes;
    }

    public boolean isAssignableFrom(Object value) {
        return value != null && this.type.isAssignableFrom(value.getClass());
    }

    public boolean isAssignableFrom(TypeDeclaration other) {
        return other != null && other.isInstanceOf(this);
    }

    public boolean isInstanceOf(TypeDeclaration other) {
        if (other != null && other.type.isAssignableFrom(this.type)) {
            if (other.genericTypes.length == 0) {
                return true;
            }

            TypeDeclaration selfType = this.castAsType(other.type);
            if (selfType == null || other.genericTypes.length != selfType.genericTypes.length) {
                return false; // should never happen!
            }

            for (int i = 0; i < selfType.genericTypes.length; i++) {
                if (other.genericTypes[i].isWildcard) {
                    // ? extends TYPE
                    if (!selfType.genericTypes[i].isInstanceOf(other.genericTypes[i])) {
                        return false;
                    }
                } else {
                    // TYPE must be exactly the same
                    if (!other.genericTypes[i].equals(selfType.genericTypes[i])) {
                        return false;
                    }
                }
            }

            return true;
        }
        return false;
    }

    /**
     * Finds the generic type information for the given Class type, as if this
     * type has been casted to the Class specified.
     * 
     * @param classType to cast as
     * @return type declaration when casted as that type, or null if casting is impossible
     */
    public TypeDeclaration castAsType(Class<?> classType) {
        if (classType.equals(this.type)) {
            return this;
        }
        if (classType.isAssignableFrom(this.type)) {
            for (TypeDeclaration type : this.getSuperTypes()) {
                if (type.type.equals(classType)) {
                    return type;
                }
            }
        }
        return null;
    }

    /**
     * Returns a new Type Declaration of this type, with the generic parameter types changed
     * 
     * @param genericTypes to set to
     * @return new type declaration
     */
    public TypeDeclaration setGenericTypes(TypeDeclaration... genericTypes) {
        return new TypeDeclaration(this, genericTypes);
    }

    /**
     * Finds a generic type parameter for this Type. If this is a raw type,
     * OBJECT is returned.
     * 
     * @param index to get
     * @return type declaration
     */
    public TypeDeclaration getGenericType(int index) {
        return (index >= 0 && index < this.genericTypes.length) ? this.genericTypes[index] : OBJECT;
    }

    private final TypeDeclaration resolveSuperType(Type superClass) {
        TypeDeclaration superType = new TypeDeclaration(this.getResolver(), superClass);
        if (superType.genericTypes.length > 0 && this.genericTypes.length > 0) {
            // Correct super type generic types to use the types declared in this type
            // For example, HashMap<Integer, ?> should turn into AbstractMap<Integer, ?>
            // Without this, it would turn into AbstractMap<K, V>
            TypeVariable<?>[] params = this.type.getTypeParameters();
            if (params.length == this.genericTypes.length) {
                for (int i = 0; i < superType.genericTypes.length; i++) {
                    String name = superType.genericTypes[i].typePath;
                    for (int j = 0; j < params.length; j++) {
                        if (params[j].getName().equals(name)) {
                            superType.genericTypes[i] = this.genericTypes[j];
                            break;
                        }
                    }
                }
            }
        }
        return superType;
    }

    @Override
    public final boolean match(Declaration declaration) {
        if (!(declaration instanceof TypeDeclaration)) {
            return false;
        }
        TypeDeclaration type = (TypeDeclaration) declaration;
        if (this.type == null || type.type == null) return false;
        if (this.isWildcard != type.isWildcard) return false;
        if (!this.type.equals(type.type)) return false;
        if (this.genericTypes.length != type.genericTypes.length) return false;
        for (int i = 0; i < this.genericTypes.length; i++) {
            if (!this.genericTypes[i].match(type.genericTypes[i])) return false;
        }
        return true;
    }

    @Override
    public final String toString(boolean longPaths) {
        if (!isValid()) {
            return "??[" + _initialDeclaration + "]??";
        }
        String typeInfo = longPaths ? this.typePath : this.typeName;
        int arrIdx = typeInfo.indexOf('[');
        String arrPart = "";
        if (arrIdx != -1) {
            arrPart = typeInfo.substring(arrIdx);
            typeInfo = typeInfo.substring(0, arrIdx);
        }
        if (this.type == null) {
            typeInfo = "??" + typeInfo + "??";
        }

        String str;
        if (this.isWildcard) {
            if (typeInfo.length() == 0) {
                str = "?";
            } else {
                str = "? extends " + typeInfo;
            }
        } else {
            str = typeInfo;
        }
        if (this.genericTypes.length > 0) {
            str += "<";
            boolean first = true;
            for (TypeDeclaration genericType : genericTypes) {
                if (first) {
                    first = false;
                } else {
                    str += ", ";
                }
                str += genericType.toString(longPaths);
            }
            str += ">";
        }
        str += arrPart;
        return str;
    }

    @Override
    public boolean isResolved() {
        if (this.type == null) {
            return false;
        }
        for (int i = 0; i < genericTypes.length; i++) {
            if (!genericTypes[i].isResolved()) {
                return false;
            }
        }
        return true;
    }

    @Override
    protected void debugString(StringBuilder str, String indent) {
        str.append(indent).append("Type {\n");
        str.append(indent).append("  declaration=").append(this._initialDeclaration).append('\n');
        str.append(indent).append("  postfix=").append(this.getPostfix()).append('\n');
        str.append(indent).append("  typeName=").append(this.typeName).append('\n');
        str.append(indent).append("  typePath=").append(this.typePath).append('\n');
        str.append(indent).append("  type=").append(this.type).append('\n');
        str.append(indent).append("  isWildcard=").append(this.isWildcard).append('\n');
        for (TypeDeclaration t : this.genericTypes) {
            t.debugString(str, indent + "  ");
        }
        str.append(indent).append("}\n");
    }

    /**
     * Gets the Type Declaration of a standard Class type
     * 
     * @param classType to turn into a Type Declaration
     * @return Type Declaration
     */
    public static TypeDeclaration fromClass(Class<?> classType) {
        if (classType == null) {
            return INVALID;
        }
        TypeDeclaration type = byClass.get(classType);
        if (type == null) {
            type = new TypeDeclaration(ClassResolver.DEFAULT, classType);
            byClass.put(classType, type);
        }
        return type;
    }

    /**
     * Parses a Type Declaration using the default Class Resolver
     * 
     * @param declaration to parse
     * @return type declaration
     */
    public static TypeDeclaration parse(String declaration) {
        return new TypeDeclaration(ClassResolver.DEFAULT, declaration);
    }
}
