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

/**
 * Resolves class names into Class Types based on package and import paths.
 */
public class ClassResolver {
    private static final List<String> default_imports = Arrays.asList("java.lang.*", "java.util.*");
    public static final ClassResolver DEFAULT = new ClassResolver().immutable();

    private final ArrayList<String> imports;
    private final ArrayList<String> manualImports;
    private final HashMap<String, String> variables = new HashMap<String, String>();
    private String packagePath;
    private Class<?> declaredClass;

    private ClassResolver(ClassResolver src) {
        this.imports = new ArrayList<String>(src.imports);
        this.manualImports = new ArrayList<String>(src.manualImports);
        this.packagePath = src.packagePath;
        this.declaredClass = src.declaredClass;
    }

    public ClassResolver() {
        this.imports = new ArrayList<String>();
        this.manualImports = new ArrayList<String>(default_imports);
        this.packagePath = "";
        this.declaredClass = null;
        this.regenImports();
    }

    public ClassResolver(String packagePath) {
        this.imports = new ArrayList<String>();
        this.manualImports = new ArrayList<String>();
        this.packagePath = "";
        this.setPackage(packagePath);
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
     * Adds a package path, making all Classes within visible
     * 
     * @param path to the package to add
     */
    public void setPackage(String path) {
        this.packagePath = path;
        this.declaredClass = null;
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
        StringBuilder result = new StringBuilder();
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            result.append("#set ").append(entry.getKey()).append(' ').append(entry.getValue()).append('\n');
        }
        return result.toString();
    }

    /**
     * Sets an environment variable used during parsing of template sources
     * 
     * @param name variable name
     * @param value variable value
     */
    public void setVariable(String name, String value) {
        variables.put(name, value);
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

        String dotName = "." + name;
        if (fieldType == null) {
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

    private void regenImports() {
        this.imports.clear();
        this.imports.addAll(this.manualImports);
        if (this.declaredClass != null) {
            this.imports.add(this.declaredClass.getName() + ".*");
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
}
