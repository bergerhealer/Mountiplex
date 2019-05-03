package com.bergerkiller.mountiplex.reflection.declarations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.bergerkiller.mountiplex.MountiplexUtil;
import com.bergerkiller.mountiplex.reflection.resolver.Resolver;
import com.bergerkiller.mountiplex.reflection.util.fast.GeneratedCodeInvoker;

/**
 * Resolves class names into Class Types based on package and import paths.
 */
public class ClassResolver {
    private static final List<String> default_imports = Arrays.asList("java.lang.*", "java.util.*");
    private static final Runnable[] default_bootstrap = new Runnable[0];
    public static final ClassResolver DEFAULT = new ClassResolver().immutable();

    private final ArrayList<String> imports;
    private final ArrayList<String> manualImports;
    private final List<Declaration> requirements;
    private VariablesMap variables;
    private String classDeclarationResolverName;
    private String packagePath;
    private String declaredClassName;
    private Class<?> declaredClass;
    private boolean logErrors;
    private boolean isGenerating;
    private Runnable[] bootstrap;

    private ClassResolver(ClassResolver src) {
        this.classDeclarationResolverName = src.classDeclarationResolverName;
        this.variables = src.variables;
        this.imports = new ArrayList<String>(src.imports);
        this.manualImports = new ArrayList<String>(src.manualImports);
        this.requirements = new ArrayList<Declaration>(src.requirements);
        this.packagePath = src.packagePath;
        this.declaredClassName = src.declaredClassName;
        this.declaredClass = src.declaredClass;
        this.logErrors = src.logErrors;
        this.isGenerating = src.isGenerating;
        this.bootstrap = src.bootstrap;
    }

