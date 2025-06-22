package com.bergerkiller.mountiplex.reflection.declarations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

import com.bergerkiller.mountiplex.MountiplexUtil;
import com.bergerkiller.mountiplex.logic.TextValueSequence;
import com.bergerkiller.mountiplex.reflection.resolver.ClassDeclarationResolver;
import com.bergerkiller.mountiplex.reflection.resolver.Resolver;
import com.bergerkiller.mountiplex.reflection.util.asm.MPLType;
import com.bergerkiller.mountiplex.reflection.util.fast.GeneratedCodeInvoker;

/**
 * Resolves class names into Class Types based on package and import paths.
 * Also includes a lot of metadata parsed from templates, such as requirement
 * macros.
 */
public class ClassResolver {
    private static final List<String> default_imports = Arrays.asList("java.lang.*", "java.util.*");
    private static final Runnable[] default_bootstrap = new Runnable[0];
    public static final ClassResolver DEFAULT = new ClassResolver().immutable();

    private final ArrayList<String> imports;
    private final ArrayList<String> manualImports;
    private final List<Requirement> requirements;
    private final Remapping.Lookup remappings;
    private VariablesMap variables;
    private String classDeclarationResolverName;
    private String packagePath;
    private String declaredClassName;
    private Class<?> declaredClass;
    private ClassLoader classLoader;
    private boolean logErrors;
    private boolean isGenerating;
    private Runnable[] bootstrap;

    private ClassResolver(ClassResolver src) {
        this.classDeclarationResolverName = src.classDeclarationResolverName;
        this.variables = src.variables;
        this.imports = new ArrayList<String>(src.imports);
        this.manualImports = new ArrayList<String>(src.manualImports);
        this.requirements = new ArrayList<Requirement>(src.requirements);
        this.remappings = src.remappings.clone();
        this.packagePath = src.packagePath;
        this.declaredClassName = src.declaredClassName;
        this.declaredClass = src.declaredClass;
        this.classLoader = src.classLoader;
        this.logErrors = src.logErrors;
        this.isGenerating = src.isGenerating;
        this.bootstrap = src.bootstrap;
    }

    public ClassResolver() {
        this.classDeclarationResolverName = "null";
        this.variables = VariablesMap.EMPTY;
        this.imports = new ArrayList<String>();
        this.manualImports = new ArrayList<String>();
        this.requirements = new ArrayList<Requirement>();
        this.remappings = Remapping.createLookup();
        this.packagePath = "";
        this.declaredClassName = null;
        this.declaredClass = null;
        this.classLoader = ClassResolver.class.getClassLoader();
        this.logErrors = true;
        this.isGenerating = false;
        this.bootstrap = default_bootstrap;
        this.regenImports();
    }

    public ClassResolver(String packagePath) {
        this.classDeclarationResolverName = "null";
        this.variables = VariablesMap.EMPTY;
        this.imports = new ArrayList<String>();
        this.manualImports = new ArrayList<String>();
        this.requirements = new ArrayList<Requirement>();
        this.remappings = Remapping.createLookup();
        this.packagePath = "";
        this.logErrors = true;
        this.isGenerating = false;
        this.bootstrap = default_bootstrap;
        this.declaredClassName = null;
        this.declaredClass = null;
        this.classLoader = ClassResolver.class.getClassLoader();
        this.setPackage(packagePath);
    }

    /**
     * Gets whether errors during resolving of declarations are logged
     * 
     * @return True if declaration resolving errors are logged
     */
    public boolean getLogErrors() {
        return this.logErrors;
    }

    /**
     * Sets whether errors during resolving of declarations are logged
     * 
     * @param log option
     */
    public void setLogErrors(boolean log) {
        this.logErrors = log;
    }

    /**
     * Gets whether this resolver is used while generating the template source code.
     * In that case, certain parsing operations can be skipped.
     * 
     * @return generating
     */
    public boolean isGenerating() {
        return this.isGenerating;
    }

    /**
     * Sets whether this resolver is used while generating the template source code.
     * In that case, certain parsing operations can be skipped.
     * 
     * @param generating
     */
    public void setGenerating(boolean generating) {
        this.isGenerating = generating;
    }

