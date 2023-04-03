package com.bergerkiller.mountiplex.reflection.declarations;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.bergerkiller.mountiplex.MountiplexUtil;
import com.bergerkiller.mountiplex.reflection.ReflectionUtil;
import com.bergerkiller.mountiplex.reflection.resolver.Resolver;
import com.bergerkiller.mountiplex.reflection.util.BoxedType;
import com.bergerkiller.mountiplex.reflection.util.StringBuffer;
import com.bergerkiller.mountiplex.reflection.util.asm.MPLType;

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
    /** A type declaration that has no type information */
    public static final TypeDeclaration INVALID = new TypeDeclaration(ClassResolver.DEFAULT, (Type) null);
    /** Object Class type */
    public static final TypeDeclaration OBJECT = new TypeDeclaration(ClassResolver.DEFAULT, Object.class);
    /** Enum Class type */
    public static final TypeDeclaration ENUM = new TypeDeclaration(ClassResolver.DEFAULT, Enum.class);
    /** Represents any type (that extends Object), which basically makes it the '?' type */
    public static final TypeDeclaration ANY = parse("?");
    /** Whether this TypeDeclaration starts with '? extends &lt;type&gt;', and the type field refers to what it extends */
    public final boolean isWildcard;
    /** Whether this TypeDeclaration refers to a primitive type, such as int/long/etc. */
    public final boolean isPrimitive;
    /** Caches the boxed type of a primitive (int/long/etc.). Stores itself if not a primitive. */
    private final TypeDeclaration boxed;

    public final String variableName;
    public final String typeName;
    public final String typePath;
    public final Class<?> type;
    public final TypeDeclaration[] genericTypes;
    public final TypeDeclaration cast;
    private TypeDeclaration[] superTypes = null;

    /**
     * Turns a {@link Type} into a TypeDeclaration.<br>
     * <br>
     * <b>Please do not use this. Use {@link #fromClass(Class)} instead.</b>
     * 
     * @param resolver that is used with this type
     * @param type to read the declaration from
     */
    public TypeDeclaration(ClassResolver resolver, Type type) {
        super(resolver);

        // Casting never used when parsing from Type
        this.cast = null;

        // Null types are invalid
        if (type == null) {
            this.isWildcard = false;
            this.variableName = null;
            this.type = null;
            this.typeName = "NULL";
            this.typePath = "NULL";
            this.isPrimitive = false;
            this.boxed = this;
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
            this.isPrimitive = false;
            this.boxed = this;
            this.genericTypes = new TypeDeclaration[params.length];
            this.variableName = null;
            for (int i = 0; i < params.length; i++) {
                this.genericTypes[i] = new TypeDeclaration(resolver, params[i]);
            }
        } else if (type instanceof Class) {
            // Example: Entity
            this.type = MountiplexUtil.getArrayType((Class<?>) type, arrayLevels);
            this.typePath = resolver.resolvePath(this.type);
            this.typeName = resolver.resolveName(this.type);
            this.isPrimitive = this.type.isPrimitive();
            this.boxed = this.isPrimitive ? fromClass(BoxedType.getBoxedType(this.type)) : this;
            this.genericTypes = new TypeDeclaration[0];
            this.variableName = null;
        } else if (type instanceof TypeVariable) {
            // Example: T
            TypeVariable<?> vtype = (TypeVariable<?>) type;
            Type varType = vtype.getBounds()[0];

            // Turn varType into a Class. We must have a Class. Handle special types.
            Class<?> bound;
            while (true) {
                if (varType instanceof Class) {
                    bound = (Class<?>) varType;
                    break;
                } else if (varType instanceof ParameterizedType) {
                    varType = ((ParameterizedType) varType).getRawType();
                } else if (varType instanceof TypeVariable) {
                    varType = ((TypeVariable<?>) varType).getBounds()[0];
                } else {
                    bound = Object.class;
                    break;
                }
            }

            this.type = MountiplexUtil.getArrayType(bound, arrayLevels);
            this.typePath = resolver.resolvePath(this.type);
            this.typeName = resolver.resolveName(this.type);
            this.isPrimitive = false;
            this.boxed = this;
            this.genericTypes = new TypeDeclaration[0];
            this.variableName = vtype.getName();
        } else {
            // ???
            MountiplexUtil.LOGGER.warning("Unsupported type in TypeDeclaration: " + type.getClass());
            this.type = null;
            this.typePath = "";
            this.typeName = "";
            this.isPrimitive = false;
            this.boxed = this;
            this.genericTypes = new TypeDeclaration[0];
            this.variableName = null;
            this.setInvalid();
        }
    }

    private TypeDeclaration(ClassResolver resolver, StringBuffer declaration) {
        super(resolver, declaration);

        // Invalid declarations are forced by passing null
        if (declaration == null) {
            this.typeName = "";
            this.typePath = "";
            this.type = null;
            this.isPrimitive = false;
            this.boxed = this;
            this.genericTypes = new TypeDeclaration[0];
            this.isWildcard = false;
            this.variableName = null;
            this.cast = null;
            this.setInvalid();
            return;
        }

        // Find the end of the raw Class type in the declaration
        // This is the first '<' we find, or otherwise the first open space
        // We also allow types like List <String>, where a space preceeds the <
        String rawType = null;
        String typeVarName = null;
        StringBuffer postfix = StringBuffer.EMPTY;
        int startIdx = -1;
        boolean anyType = false;
        boolean foundExtends = false;
        TypeDeclaration castType = null;
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
                if (c == ')' && castType != null) {
                    continue;
                }
            }

            boolean validNameChar = !MountiplexUtil.containsChar(c, invalid_name_chars);

            // Verify the first character of the name is valid, and set it
            if (startIdx == -1) {
                if (c == '(') {
                    // Find the very next matching ) for closing the cast
                    int cast_end = declaration.indexOf(')', cidx + 1);
                    if (cast_end == -1) {
                        break; // invalid
                    }

                    // Type cast is declared; parse this type now
                    castType = new TypeDeclaration(resolver, declaration.substring(cidx + 1, cast_end));
                    if (!castType.isValid()) {
                        break; // invalid
                    }

                    // Continue onwards from past the declared type
                    cidx = cast_end;
                    continue;
                } else if (validNameChar) {
                    startIdx = cidx; 
                } else {
                    postfix = declaration.substring(cidx);
                    break; // not a valid start of the name
                }
            }

            // The first invalid character finishes the raw type declaration
            if (!validNameChar && rawType == null) {
                rawType = declaration.substringToString(startIdx, cidx);
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
                if (declaration.substring(cidx).startsWith("extends ")) {
                    // Var names are only allowed for names > 1 character long
                    // Otherwise, stop parsing to allow Class Declarations to handle extends instead
                    if (rawType.length() > 1) {
                        postfix = declaration.substring(cidx);
                        break;
                    }
                    typeVarName = rawType;
                    foundExtends = true;
                    rawType = null;
                    startIdx = -1;
                    cidx += 7;
                } else {
                    postfix = declaration.substring(cidx);
                    break;
                }
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
                this.isPrimitive = false;
                this.boxed = this;
                this.variableName = typeVarName;
                this.genericTypes = new TypeDeclaration[0];
                this.cast = castType;
            } else {
                this.setInvalid();
                this.typeName = "";
                this.typePath = "";
                this.type = null;
                this.isPrimitive = false;
                this.boxed = this;
                this.variableName = typeVarName;
                this.genericTypes = new TypeDeclaration[0];
                this.cast = castType;
            }
            return;
        }

        // No contents after raw type?
        if (rawType == null) {
            rawType = declaration.substringToString(startIdx);
            postfix = StringBuffer.EMPTY;
        }

        // enum - invalid
        if (rawType != null && rawType.equals("enum")) {
            this.setInvalid();
            this.typeName = "";
            this.typePath = "";
            this.type = null;
            this.variableName = typeVarName;
            this.isPrimitive = false;
            this.boxed = this;
            this.genericTypes = new TypeDeclaration[0];
            this.cast = castType;
            return;
        }

        // <T>
        if (rawType != null && rawType.length() == 1 && typeVarName == null) {
            typeVarName = rawType;
            rawType = "Object";

            // Figure out the upper bound of this generic type variable.
            // We can do so if it refers to a type variable of the declaring Class.
            // If this fails, we assume Object as fallback.
            if (resolver.getDeclaredClass() != null) {
                typeVariableLoop:
                for (TypeVariable<?> tVar : resolver.getDeclaredClass().getTypeParameters()) {
                    if (typeVarName.equals(tVar.getName())) {
                        for (Type bound : tVar.getBounds()) {
                            // Figure out, at runtime, what Class this is
                            TypeDeclaration tDec = fromType(resolver, bound);
                            if (tDec.isValid() && tDec.isResolved()) {
                                rawType = tDec.typePath;
                                break typeVariableLoop;
                            }
                        }
                    }
                }
            }
        }

        this.variableName = typeVarName;

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
                    this.isPrimitive = false;
                    this.boxed = this;
                    this.genericTypes = new TypeDeclaration[0];
                    this.cast = null;
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
            this.setPostfix(StringBuffer.EMPTY);
        } else {
            this.setPostfix(postfix.substring(arrayEnd));
        }

        // Resolve the raw type
        ClassResolver.ResolveResult resolveResult = resolver.resolve(rawType);
        this.cast = castType;
        this.type = resolveResult.classType;
        this.typePath = resolveResult.classPath;
        this.typeName = rawType;
        this.isPrimitive = (this.type != null) && this.type.isPrimitive();
        this.boxed = this.isPrimitive ? fromClass(BoxedType.getBoxedType(this.type)) : this;
    }

    private TypeDeclaration(TypeDeclaration mainType, TypeDeclaration[] genericTypes) {
        super(mainType.getResolver());
        this.cast = mainType.cast;
        this.isWildcard = mainType.isWildcard;
        this.typeName = mainType.typeName;
        this.typePath = mainType.typePath;
        this.type = mainType.type;
        this.isPrimitive = mainType.isPrimitive;
        this.boxed = mainType.isPrimitive ? mainType.boxed : this;
        this.variableName = mainType.variableName;
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
        return resolveSuperType(Resolver.getMeta(this.type).superType);
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
            addInterfaces(types);
            this.superTypes = types.toArray(new TypeDeclaration[types.size()]);
        }
        return this.superTypes;
    }

    private void addInterfaces(ArrayList<TypeDeclaration> types) {
        if (this.type != null) {
            for (TypeDeclaration iif : Resolver.getMeta(this.type).interfaces) {
                iif = resolveSuperType(iif);
                if (!types.contains(iif)) {
                    types.add(iif);
                    iif.addInterfaces(types);
                }
            }
        }
    }

    /**
     * Checks whether this type is an array type
     * 
     * @return True if this type is an array type
     */
    public boolean isArray() {
        //TODO: Also support unresolved types
        return this.type != null && this.type.isArray();
    }

    /**
     * Gets the cast type if a cast is set, otherwise return this type.
     * 
     * @return exposed type
     */
    public TypeDeclaration exposed() {
        return (cast == null) ? this : cast;
    }

    /**
     * Gets the component type if this type is an array. Returns null if this is not an array type.
     * 
     * @return array component type
     */
    public TypeDeclaration getComponentType() {
        //TODO: Also support unresolved types
        if (this.type != null && this.type.isArray()) {
            TypeDeclaration componentType = new TypeDeclaration(this.getResolver(), this.type.getComponentType());
            componentType = componentType.setGenericTypes(this.genericTypes);
            return componentType;
        }
        return null;
    }

    /**
     * If this is a {@link #isPrimitive Primitive} type, returns the Boxed version of that
     * type. For example, int -> Integer. If not, returns this type itself.
     *
     * @return Boxed type
     */
    public TypeDeclaration getBoxedType() {
        return boxed;
    }

    /**
     * Gets whether this Type has any typed generic variables in it, which
     * would allow multiple different types to be represented
     * 
     * @return True if this type has type variables
     */
    public boolean hasTypeVariables() {
        if (this.variableName != null) {
            return true;
        }
        if (this.genericTypes.length == 0) {
            return false;
        }
        for (int i = 0; i < this.genericTypes.length; i++) {
            if (this.genericTypes[i].hasTypeVariables()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Gets whether this type can be cast to the {@link #cast} type.
     * If no cast type is set, this method returns true.
     * 
     * @return True if this type can be cast to {@link #cast}
     */
    public boolean canDownCast() {
        return this.cast == null || this.cast.isAssignableFrom(this);
    }

    /**
     * Gets whether {@link #cast} can be cast to this type.
     * If no cast type is set, this method returns true.
     * 
     * @return True if {@link #cast} can be cast to this type
     */
    public boolean canUpCast() {
        return this.cast == null || this.isAssignableFrom(this.cast);
    }

    public boolean isAssignableFrom(Object value) {
        return value != null && this.type.isAssignableFrom(value.getClass());
    }

    public boolean isAssignableFrom(TypeDeclaration other) {
        return other != null && other.isInstanceOf(this);
    }

    public boolean isInstanceOf(Class<?> otherType) {
        return otherType != null && this.type != null && otherType.isAssignableFrom(this.type);
    }

    public boolean isInstanceOf(TypeDeclaration other) {
        // Make sure the class itself is assignable
        if (other == null || other.type == null || this.type == null || !other.type.isAssignableFrom(this.type)) {
            return false;
        }

        // List<String> instanceof List == true
        if (other.genericTypes.length == 0) {
            return true;
        }

        TypeDeclaration selfType = this.type.equals(other.type) ? this : this.castAsSuperType(other.type);
        if (selfType == null || other.genericTypes.length != selfType.genericTypes.length) {
            return false; // should never happen!
        }

        for (int i = 0; i < selfType.genericTypes.length; i++) {
            TypeDeclaration s = selfType.genericTypes[i];
            TypeDeclaration t = other.genericTypes[i];
            if (t.type == null) {
                return false; // unresolved.
            }
            if (t.isWildcard) {
                // ? extends TYPE
                if (!s.isInstanceOf(t)) {
                    return false;
                }
            } else if ((t.variableName != null) && (s.isInstanceOf(t))) {
                // T matches all other generic types
                continue;
            } else {
                // TYPE must be exactly the same
                if (!t.equals(s)) {
                    return false;
                }
            }
        }

        return true;
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
            return castAsSuperType(classType);
        }
        return null;
    }

    private TypeDeclaration castAsSuperType(Class<?> classType) {
        for (TypeDeclaration type : this.getSuperTypes()) {
            if (type.type.equals(classType)) {
                return type;
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

    private final TypeDeclaration resolveSuperType(TypeDeclaration superType) {
        if (superType == null) {
            return null;
        }
        if (superType.genericTypes.length > 0 && this.genericTypes.length > 0) {

            // Correct super type generic types to use the types declared in this type
            // For example, HashMap<Integer, ?> should turn into AbstractMap<Integer, ?>
            // Without this, it would turn into AbstractMap<K, V>
            TypeDeclaration[] newTypeParams = superType.genericTypes.clone();
            TypeVariable<?>[] params = this.type.getTypeParameters();
            if (params.length == this.genericTypes.length) {
                boolean sameParamTypeCount = (params.length == superType.genericTypes.length);
                for (int i = 0; i < superType.genericTypes.length; i++) {
                    String name = superType.genericTypes[i].variableName;
                    if (name == null) {
                        continue;
                    }
                    if (sameParamTypeCount && params[i].getName().equals(name)) {
                        // Correct assumption
                        newTypeParams[i] = this.genericTypes[i];
                    } else {
                        // Out of order or different parameter count, find it
                        for (int j = 0; j < params.length; j++) {
                            if (params[j].getName().equals(name)) {
                                newTypeParams[i] = this.genericTypes[j];
                                break;
                            }
                        }
                    }
                }
            }
            return superType.setGenericTypes(newTypeParams);
        }
        return superType;
    }

    @Override
    public double similarity(Declaration other) {
        if (!(other instanceof TypeDeclaration)) {
            return 0.0;
        }
        TypeDeclaration t = (TypeDeclaration) other;
        if (!t.isValid() || !this.isValid()) {
            return 0.0;
        }

        double mainTypeSimilarity;
        if (!t.isResolved() || !this.isResolved()) {
            // All we can do is compare the type names
            mainTypeSimilarity = MountiplexUtil.similarity(this.typePath, t.typePath);
        } else if (this.type.equals(t.type)) {
            // Types are exactly equal
            mainTypeSimilarity = 1.0;
        } else {
            Class<?> t1 = BoxedType.tryBoxType(this.type);
            Class<?> t2 = BoxedType.tryBoxType(t.type);
            if (t1.isAssignableFrom(t2)) {
                // t2 extends t1. Check how many layers down.
                int n = 1;
                Class<?> s = t2;
                while ((s = s.getSuperclass()) != null && !s.equals(t1)) {
                    n++;
                }
                mainTypeSimilarity = 1.0 / (double) n;
            } else if (t2.isAssignableFrom(t1)) {
                // t1 extends t2. Check how many layers down.
                int n = 1;
                Class<?> s = t1;
                while ((s = s.getSuperclass()) != null && !s.equals(t2)) {
                    n++;
                }
                mainTypeSimilarity = 1.0 / (double) n;
            } else {
                // The types may share the same base class (exempt Object)
                // they may also share certain interfaces. Each adds to the similarity.
                Collection<Class<?>> t1s = ReflectionUtil.getAllClassesAndInterfaces(t1)
                        .filter(c -> c != Object.class)
                        .collect(Collectors.toCollection(ArrayList::new));
                Collection<Class<?>> t2s = ReflectionUtil.getAllClassesAndInterfaces(t2)
                        .filter(c -> c != Object.class)
                        .collect(Collectors.toCollection(ArrayList::new));

                long numberOverlap = t1s.stream().filter(t2s::contains).count();
                if (numberOverlap == 0) {
                    // Two very standalone classes. No similarity, at all.
                    mainTypeSimilarity = 0.0;
                } else {
                    // Amount of classes overlap / total defines similarity
                    long totalSuperTypes = Stream.concat(t1s.stream(), t2s.stream()).distinct().count();
                    mainTypeSimilarity = (double) numberOverlap / (double) totalSuperTypes;
                }
            }
        }

        // Handle generic types
        double genericSimilarity;
        if (this.genericTypes.length > 0 && t.genericTypes.length > 0) {
            if (this.genericTypes.length == t.genericTypes.length) {
                // Compare generic types
                genericSimilarity = 0.0;
                for (int i = 0; i < this.genericTypes.length; i++) {
                    genericSimilarity += this.genericTypes[i].similarity(t.genericTypes[i]);
                }
                genericSimilarity /= this.genericTypes.length;
            } else {
                // Different number of generic types for each - dissimilar
                genericSimilarity = 0.0;
            }
        } else if (this.genericTypes.length == 0 && t.genericTypes.length == 0) {
            // No generic types are used - 100% similar
            genericSimilarity = 1.0;
        } else {
            // One type is generic, the other is a raw type. No real way to identify.
            // Use a constant 0.5
            genericSimilarity = 0.5;
        }

        // Final result is 75% main type, 25% generic type
        return (0.75 * mainTypeSimilarity) + (0.25 * genericSimilarity);
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

        // Ignore rawtype assignment: List<Integer> = List
        if (this.genericTypes.length != 0 && type.genericTypes.length != 0) {
            if (this.genericTypes.length != type.genericTypes.length) return false;
            for (int i = 0; i < this.genericTypes.length; i++) {
                if (!this.genericTypes[i].match(type.genericTypes[i])) return false;
            }
        }

        return true;
    }

    @Override
    public final String toString(boolean identity) {
        if (!isValid()) {
            return "??[" + _initialDeclaration + "]??";
        }
        String typeInfo = identity ? ((this.type == null) ? this.typePath : MPLType.getName(this.type)) : this.typeName;
        int arrIdx = typeInfo.indexOf('[');
        String arrPart = "";
        if (arrIdx != -1) {
            arrPart = typeInfo.substring(arrIdx);
            typeInfo = typeInfo.substring(0, arrIdx);
        }
        if (this.type == null) {
            typeInfo = "??" + typeInfo + "??";
        }

        String str = "";
        if (this.cast != null && !identity) {
            str += "(" + this.cast.toString(identity) + ") ";
        }
        if (this.isWildcard) {
            if (typeInfo.length() == 0 || (this.type == Object.class)) {
                str += "?";
            } else {
                str += "? extends " + typeInfo;
            }
        } else if (this.variableName != null) {
            if (typeInfo.length() == 0 || (this.type == Object.class)) {
                str += this.variableName;
            } else {
                str += this.variableName + " extends " + typeInfo;
            }
        } else {
            str += typeInfo;
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
                str += genericType.toString(identity);
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
        if (this.cast != null && !this.cast.isResolved()) {
            return false;
        }
        for (int i = 0; i < genericTypes.length; i++) {
            if (!genericTypes[i].isResolved()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Gets whether this Type is a builtin Java type that does not require any imports.
     * Examples are primitives and their array counterparts.
     * 
     * @return True if built-in, False if not
     */
    public boolean isBuiltin() {
        return isBuiltin(this.type);
    }

    private static boolean isBuiltin(Class<?> type) {
        if (type != null) {
            if (type.isPrimitive()) {
                return true;
            }
            if (type.isArray()) {
                return isBuiltin(type.getComponentType());
            }
            String path = type.getName();
            if (path.startsWith("java.lang.")) {
                return true;
            }
        }
        return false;
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
     * @param classType to turn into a Type Declaration.
     *        Using null with yield {@link #INVALID}
     * @return Type Declaration
     */
    public static TypeDeclaration fromClass(Class<?> classType) {
        return Resolver.getMeta(classType).typeDec;
    }

    /**
     * Creates a generic type declaration from a base Class type, and a number of generic enclosing types
     * 
     * @param baseType
     * @param genericTypes
     * @return generic type declaration
     */
    public static TypeDeclaration createGeneric(Class<?> baseType, Class<?>... genericTypes) {
        TypeDeclaration[] gen = new TypeDeclaration[genericTypes.length];
        for (int i = 0; i < gen.length; i++) {
            gen[i] = TypeDeclaration.fromClass(genericTypes[i]);
        }
        return createGeneric(baseType, gen);
    }

    /**
     * Creates a generic type declaration from a base Class type, and a number of generic enclosing types
     * 
     * @param baseType
     * @param genericTypes
     * @return generic type declaration
     */
    public static TypeDeclaration createGeneric(Class<?> baseType, TypeDeclaration... genericTypes) {
        return TypeDeclaration.fromClass(baseType).setGenericTypes(genericTypes);
    }

    /**
     * Creates a type declaration for an array type of the component type specified
     * 
     * @param componentType of the array
     * @return array type declaration
     */
    public static TypeDeclaration createArray(Class<?> componentType) {
        return TypeDeclaration.fromClass(MountiplexUtil.getArrayType(componentType));
    }

    /**
     * Creates a type declaration for an array type of the component type specified
     * 
     * @param componentType of the array
     * @return array type declaration
     */
    public static TypeDeclaration createArray(TypeDeclaration componentType) {
        TypeDeclaration result = createArray(componentType.type);
        result = result.setGenericTypes(componentType.genericTypes);
        return result;
    }

    /**
     * Creates a Type Declaration by inspecting a type, resolving using a set Class Resolver
     * 
     * @param classResolver to use
     * @param type to use for initialization
     * @return Type Declaration
     */
    public static TypeDeclaration fromType(ClassResolver classResolver, Type type) {
        return new TypeDeclaration(classResolver, type);
    }

    /**
     * Creates a Type Declaration by inspecting a type, caching the result if possible
     * 
     * @param type to retrieve
     * @return Type Declaration for the type
     */
    public static TypeDeclaration fromType(Type type) {
        if (type instanceof Class) {
            return fromClass((Class<?>) type);
        } else {
            return new TypeDeclaration(ClassResolver.DEFAULT, type);
        }
    }

    /**
     * Parses a Type Declaration using the default Class Resolver
     * 
     * @param declaration to parse
     * @return Type Declaration
     */
    public static TypeDeclaration parse(StringBuffer declaration) {
        return new TypeDeclaration(ClassResolver.DEFAULT, declaration);
    }

    /**
     * Parses a Type Declaration using a Class Resolver
     * 
     * @param classResolver to use
     * @param declaration to parse
     * @return Type Declaration
     */
    public static TypeDeclaration parse(ClassResolver classResolver, StringBuffer declaration) {
        return new TypeDeclaration(classResolver, declaration);
    }

    /**
     * Parses a Type Declaration using the default Class Resolver
     * 
     * @param declaration to parse
     * @return Type Declaration
     */
    public static TypeDeclaration parse(String declaration) {
        return new TypeDeclaration(ClassResolver.DEFAULT, StringBuffer.of(declaration));
    }

    /**
     * Parses a Type Declaration using a Class Resolver
     * 
     * @param classResolver to use
     * @param declaration to parse
     * @return Type Declaration
     */
    public static TypeDeclaration parse(ClassResolver classResolver, String declaration) {
        return new TypeDeclaration(classResolver, StringBuffer.of(declaration));
    }
}
