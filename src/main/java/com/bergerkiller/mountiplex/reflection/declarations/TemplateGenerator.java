package com.bergerkiller.mountiplex.reflection.declarations;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import com.bergerkiller.mountiplex.reflection.util.BoxedType;

public class TemplateGenerator {
    private ClassDeclaration rootClassDec = null;
    private File rootDir = null;
    private String path = "";
    private StringBuilder builder = new StringBuilder();
    private Map<String, String> imports = new TreeMap<String, String>();
    private int indent = 0;
    private Map<TypeDeclaration, TemplateGenerator> generatorPool = null;

    public void setClass(ClassDeclaration classDec) {
        this.rootClassDec = classDec;
    }

    public ClassDeclaration getClassType() {
        return this.rootClassDec;
    }

    public void setPool(Map<TypeDeclaration, TemplateGenerator> generatorPool) {
        this.generatorPool = generatorPool;
    }

    public void setRootDirectory(File rootDir) {
        this.rootDir = rootDir;
    }

    public void setPath(String path) {
        this.path = path.replace('/', '.');
    }

    public void generate() {
        this.builder = new StringBuilder();
        this.imports = new TreeMap<String, String>();
        this.imports.put("Template", Template.class.getName());
        this.indent = 0;
        String packagePath = this.path;

        addClass(this.rootClassDec);

        // Insert package path and imports at the top
        String resultStr = this.builder.toString();
        this.builder.setLength(0);
        addLine("package " + packagePath);
        addLine();

        String classRoot = this.path + "." + this.handleName(this.rootClassDec);
        for (String importPath : this.imports.values()) {
            // Verify this import is not in the root class file
            if (importPath.startsWith(classRoot)) {
                continue;
            }
            // Verify this import is not another import in the same package
            if (importPath.startsWith(this.path) && !importPath.substring(this.path.length() + 1).contains(".")) {
                continue;
            }
            addLine("import " + importPath);
        }
        this.builder.append(resultStr);
        String templateContents = this.builder.toString();

        try {
            File sourceFileDir = new File(this.rootDir, packagePath.replace('.', File.separatorChar));
            sourceFileDir.mkdirs();
            File sourceFile = new File(sourceFileDir, handleName(this.rootClassDec) + ".java");

            // First, check if the template contents have changed before writing
            // This avoids unneeded compilation of classes that have not (actually) changed
            boolean changed = true;
            if (sourceFile.exists()) {
                String originalContents = new String(Files.readAllBytes(sourceFile.toPath()), StandardCharsets.UTF_8);
                changed = !originalContents.equals(templateContents);
            }

            if (changed) {
                BufferedWriter writer = new BufferedWriter(new FileWriter(sourceFile));
                try {
                    writer.write(templateContents);
                } finally {
                    writer.close();
                }
            }

        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    private String findClassName(ClassDeclaration root, String path, TypeDeclaration type) {
        String new_path = path + "." + handleName(root);
        if (root.type.equals(type)) {
            return new_path;
        } else {
            for (ClassDeclaration sub : root.subclasses) {
                String result = findClassName(sub, new_path, type);
                if (result != null) {
                    return result;
                }
            }
            return null;
        }
    }

    private void addClass(ClassDeclaration classDec) {
        if (classDec.type.typePath.equals("")) {
            return; // don't know why this happens, but it does
        }
        String extendedHandleType = "Template.Handle";
        TypeDeclaration baseType = classDec.base;
        if (baseType != null && generatorPool != null) {
            TemplateGenerator baseGen = generatorPool.get(baseType);
            if (baseGen != null) {
                String path = baseGen.findClassName(baseGen.rootClassDec, baseGen.path, classDec.base);
                if (path != null) {
                    extendedHandleType = resolveImport(path);
                } else {
                    System.err.println("Failed to find super type template class: " + baseType.typePath);
                    System.err.println("With super template generator: " + baseGen.path);
                    System.err.println("At template " + classDec.type.typePath);
                }
            } else {
                System.err.println("Super type has no template: " + baseType.typePath);
                System.err.println("At template " + classDec.type.typePath);
            }
        }

        String classHeadStatic = "";
        if (classDec != rootClassDec) {
            classHeadStatic = "static ";
        }

        addLine();
        addComment("Instance wrapper handle for type <b>" + classDec.type.typePath + "</b>.\n" +
                   "To access members without creating a handle type, use the static {@link #T} member.\n" +
                   "New handles can be created from raw instances using {@link #createHandle(Object)}.");
        populateModifiers(classDec.modifiers);
        addLine("@Template.InstanceType(\"" + classDec.type.typePath + "\")");
        addLine("public abstract " + classHeadStatic + "class " + handleName(classDec) + " extends " + extendedHandleType + " {");
        {
            addComment("@See {@link " + className(classDec) + "}");
            addLine("public static final " + className(classDec) + " T = Template.Class.create(" + className(classDec) + ".class, " +
                    classDec.getResolver().getClassDeclarationResolverName() + ")");

            {
                // Enumeration constants
                for (FieldDeclaration fDec : classDec.fields) {
                    if (!fDec.isEnum || fDec.modifiers.isOptional()) {
                        continue;
                    }

                    String typeStr = getFieldTypeStr(fDec);
                    String fName = fDec.name.real();

                    populateModifiers(fDec.modifiers);
                    addLine("public static final " + typeStr + " " + fName + " = T." + fName + ".getSafe()");
                }

                // Static constant fields
                for (FieldDeclaration fDec : classDec.fields) {
                    if (fDec.modifiers.isUnknown() || !fDec.modifiers.isStatic() || !fDec.modifiers.isFinal() || fDec.isEnum || fDec.modifiers.isOptional()) {
                        continue;
                    }

                    String primTypeStr = getPrimFieldType(fDec);
                    String typeStr = getFieldTypeStr(fDec);
                    String fName = fDec.name.real();

                    populateModifiers(fDec.modifiers);
                    addLine("public static final " + typeStr + " " + fName + " = T." + fName + ".get" + primTypeStr + "Safe()");
                }

                addLine("/* ============================================================================== */");
                addLine();

                // Create from existing handle; important for use by converters
                addLine("public static " + handleName(classDec) + " createHandle(Object handleInstance) {");
                addLine("return T.createHandle(handleInstance)");
                addLine("}");

                // Constructors turned into static create functions, with converted parameters
                for (ConstructorDeclaration cDec : classDec.constructors) {
                    if (cDec.modifiers.isOptional()) {
                        continue;
                    }

                    populateModifiers(cDec.modifiers);
                    String cHeader = "public static final " + getExposedTypeStr(cDec.type) + " createNew";
                    addLine(cHeader + getParamsBody(cDec.parameters) + " {");
                    if (cDec.parameters.parameters.length <= 5) {
                        addLine("return T." + cDec.getName() + ".newInstance(" + getArgsBody(cDec.parameters) + ")");
                    } else {
                        addLine("return T." + cDec.getName() + ".newInstanceVA(" + getArgsBody(cDec.parameters) + ")");
                    }
                    addLine("}");
                }

                addLine("/* ============================================================================== */");
                addLine();

                // Static fields
                for (FieldDeclaration fDec : classDec.fields) {
                    if (fDec.modifiers.isUnknown() || !fDec.modifiers.isStatic() || fDec.modifiers.isFinal() || fDec.isEnum || fDec.modifiers.isOptional()) {
                        continue;
                    }
                    String primTypeStr = getPrimFieldType(fDec);
                    String typeStr = getFieldTypeStr(fDec);
                    String fName = fDec.name.real();

                    // Getter
                    populateModifiers(fDec.modifiers);
                    addLine("public static " + typeStr + " " + fName + "() {");
                    addLine("return T." + fDec.name.real() + ".get" + primTypeStr + "()");
                    addLine("}");

                    // Setter
                    if (!fDec.modifiers.isReadonly()) {
                        populateModifiers(fDec.modifiers);
                        addLine("public static void " + fName + "_set(" + typeStr + " value) {");
                        addLine("T." + fDec.name.real() + ".set" + primTypeStr + "(value)");
                        addLine("}");
                    }
                }

                // Static methods
                for (MethodDeclaration mDec : classDec.methods) {
                    if (mDec.modifiers.isUnknown() || !mDec.modifiers.isStatic() || mDec.modifiers.isOptional()) {
                        continue;
                    }
                    if (TemplateHandleBuilder.isCreateHandleMethod(mDec)) {
                        continue;
                    }
                    addStaticMethodBody(mDec);
                }

                // Local methods
                for (MethodDeclaration mDec : classDec.methods) {
                    if (mDec.modifiers.isUnknown() || mDec.modifiers.isStatic() || mDec.modifiers.isOptional()) {
                        continue;
                    }
                    addAbstractMethodBody(mDec);
                }

                // Custom code section
                if (classDec.code.length() > 0) {
                    for (String line : classDec.code.split("\n")) {
                        if (line.trim().length() > 0) {
                            for (int i = 0; i < indent; i++) {
                                this.builder.append("    ");
                            }
                            this.builder.append(line);
                        }
                        this.builder.append('\n');
                    }
                }

                // Local fields
                for (FieldDeclaration fDec : classDec.fields) {
                    if (fDec.modifiers.isUnknown() || fDec.modifiers.isStatic() || fDec.isEnum || fDec.modifiers.isOptional()) {
                        continue;
                    }
                    String typeStr = getFieldTypeStr(fDec);

                    // Getter
                    populateModifiers(fDec.modifiers);
                    addLine("public abstract " + typeStr + " " + getGetterName(fDec) + "()");

                    // Setter
                    if (!fDec.modifiers.isReadonly()) {
                        populateModifiers(fDec.modifiers);
                        addLine("public abstract void " + getSetterName(fDec) + "(" + typeStr + " value)");
                    }

                    /*
                    String primTypeStr = getPrimFieldType(fDec);

                    // Getter
                    populateModifiers(fDec.modifiers);
                    addLine("public " + typeStr + " " + getGetterName(fDec) + "() {");
                    addLine("return T." + fDec.name.real() + ".get" + primTypeStr + "(getRaw())");
                    addLine("}");

                    // Setter
                    populateModifiers(fDec.modifiers);
                    addLine("public void " + getSetterName(fDec) + "(" + typeStr + " value) {");
                    addLine("T." + fDec.name.real() + ".set" + primTypeStr + "(getRaw(), value)");
                    addLine("}");
                    */
                }
            }

            addComment("Stores class members for <b>" + classDec.type.typePath + "</b>.\n" +
                       "Methods, fields, and constructors can be used without using Handle Objects.");
            addLine("public static final class " + className(classDec) + " extends Template.Class<" + handleName(classDec) + "> {");
            {
                // Enumeration constants
                boolean hasEnumFields = false;
                for (FieldDeclaration fDec : classDec.fields) {
                    if (fDec.modifiers.isUnknown() || !fDec.isEnum) {
                        continue;
                    }

                    String fieldTypeStr = "Template.EnumConstant";
                    if (hasConversion(fDec)) {
                        fieldTypeStr += ".Converted";
                        fieldTypeStr += "<" + getTypeStr(fDec.type.cast) + ">";
                    } else {
                        fieldTypeStr += "<" + getFieldTypeStr(fDec) + ">";
                    }

                    populateModifiers(fDec.modifiers);
                    addLine("public final " + fieldTypeStr + " " + fDec.name.real() + " = new " + fieldTypeStr + "()");
                    hasEnumFields = true;
                }
                if (hasEnumFields) {
                    addLine();
                }

                // Constructors
                boolean hasConstructors = false;
                for (ConstructorDeclaration cDec : classDec.constructors) {
                    String constrTypeStr = "Template.Constructor";
                    if (hasConversion(cDec)) {
                        constrTypeStr += ".Converted";
                    }
                    constrTypeStr += "<" + getExposedTypeStr(cDec.type) + ">";

                    populateModifiers(cDec.modifiers);
                    addLine("public final " + constrTypeStr + " " + cDec.getName() + " = new " + constrTypeStr + "()");
                    hasConstructors = true;
                }
                if (hasConstructors) {
                    addLine();
                }

                // Static Fields
                boolean hasStaticFields = false;
                for (FieldDeclaration fDec : classDec.fields) {
                    if (fDec.modifiers.isUnknown() || !fDec.modifiers.isStatic() || fDec.isEnum) {
                        continue;
                    }
                    String fieldTypeStr = "Template.StaticField";
                    if (hasConversion(fDec)) {
                        fieldTypeStr += ".Converted";
                        fieldTypeStr += "<" + getTypeStr(fDec.type.cast) + ">";
                    } else {
                        String primTypeStr = getPrimFieldType(fDec);
                        if (!primTypeStr.isEmpty()) {
                            fieldTypeStr += "." + primTypeStr;
                        } else {
                            fieldTypeStr += "<" + getFieldTypeStr(fDec) + ">";
                        }
                    }

                    populateModifiers(fDec.modifiers);
                    addLine("public final " + fieldTypeStr + " " + fDec.name.real() + " = new " + fieldTypeStr + "()");
                    hasStaticFields = true;
                }
                if (hasStaticFields) {
                    addLine();
                }

                // Local fields
                boolean hasLocalFields = false;
                for (FieldDeclaration fDec : classDec.fields) {
                    if (fDec.modifiers.isUnknown() || fDec.modifiers.isStatic() || fDec.isEnum) {
                        continue;
                    }
                    String fieldTypeStr = "Template.Field";
                    if (hasConversion(fDec)) {
                        fieldTypeStr += ".Converted";
                        fieldTypeStr += "<" + getTypeStr(fDec.type.cast) + ">";
                    } else {
                        String primTypeStr = getPrimFieldType(fDec);
                        if (!primTypeStr.isEmpty()) {
                            fieldTypeStr += "." + primTypeStr;
                        } else {
                            fieldTypeStr += "<" + getFieldTypeStr(fDec) + ">";
                        }
                    }

                    populateModifiers(fDec.modifiers);
                    addLine("public final " + fieldTypeStr + " " + fDec.name.real() + " = new " + fieldTypeStr + "()");
                    hasLocalFields = true;
                }
                if (hasLocalFields) {
                    addLine();
                }

                // Static methods
                boolean hasStaticMethods = false;
                for (MethodDeclaration mDec : classDec.methods) {
                    if (mDec.modifiers.isUnknown() || !mDec.modifiers.isStatic()) {
                        continue;
                    }
                    String methodTypeStr = "Template.StaticMethod" + getMethodAppend(mDec);

                    populateModifiers(mDec.modifiers);
                    addLine("public final " + methodTypeStr + " " + mDec.name.real() + " = new " + methodTypeStr + "()");
                    hasStaticMethods = true;
                }
                if (hasStaticMethods) {
                    addLine();
                }

                // Local methods
                boolean hasLocalMethods = false;
                for (MethodDeclaration mDec : classDec.methods) {
                    if (mDec.modifiers.isUnknown() || mDec.modifiers.isStatic()) {
                        continue;
                    }
                    String methodTypeStr = "Template.Method" + getMethodAppend(mDec);

                    populateModifiers(mDec.modifiers);
                    addLine("public final " + methodTypeStr + " " + mDec.name.real() + " = new " + methodTypeStr + "()");
                    hasLocalMethods = true;
                }
                if (hasLocalMethods) {
                    addLine();
                }
            }
            addLine("}");

            // Subclasses
            for (ClassDeclaration subClassDec : classDec.subclasses) {
                addClass(subClassDec);
            }
        }

        addLine("}");

    }

    public static String getGetterName(FieldDeclaration fDec) {
        String fName = getPropertyName(fDec);

        // IsProperty is retained without adding 'get' in front of it
        if (fName.length() > 2 && fName.startsWith("Is") && Character.isUpperCase(fName.charAt(2))) {
            return fName.substring(0, 1).toLowerCase(Locale.ENGLISH) + fName.substring(1);
        }

        // If field type is boolean, use 'is' instead of 'get'
        if (boolean.class.equals(fDec.type.exposed().type)) {
            return "is" + fName;
        } else {
            return "get" +  fName;
        }
    }

    public static String getSetterName(FieldDeclaration fDec) {
        return "set" + getPropertyName(fDec);
    }

    private void populateModifiers(ModifierDeclaration dec) {
        if (dec.isRawtype()) {
            addLine("@SuppressWarnings(\"rawtypes\")");
        }
        if (dec.isOptional()) {
            addLine("@Template.Optional");
        }
        if (dec.isReadonly()) {
            addLine("@Template.Readonly");
        }
    }

    private String getParamsBody(ParameterListDeclaration parameters) {
        String paramsStr = "(";
        for (int i = 0; i < parameters.parameters.length; i++) {
            if (i > 0) {
                paramsStr += ", ";
            }
            paramsStr += getExposedTypeStr(parameters.parameters[i].type);
            paramsStr += " " + parameters.parameters[i].name.real();
        }
        return paramsStr + ")";
    }

    private String getArgsBody(ParameterListDeclaration parameters) {
        String argsStr = "";
        for (int i = 0; i < parameters.parameters.length; i++) {
            if (i > 0) {
                argsStr += ", ";
            }
            argsStr += parameters.parameters[i].name.real();
        }
        return argsStr;
    }

    private void addAbstractMethodBody(MethodDeclaration mDec) {
        addLine("public abstract " +
                getExposedTypeStr(mDec.returnType) +
                " " + mDec.name.real() +
                getParamsBody(mDec.parameters));
    }

    private void addStaticMethodBody(MethodDeclaration mDec) {
        // Include body
        addLine("public static " +
                getExposedTypeStr(mDec.returnType) +
                " " + mDec.name.real() +
                getParamsBody(mDec.parameters) + " {");

        String bodyStr = "";
        if (!void.class.equals(mDec.returnType.exposed().type)) {
            bodyStr += "return ";
        }

        if (hasConversion(mDec)) {
            // Call invoke/invokeVA on the Template methods themselves
            if (mDec.parameters.parameters.length <= 5) {
                // 0/1/2/3/4/5 argument specific invoke functions that avoid Object[] creation
                bodyStr += "T." + mDec.name.real() + ".invoke(";
            } else {
                // varargs Object[] invoke
                bodyStr += "T." + mDec.name.real() + ".invokeVA(";
            }
        } else {
            // Call invoke/invokeVA on the 'invoker' field
            if (mDec.parameters.parameters.length == 0) {
                // 0 argument specified invoke function, omit trailing ,
                bodyStr += "T." + mDec.name.real() + ".invoker.invoke(null";
            } else if (mDec.parameters.parameters.length <= 5) {
                // 1/2/3/4/5 argument specific invoke functions that avoid Object[] creation
                bodyStr += "T." + mDec.name.real() + ".invoker.invoke(null,";
            } else {
                // varargs Object[] invoke
                bodyStr += "T." + mDec.name.real() + ".invoker.invokeVA(null,";
            }
        }

        bodyStr += getArgsBody(mDec.parameters);
        bodyStr += ")";

        populateModifiers(mDec.modifiers);
        addLine(bodyStr);
        addLine("}");
    }

    private boolean hasConversion(FieldDeclaration fDec) {
        if (fDec.type.cast != null) {
            // If read-only then casting to Object is free and no conversion is needed
            // When writing is possible, there is more complex conversion involved
            if (!fDec.modifiers.isReadonly() || fDec.type.type != Object.class) {
                return true;
            }
        }
        return false;
    }

    private boolean hasConversion(ConstructorDeclaration cDec) {
        if (cDec.type.cast != null && cDec.type.cast.type != Object.class) {
            return true;
        }
        for (int i = 0; i < cDec.parameters.parameters.length; i++) {
            if (cDec.parameters.parameters[i].type.cast != null) {
                return true;
            }
        }
        return false;
    }

    private boolean hasConversion(MethodDeclaration mDec) {
        if (mDec.returnType.cast != null && mDec.returnType.cast.type != Object.class) {
            return true;
        }
        for (int i = 0; i < mDec.parameters.parameters.length; i++) {
            if (mDec.parameters.parameters[i].type.cast != null) {
                return true;
            }
        }
        return false;
    }

    private String getMethodAppend(MethodDeclaration mDec) {
        String app = "";
        if (hasConversion(mDec)) {
            app += ".Converted";
        }
        TypeDeclaration returnType = (mDec.returnType.cast != null) ? mDec.returnType.cast : mDec.returnType;
        if (returnType.type != null && returnType.type.isPrimitive()) {
            app += "<" + BoxedType.getBoxedType(returnType.type).getSimpleName() + ">"; 
        } else {
            app += "<" + getTypeStr(returnType) + ">";
        }
        return app;
    }

    private String getExposedTypeStr(TypeDeclaration type) {
        return getTypeStr(type.exposed());
    }

    private String getFieldTypeStr(FieldDeclaration fDec) {
        return getExposedTypeStr(fDec.type);
    }

    // adds/resolves the imports for a class type, returning the keyword to use in the source file
    private String resolveImport(String typePath) {
        String importPath = typePath;
        while (importPath.endsWith("[]")) {
            importPath = importPath.substring(0, importPath.length() - 2);
        }
        String typeName = typePath.substring(typePath.lastIndexOf('.') + 1);
        String importName = importPath.substring(importPath.indexOf('.') + 1);
        String oldImport = this.imports.get(importName);
        String fullType;
        if (oldImport != null) {
            if (oldImport.equals(importPath)) {
                fullType = typeName;
            } else {
                fullType = typePath;
            }
        } else {
            this.imports.put(importName, importPath);
            fullType = typeName;
        }
        return fullType;
    }

    // gets the type string while automatically adding/resolving imports
    private String getTypeStr(TypeDeclaration type) {
        if (type.isArray()) {
            return getTypeStr(type.getComponentType()) + "[]";
        }
        String fullType;
        if (type.isBuiltin()) {
            fullType = type.typeName;
        } else {
            fullType = resolveImport(type.typePath);
        }
        if (type.isWildcard) {
            if (fullType.length() > 0) {
                fullType = "? extends " + fullType;
            } else {
                fullType = "?";
            }
        }
        if (type.genericTypes.length > 0) {
            fullType += "<";
            boolean first = true;
            for (TypeDeclaration gen : type.genericTypes) {
                if (first) {
                    first = false;
                } else {
                    fullType += ", ";
                }
                fullType += getTypeStr(gen);
            }
            fullType += ">";
        }
        return fullType;
    }

    private static String getPropertyName(FieldDeclaration fDec) {
        String name = fDec.name.real();
        if (name.isEmpty()) {
            return "UNKNOWN";
        } else {
            return name.substring(0, 1).toUpperCase(Locale.ENGLISH) + name.substring(1);
        }
    }

    private String getPrimFieldType(FieldDeclaration fDec) {
        Class<?> fType = fDec.type.exposed().type;
        if (fType != null) {
            if (fType.equals(byte.class)) {
                return "Byte";
            } else if (fType.equals(short.class)) {
                return "Short";
            } else if (fType.equals(int.class)) {
                return "Integer";
            } else if (fType.equals(long.class)) {
                return "Long";
            } else if (fType.equals(float.class)) {
                return "Float";
            } else if (fType.equals(double.class)) {
                return "Double";
            } else if (fType.equals(char.class)) {
                return "Character";
            } else if (fType.equals(boolean.class)) {
                return "Boolean";
            }
        }
        return "";
    }

    private String handleName(ClassDeclaration classDec) {
        return filterTypeName(classDec.type.typeName) + "Handle";
    }

    private String className(ClassDeclaration classDec) {
        return filterTypeName(classDec.type.typeName) + "Class";
    }

    private static String filterTypeName(String name) {
        int idx = name.lastIndexOf('.');
        if (idx != -1) {
            return name.substring(idx + 1);
        }
        return name;
    }

    private void addComment(String commentText) {
        String[] lines = commentText.split("\\r?\\n");
        if (lines.length == 1) {
            addRawLine("/** " + lines[0] + " */");
        } else {
            addRawLine("/**");
            for (String line : lines) {
                addRawLine(" * " + line);
            }
            addRawLine(" */");
        }
    }

    private void addLine() {
        this.builder.append('\n');
    }

    private void addLine(String line) {
        if (line.endsWith("}")) {
            this.indent(-1);
        }
        for (int i = 0; i < indent; i++) {
            this.builder.append("    ");
        }
        this.builder.append(line);
        if (line.endsWith("{")) {
            this.indent(1);
        } else if (line.endsWith("}")) {
            this.builder.append('\n');
        } else if (!line.startsWith("//") && !line.startsWith("/*") && !line.startsWith("@")) {
            this.builder.append(';');
        }
        this.builder.append('\n');
    }

    private void addRawLine(String line) {
        for (int i = 0; i < indent; i++) {
            this.builder.append("    ");
        }
        this.builder.append(line);
        this.builder.append('\n');
    }

    private void indent(int indent) {
        this.indent += indent;
    }
}