    /**
     * Clones this ClassResolver so that independent Class imports can be included
     */
    @Override
    public ClassResolver clone() {
        return new ClassResolver(this);
    }

    /**
     * Clones this ClassResolver into an immutable version
     */
    public ClassResolver immutable() {
        return new ImmutableClassResolver(this);
    }

    /**
     * Sets the Class Loader used to load new class types by name.
     * By default uses the same class loader that loaded this library.
     * 
     * @param loader
     */
    public void setClassLoader(ClassLoader loader) {
        this.classLoader = loader;
    }

    public ClassLoader getClassLoader() {
        return this.classLoader;
    }

    public void setDeclaredClassName(String typeName) {
        ResolveResult result = this.resolve(typeName);
        this.setDeclaredClass(result.classType, result.classPath);
    }

    /**
     * Sets a declared class, from which other subclasses and classes in the same package can be found
     * 
     * @param type to set as the declared class
     */
    public void setDeclaredClass(Class<?> type) {
        if (type != null) {
            setDeclaredClass(type, MPLType.getName(type));
        }
    }

    /**
     * Sets a declared class, from which other subclasses and classes in the same package can be found
     * 
     * @param type to set as the declared class
     * @param typeName the name of the class, which makes up the package path information
     */
    public void setDeclaredClass(Class<?> type, String typeName) {
        //Package pkg = type.getPackage();
        //if (pkg != null) {
        //    this.packagePath = pkg.getName();
        //}

        this.declaredClass = type;
        this.declaredClassName = typeName;

        // Compute package path from the type namee
        // We rely on people not being idiots with the class names
        String packageName = MountiplexUtil.getPackagePathFromClassPath(typeName);
        if (!packageName.isEmpty()) {
            this.packagePath = packageName;
        }

        this.regenImports();
    }

    /**
     * Gets the declared class
     * 
     * @return declared class
     */
    public Class<?> getDeclaredClass() {
        return this.declaredClass;
    }

    /**
     * Gets the declared class name. If the class does not exist,
     * and it was set using setDeclaredClassName, it still returns the name.
     * 
     * @return declared class name
     */
    public String getDeclaredClassName() {
        return this.declaredClassName;
    }

    /**
     * Adds a package path, making all Classes within visible.
     * Original imports and declared class info prior to setting the package are lost.
     * 
     * @param path to the package to set
     */
    public void setPackage(String path) {
        setPackage(path, true);
    }

    /**
     * Adds a package path, making all Classes within visible.
     * Original imports and declared class info prior to setting the package are lost
     * when reset is true.
     * 
     * @param path to the package to set
     * @param reset whether to reset declared class and imports
     */
    public void setPackage(String path, boolean reset) {
        this.packagePath = path;
        if (reset) {
            this.declaredClass = null;
            this.declaredClassName = null;
            this.manualImports.clear();
        }
        this.regenImports();
    }

    /**
     * Gets the package path last set using {@link #setPackage(String)}.
     * Is empty if no package path was set
     * 
     * @return package path
     */
    public String getPackage() {
        return this.packagePath;
    }

    /**
     * Whether a package level import was defined
     * 
     * @return True if a package path is set
     */
    public boolean hasPackage() {
        return !this.packagePath.isEmpty();
    }

    /**
     * Sets the full name or method used to obtain the class declaration resolver that
     * loads the declaration for the classes managed by this resolver.
     * This is used internally to initialize the templates for the first time.
     * 
     * @param name class declaration resolver name
     */
    public void setClassDeclarationResolverName(String name) {
        this.classDeclarationResolverName = name;
    }

    /**
     * Gets the full name or method used to obtain the class declaration resolver that
     * loads the declaration for the classes managed by this resolver.
     * This is used internally to initialize the templates for the first time.
     * 
     * @return class declaration resolver name
     */
    public String getClassDeclarationResolverName() {
        return this.classDeclarationResolverName;
    }

    /**
     * Gets a list of runnables that should be executed prior to using any portions of this Class
     * 
     * @return bootstrap runnable list
     */
    public List<Runnable> getBootstrap() {
        return Arrays.asList(this.bootstrap);
    }