    public ClassResolver() {
        this.classDeclarationResolverName = "null";
        this.variables = VariablesMap.EMPTY;
        this.imports = new ArrayList<String>();
        this.manualImports = new ArrayList<String>(default_imports);
        this.requirements = new ArrayList<Declaration>();
        this.packagePath = "";
        this.declaredClassName = null;
        this.declaredClass = null;
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
        this.requirements = new ArrayList<Declaration>();
        this.packagePath = "";
        this.logErrors = true;
        this.isGenerating = false;
        this.bootstrap = default_bootstrap;
        this.declaredClassName = null;
        this.declaredClass = null;
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

    public void setDeclaredClassName(String typeName) {
        this.declaredClassName = typeName;
        this.setDeclaredClass(this.resolveClass(typeName));
    }

    /**
     * Sets a declared class, from which other subclasses and classes in the same package can be found
     * 
     * @param type to set as the declared class
     */
    public void setDeclaredClass(Class<?> type) {
        if (type == null) {
            return;
        }
        Package pkg = type.getPackage();
        if (pkg != null) {
            this.packagePath = pkg.getName();
        }
        this.declaredClass = type;
        this.declaredClassName = type.getName();
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
     * and it was set using setDeclaredClassName, it still returns then name.
     * 
     * @return declared class name
     */
    public String getDeclaredClassName() {
        return this.declaredClassName;
    }

    /**
     * Adds a package path, making all Classes within visible
     * 
     * @param path to the package to add
     */
    public void setPackage(String path) {
        this.packagePath = path;
        this.declaredClass = null;
        this.declaredClassName = null;
        this.manualImports.clear();
        this.manualImports.addAll(default_imports);
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
     * Gets the list of imports added to this resolver using {@link #addImport(String)}
     * 
     * @return List of imports
     */
    public Collection<String> getImports() {
        return Collections.unmodifiableCollection(this.manualImports);
    }

    /**
     * Adds an import declaration. This method supports wildcard imports.
     * 
     * @param path to import
     */
    public void addImport(String path) {
        this.manualImports.add(0, path);
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
        this.variables = this.variables.put(name, value);
    }

    /**
     * Evaluates a simple logical expression using the variables set
     *
     * @param expression to evaluate
     * @return True if the expression evaluates as True, False if not
     */
    public boolean evaluateExpression(String expression) {
        // =============== Tokenize the expression ================
        expression = expression.trim();
        String varName = null;
        for (int cIdx = 0; cIdx < expression.length(); cIdx++) {
            char c = expression.charAt(cIdx);
            if (cIdx == ' ' || !Character.isLetter(c)) {
                varName = expression.substring(0, cIdx);
                expression = expression.substring(cIdx).trim();
                break;
            }
        }
        if (varName == null) {
            varName = expression;
            expression = "";
        }
        if (varName.equals("classexists")) {
            return Resolver.loadClass(expression, false) != null;
        } else if (varName.equals("methodexists") || varName.equals("fieldexists")) {
            int decClassEnd = expression.indexOf(' ');
            if (decClassEnd == -1) {
                return false; // Class not declared
            }

            // Class the method/field should be found in
            String classPath = expression.substring(0, decClassEnd);
            Class<?> declaredClass = Resolver.loadClass(classPath, false);
            if (declaredClass == null) {
                return false; // Class not available
            }

            // Rest is method/field signature
            String signatureStr = expression.substring(decClassEnd + 1).trim();

            if (varName.equals("methodexists")) {
                // Attempt to find the method by this declaration inside the Class
                return Resolver.findMethod(declaredClass, signatureStr) != null;
            } else {
                // Attempt to find the field by this declaration inside the Class
                return Resolver.findField(declaredClass, signatureStr) != null;
            }
        }
        String value1 = this.variables.get(varName);
        if (value1 == null) {
            // Edge cases: true/false constants
            if (varName.equals("1") || varName.equalsIgnoreCase("true")) {
                return true;
            }
            return false; // variable not found; never evaluates to True
        }
        int logicEndIdx = expression.indexOf(' ');
        if (logicEndIdx == -1) {
            // #if <varname> simply checks if the variable exists
            return true;
        }
        String operand = expression.substring(0, logicEndIdx);
        String value2 = expression.substring(logicEndIdx + 1).trim();
        return MountiplexUtil.evaluateText(value1, operand, value2);
    }

    /**
     * Resolves a class name to an assumed full class path.
     * If the class could not be found, one is assumed in the package path.
     * 
     * @param name of the class (generic names not supported)
     * @return resolved class path (never fails)
     */
    public String resolvePath(String name) {
        // first try to resolve the class from the name
        // note that this only succeeds when the class actually exists
        Class<?> type = resolveClass(name);
        if (type != null) {
            return resolvePath(type);
        }

        // Handle array types proper.
        if (name.endsWith("[]")) {
            return resolvePath(name.substring(0, name.length() - 2)) + "[]";
        }

        // retrieve the first word of the class, before the .
        int nameFirstEnd = name.indexOf('.');
        String nameFirst = (nameFirstEnd == -1) ? name : name.substring(0, nameFirstEnd);
        String nameAfter = (nameFirstEnd == -1) ? "" : name.substring(nameFirstEnd);

        // check if this is one of our imports
        for (String imp : this.manualImports) {
            String impName = imp.substring(imp.lastIndexOf('.') + 1);
            if (impName.equals(nameFirst)) {
                return imp + nameAfter;
            }
        }

        // 'assume' the class can be found at the package path
        // only do this when no package path portion is declared
        if (packagePath.isEmpty() || (Character.isLowerCase(name.charAt(0)) && name.contains("."))) {
            return name;
        } else {
            return packagePath + "." + name;
        }
    }

    /**
     * Resolves a class name to a class.
     * 
     * @param name of the class (generic names not supported)
     * @return resolved class, or null if not found
     */
    public Class<?> resolveClass(String name) {
        // Return Object for generic typings (T, K, etc.)
        if (name.length() == 1) {
            return Object.class;
        }

        // Array types
        if (name.endsWith("[]")) {
            Class<?> componentType = resolveClass(name.substring(0, name.length() - 2));
            if (componentType != null) {
                return MountiplexUtil.getArrayType(componentType);
            } else {
                return null;
            }
        }

        Class<?> fieldType = Resolver.loadClass(name, false);

        if (fieldType == null) {
            String dotName = "." + name;
            for (String imp : this.imports) {
                if (imp.endsWith(".*")) {
                    fieldType = Resolver.loadClass(imp.substring(0, imp.length() - 1) + name, false);
                } else if (imp.endsWith(dotName)) {
                    fieldType = Resolver.loadClass(imp, false);
                } else {
                    continue;
                }
                if (fieldType != null) {
                    break;
                }
            }
        }

        return fieldType;
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
            return type.getName().replace('$', '.');
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
        String name = type.getName().replace('$', '.');
        for (String imp : this.imports) {
            if (imp.equals(name)) {
                return type.getSimpleName();
            }
            if (imp.endsWith(".*")) {
                String imp_p = imp.substring(0, imp.length() - 1);
                if (name.startsWith(imp_p)) {
                    return name.substring(imp_p.length());
                }
            }
        }
        return name;
    }

    /**
     * Stores a requirement parsed from a #require statement.
     * It can later be found again when resolving requirements in generated code.
     * 
     * @param declaration
     */
    public void storeRequirement(Declaration declaration) {
        this.requirements.add(0, declaration);
    }

    /**
     * Gets a list of requirements parsed from #require statements.
     * 
     * @return requirements
     */
    public List<Declaration> getRequirements() {
        return this.requirements;
    }

    private void regenImports() {
        this.imports.clear();
        this.imports.addAll(this.manualImports);
        if (this.declaredClassName != null) {
            this.imports.add(this.declaredClassName + ".*");
        }
        if (this.packagePath != null && this.packagePath.length() > 0) {
            this.imports.add(this.packagePath + ".*");
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

        public String get(String key) {
            return this._map.get(key);
        }

        public VariablesMap put(String key, String value) {
            Map<String, String> new_map = new HashMap<String, String>(this._map);
            new_map.put(key, value);
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