    public void runBootstrap() {
        for (int i = 0; i < this.bootstrap.length; i++) {
            try {
                this.bootstrap[i].run();
            } catch (Throwable t) {
                throw new BootstrapException(this.bootstrap[i], t);
            }
        }
    }

    /**
     * Adds a runnable to be executed prior to using any portions of this Class.
     * The code is only going to be ever executed once
     * 
     * @param runnable to add
     */
    public void addBootstrap(Runnable runnable) {
        this.bootstrap = Arrays.copyOf(this.bootstrap, this.bootstrap.length + 1);
        this.bootstrap[this.bootstrap.length - 1] = new BootstrapRunnable(runnable);
    }

    /**
     * Adds code to be executed prior to using any portions of this Class.
     * The code is compiled at runtime when the code is meant to be called.
     * 
     * @param code to add
     */
    public void addBootstrap(String code) {
        this.bootstrap = Arrays.copyOf(this.bootstrap, this.bootstrap.length + 1);
        this.bootstrap[this.bootstrap.length - 1] = new BootstrapCode(this, code);
    }

    /**
     * Gets a stream of imports added to this resolver using {@link #addImport(String)},
     * including default imports.
     *
     * @return List of imports
     */
    public Stream<String> getAllImports() {
        return Stream.concat(this.manualImports.stream(), default_imports.stream());
    }

    /**
     * Adds an import declaration. This method supports wildcard imports.
     * 
     * @param path to import
     */
    public void addImport(String path) {
        this.manualImports.add(path);
        this.regenImports();
    }

    /**
     * Adds multiple import declarations. This method supports wildcard imports.
     * 
     * @param paths to import
     */
    public void addImports(Collection<String> paths) {
        this.manualImports.addAll(paths);
        this.regenImports();
    }

    /**
     * Saves all set variables to a String declaration, used for parsing template sources
     * 
     * @return variables declaration
     */
    public String saveDeclaration() {
        return this.variables.getDeclaration();
    }

    /**
     * Sets an environment variable used during parsing of template sources
     * 
     * @param name variable name
     * @param value variable value
     */
    public void setVariable(String name, String value) {
        this.variables = this.variables.modify(m -> m.put(name, value));
    }

    /**
     * Gets the value of an environment variable, returns def if not found.
     * 
     * @param name variable name
     * @param def default value
     * @return variable value, def if not found
     */
    public String getVariable(String name, String def) {
        String value = this.variables.get(name);
        return (value != null) ? value : def;
    }

    /**
     * Includes all the class resolver details that are inherited/included when using
     * the #include macro to add another source file. Some stuff, like imports and
     * class name, are not included. Other things, like remapping rules and requirement macros,
     * are.<br>
     * <br>
     * This method assumes that the class resolver provided was already derived from this one.
     * That is, the original requirements and such that are already defined in this resolver,
     * should also be defined in the one specified.
     *
     * @param from ClassResolver to copy source details from
     */
    public void includeSourceDetails(ClassResolver from) {
        this.remappings.assign(from.remappings); // Assumes from is derived from this one
        this.requirements.clear();
        this.requirements.addAll(from.requirements);
        this.variables = from.variables;
        this.bootstrap = from.bootstrap;
    }

    /**
     * Sets multiple environment variables in one go, which are
     * used during parsing of template sources
     * 
     * @param variables The variables to add
     */
    public void setAllVariables(Map<String, String> variables) {
        this.variables = this.variables.modify(m -> m.putAll(variables));
    }

    /**
     * Sets multiple environment variables in one go, which are
     * used during parsing of template sources. The variables are loaded
     * using a ClassDeclarationResolver. This resolver must have a valid declared
     * Class set.
     * 
     * @param resolver The resolver to ask for variables
     */
    public void setAllVariables(ClassDeclarationResolver resolver) {
        if (resolver == null) {
            throw new IllegalArgumentException("Resolver cannot be null");
        }
        if (this.declaredClassName == null || this.declaredClassName.isEmpty() || this.declaredClass == null) {
            throw new IllegalStateException("Class Resolver has no declared Class");
        }
        this.variables = this.variables.modify(m -> {
            resolver.resolveClassVariables(this.declaredClassName, this.declaredClass, m);
        });

        // Load remappings as well
        {
            ClassResolver classResolver = resolver.getRootClassResolver(this.declaredClassName, this.declaredClass);
            for (Remapping remapping : classResolver.getRemappings().getAllStoredRemappings()) {
                this.remappings.addRemapping(remapping);
            }
        }
    }

    /**
     * Gets a key-value map of all variables currently set
     * 
     * @return all variables mapping, unmodifiable
     */
    public Map<String, String> getAllVariables() {
        return this.variables.getAll();
    }

    /**
     * Evaluates a simple logical expression using the variables set
     *
     * @param expression to evaluate
     * @return True if the expression evaluates as True, False if not
     */
    public boolean evaluateExpression(String expression) {
        // Find instances of && or || in the String, and separate each expression by these
        // We evaluate left to right, which means this statement:
        // true && false || true
        // evaluates to true, because true && false = false -> false || true = true
        boolean prevExpressionResult = false;
        int prevExpressionStart = 0;
        boolean compareAnd = false;
        for (int cIdx = 0; cIdx < expression.length()-1; cIdx++) {
            char c = expression.charAt(cIdx);

            boolean nextCompareAnd;
            if (c == '&' && expression.charAt(cIdx+1) == '&') {
                nextCompareAnd = true;
            } else if (c == '|' && expression.charAt(cIdx+1) == '|') {
                nextCompareAnd = false;
            } else {
                continue;
            }

            // Evaluate previous expression
            if (compareAnd) {
                prevExpressionResult &= evaluateExpression_part(expression.substring(prevExpressionStart, cIdx));
            } else {
                prevExpressionResult |= evaluateExpression_part(expression.substring(prevExpressionStart, cIdx));
            }

            // Reset to the next expression
            cIdx++; // skip second & or |
            compareAnd = nextCompareAnd;
            prevExpressionStart = cIdx + 1;
        }

        // Trailing part
        if (compareAnd) {
            return prevExpressionResult && evaluateExpression_part(expression.substring(prevExpressionStart));
        } else {
            return prevExpressionResult || evaluateExpression_part(expression.substring(prevExpressionStart));
        }
    }

    private boolean evaluateExpression_part(String expression) {
        // =============== Tokenize the expression ================
        expression = expression.trim();
        String varName = null;
        for (int cIdx = 0; cIdx < expression.length(); cIdx++) {
            char c = expression.charAt(cIdx);
            if (c == ' ' || (!Character.isLetter(c) && c != '_' && c != '!')) {
                varName = expression.substring(0, cIdx);
                expression = expression.substring(cIdx).trim();
                break;
            }
        }
        if (varName == null) {
            varName = expression;
            expression = "";
        }

        boolean inverted = false;
        while (varName.startsWith("!")) {
            inverted = !inverted;
            varName = varName.substring(1);
        }

        if (varName.equals("classexists") || // legacy
            varName.equals("methodexists") || // legacy
            varName.equals("fieldexists") || // legacy
            varName.equals("exists"))
        {
            int signatureStart = expression.indexOf(' ');
            String classPath;
            if (signatureStart == -1) {
                classPath = expression;
                signatureStart = expression.length();
            } else {
                classPath = expression.substring(0, signatureStart);
                signatureStart++;
            }
            while (classPath.endsWith(";")) {
                classPath = classPath.substring(0, classPath.length() - 1);
            }

            // Find class that the object is declared in
            Class<?> declaredClass = Resolver.loadClass(classPath, false, this.classLoader);
            if (declaredClass == null) {
                return inverted; // Class not available
            }

            // Rest is method/field signature
            String signatureStr = expression.substring(signatureStart).trim();
            if (signatureStr.isEmpty()) {
                return !inverted; // Only Class is asked
            }

            // Parse the signature
            ClassResolver resolver = new ClassResolver(this);
            resolver.setDeclaredClass(declaredClass, classPath);
            resolver.setLogErrors(false);
            Declaration declaration = Declaration.parseDeclaration(resolver, signatureStr);
            if (declaration == null) {
                if (this.getLogErrors()) {
                    MountiplexUtil.LOGGER.warning("Failed to parse declaration to check existance: " + signatureStr);
                    return inverted;
                }
            }

            // If signature itself could not be resolved, it simply doesn't exist
            if (!declaration.isResolved()) {
                return inverted;
            }

            // Find the declaration's actual method/field/constructor to check it exists
            return (declaration.discover() != null) != inverted;
        }

        if (varName.equals("assignable")) {
            int secondClassStart = expression.indexOf(' ');
            String firstClassPath, secondClassPath;
            if (secondClassStart == -1) {
                firstClassPath = expression;
                secondClassPath = Object.class.getName();
            } else {
                firstClassPath = expression.substring(0, secondClassStart);
                secondClassPath = expression.substring(secondClassStart + 1).trim();
            }

            firstClassPath = this.resolvePath(firstClassPath);
            secondClassPath = this.resolvePath(secondClassPath);

            Class<?> firstClass = Resolver.loadClass(firstClassPath, false, this.classLoader);
            Class<?> secondClass = Resolver.loadClass(secondClassPath, false, this.classLoader);
            if (firstClass == null || secondClass == null) {
                return !inverted; // Either class not available
            }

            // Check assignable
            return firstClass.isAssignableFrom(secondClass) != inverted;
        }

        String value1 = this.variables.get(varName);
        if (value1 == null) {
            // Edge cases: true/false constants
            if (varName.equals("1") || varName.equalsIgnoreCase("true")) {
                return !inverted;
            }
            return inverted; // variable not found; never evaluates to True
        }
        int logicEndIdx = expression.indexOf(' ');
        if (logicEndIdx == -1) {
            // #if <varname> simply checks if the variable exists
            return !inverted;
        }
        String operand = expression.substring(0, logicEndIdx);
        String value2 = expression.substring(logicEndIdx + 1).trim();
        return TextValueSequence.evaluateText(value1, operand, value2) != inverted;
    }

    /**
     * Resolves a class name to an assumed full class path.
     * If the class could not be found, one is assumed in the package path.
     * 
     * @param name of the class (generic names not supported)
     * @return resolved class path (never fails)
     */
    public String resolvePath(String name) {
        return resolve(name).classPath;
    }

    /**
     * Resolves a class name to a class. Returns null if the name
     * could not be resolved to an existing Class type.
     * 
     * @param name of the class (generic names not supported)
     * @return resolved class (null if not found)
     */
    public Class<?> resolveClass(String name) {
        return resolve(name).classType;
    }

    /**
     * Resolves a class name to a class and class path.
     * 
     * @param name of the class (generic names not supported)
     * @return resolve result, which always has a name, but may not have a class type
     */
    public ResolveResult resolve(String name) {
        // Return Object for generic typings (T, K, etc.)
        if (name.length() == 1) {
            return new ResolveResult(name, Object.class);
        }

        // Array types
        if (name.endsWith("[]")) {
            int arrayLevels = 0;
            do {
                arrayLevels++;
                name = name.substring(0, name.length() - 2);
            } while (name.endsWith("[]"));

            // Resolve the component
            ResolveResult componentResult = resolve(name);

            // Generate new class path with same number of array levels
            StringBuilder newPath = new StringBuilder(componentResult.classPath);
            for (int i = 0; i < arrayLevels; i++) {
                newPath.append("[]");
            }

            // Turn into ResolveResult
            if (componentResult.classType != null) {
                return new ResolveResult(newPath.toString(), MountiplexUtil.getArrayType(componentResult.classType, arrayLevels));
            } else {
                return new ResolveResult(newPath.toString(), null) ;
            }
        }

        // Directly by name
        Class<?> byAbsoluteName = Resolver.loadClass(name, false, this.classLoader);
        if (byAbsoluteName != null) {
            return new ResolveResult(name, byAbsoluteName);
        }

        // Try imports
        String classPath;
        String bestImport = null;
        String dotName = "." + name;
        for (String imp : this.imports) {
            if (imp.endsWith(".*") || imp.endsWith("$*")) {
                classPath = imp.substring(0, imp.length() - 1) + name;
            } else if (imp.endsWith(dotName)) {
                classPath = imp;
                bestImport = imp;
            } else {
                continue;
            }

            Class<?> byImport = Resolver.loadClass(classPath, false, this.classLoader);
            if (byImport != null) {
                return new ResolveResult(classPath, byImport);
            }
        }

        // Try package path
        if (!packagePath.isEmpty() && !(Character.isLowerCase(name.charAt(0)) && name.contains("."))) {
            classPath = packagePath + "." + name;
            Class<?> byPackage = Resolver.loadClass(classPath, false, this.classLoader);
            if (byPackage != null) {
                return new ResolveResult(classPath, byPackage);
            }
        } else {
            classPath = name;
        }

        // If it could not be loaded here, and we found a matching import, prefer that
        // This makes sure that during code generation nothing goes wrong
        if (bestImport != null) {
            classPath = bestImport;
        }

        // Failed
        return new ResolveResult(classPath, null);
    }

    /**
     * Resolves the path of a Class type
     * 
     * @param type to resolve
     * @return class path
     */
    public String resolvePath(Class<?> type) {
        if (type.isArray()) {
            return resolvePath(type.getComponentType()) + "[]";
        } else {
            return MPLType.getName(type).replace('$', '.');
        }
    }

    /**
     * Resolves the name of a Class type when resolved by this resolver
     * 
     * @param type to resolve
     * @return class name
     */
    public String resolveName(Class<?> type) {
        // Null types shouldn't happen, but security and all
        if (type == null) {
            return "NULL";
        }

        // Handle arrays elegantly
        if (type.isArray()) {
            return resolveName(type.getComponentType()) + "[]";
        }

        // See if the class type was imported
        String name = MPLType.getName(type).replace('$', '.');
        for (String imp : this.imports) {
            if (equalsIgnoreDollarSign(imp, name)) {
                return type.getSimpleName();
            }
            if (imp.endsWith(".*") || imp.endsWith("$*")) {
                int imp_len = imp.length() - 1;
                if (name.length() >= imp_len && equalsIgnoreDollarSign(name, imp, imp_len)) {
                    return name.substring(imp_len);
                }
            }
        }
        return name;
    }

    private static boolean equalsIgnoreDollarSign(String a, String b) {
        int len = a.length();
        return len == b.length() && equalsIgnoreDollarSign(a, b, len);
    }

    private static boolean equalsIgnoreDollarSign(String a, String b, int len) {
        for (int i = 0; i < len; i++) {
            char ca = a.charAt(i);
            char cb = b.charAt(i);
            if (ca != cb && ((ca != '.' && ca != '$') || (cb != '.' && cb != '$'))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Stores a requirement parsed from a #require statement.
     * It can later be found again when resolving requirements in generated code.
     * 
     * @param declaration
     */
    public void storeRequirement(Requirement declaration) {
        this.requirements.add(0, declaration);
    }

    /**
     * Gets a list of requirements parsed from #require statements.
     * 
     * @return requirements
     */
    public List<Requirement> getRequirements() {
        return this.requirements;
    }

    /**
     * Stores a method or field name remapping parsed from a #remap statement.
     * It can later be used again when resolving fields/methods inside method bodies
     * and declarations.
     *
     * @param remapping Field or Method Remapping to store which will be made available
     *                  to all declarations parsed using this resolver from now on
     */
    public void storeRemapping(Remapping remapping) {
        this.remappings.addRemapping(remapping);
    }

    /**
     * Gets all remapping information tracked by this Class Resolver. This can be used
     * to find the proper method or field name by the names encountered in the declarations
     * or method bodies.
     *
     * @return Remapping lookup
     */
    public Remapping.Lookup getRemappings() {
        return remappings;
    }

    private void regenImports() {
        this.imports.clear();
        this.imports.addAll(this.manualImports);
        Collections.reverse(this.imports);
        if (this.declaredClassName != null) {
            this.imports.add(this.declaredClassName + "$*");
        }
        if (this.packagePath != null && !this.packagePath.isEmpty()) {
            this.imports.add(this.packagePath + ".*");
        }
        this.imports.addAll(default_imports);
    }

    /**
     * Stores the result of resolving a name into a Class Type and Class Path at runtime
     */
    public static class ResolveResult {
        /** Best-matching Class Path deduced using the resolver environment */
        public final String classPath;
        /** Found Class Type for Class Path, null if the Class could not be found */
        public final Class<?> classType;

        public ResolveResult(String classPath, Class<?> classType) {
            this.classPath = classPath;
            this.classType = classType;
        }

        /**
         * Gets whether resolving was successful, and a Class could be found
         * 
         * @return True if successful
         */
        public boolean success() {
            return classType != null;
        }
    }

    private static class ImmutableClassResolver extends ClassResolver {

        public ImmutableClassResolver(ClassResolver resolver) {
            super(resolver);
        }

        @Override
        public void setDeclaredClass(Class<?> type) {
            throw new UnsupportedOperationException("Class Resolver is immutable");
        }

        @Override
        public void setPackage(String path) {
            throw new UnsupportedOperationException("Class Resolver is immutable");
        }

        @Override
        public void addImport(String path) {
            throw new UnsupportedOperationException("Class Resolver is immutable");
        }

        @Override
        public void setVariable(String name, String value) {
            throw new UnsupportedOperationException("Class Resolver is immutable");
        }

        @Override
        public void storeRequirement(Requirement declaration) {
            throw new UnsupportedOperationException("Class Resolver is immutable");
        }

        @Override
        public void storeRemapping(Remapping remapping) {
            throw new UnsupportedOperationException("Class Resolver is immutable");
        }
    }

    private static class BootstrapRunnable implements Runnable {
        private final Runnable runnable;
        private boolean needsExecuting = true;

        public BootstrapRunnable(Runnable runnable) {
            this.runnable = runnable;
        }

        @Override
        public synchronized void run() {
            if (needsExecuting) {
                runnable.run();
                needsExecuting = false;
            }
        }

        @Override
        public String toString() {
            return "RUNNABLE{" + this.runnable + "}";
        }
    }

    private static class BootstrapCode implements Runnable {
        private final ClassResolver resolver;
        private final String code;
        private boolean needsExecuting = true;

        public BootstrapCode(ClassResolver resolver, String code) {
            this.resolver = resolver;
            this.code = code;
        }

        @Override
        public void run() {
            if (needsExecuting) {
                MethodDeclaration decl = new MethodDeclaration(resolver, "public static void run() {\n" + code + "\n}");
                GeneratedCodeInvoker<Void> invoker = GeneratedCodeInvoker.create(decl);
                invoker.invoke(null);
                needsExecuting = false;
            }
        }

        @Override
        public String toString() {
            return "CODE" + this.code;
        }
    }

    private static class BootstrapException extends RuntimeException {
        private static final long serialVersionUID = -1827332615527781142L;

        public BootstrapException(Runnable runnable, Throwable cause) {
            super("Failed to bootstrap " + runnable, cause);
        }
    }

    private static class VariablesMap {
        private final Map<String, String> _map;
        private String _decl;
        public static final VariablesMap EMPTY = new VariablesMap();

        private VariablesMap() {
            this._map = Collections.emptyMap();
            this._decl = "";
        }

        public VariablesMap(Map<String, String> map) {
            this._map = map;
            this._decl = null;
        }

        public Map<String, String> getAll() {
            return Collections.unmodifiableMap(this._map);
        }

        public String get(String key) {
            return this._map.get(key);
        }

        public VariablesMap modify(Consumer<Map<String, String>> modifier) {
            Map<String, String> new_map = new HashMap<String, String>(this._map);
            modifier.accept(new_map);
            return new VariablesMap(new_map);
        }

        public String getDeclaration() {
            if (this._decl == null) {
                StringBuilder result = new StringBuilder();
                for (Map.Entry<String, String> entry : this._map.entrySet()) {
                    result.append("#set ").append(entry.getKey()).append(' ').append(entry.getValue()).append('\n');
                }
                this._decl = result.toString();
            }
            return this._decl;
        }

    }
}
