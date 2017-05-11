package com.bergerkiller.mountiplex.reflection.declarations;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.HashMap;

import com.bergerkiller.mountiplex.reflection.util.BoxedType;

public class TemplateGenerator {
    private ClassDeclaration classDec = null;
    private File rootDir = null;
    private String path = "";
    private StringBuilder builder = new StringBuilder();
    private HashMap<String, String> imports = new HashMap<String, String>();
    private int indent = 0;

    public void setClass(ClassDeclaration classDec) {
        this.classDec = classDec;
    }

    public void setRootDirectory(File rootDir) {
        this.rootDir = rootDir;
    }

    public void setPath(String path) {
        this.path = path.replace('/', '.');
    }

    public void generate() {
        this.builder = new StringBuilder();
        this.imports = new HashMap<String, String>();
        this.imports.put("Template", Template.class.getName());
        this.indent = 0;
        String packagePath = this.path;
        String extendedHandleType = "Template.Handle";
        String extendedClassType = "Template.Class";

        addLine("public class " + handleName() + " extends " + extendedHandleType + " {");
        {
            addLine("public static final " + className() + " T = new " + className() + "()");
            addLine();

            {
                // Static constant fields
                for (FieldDeclaration fDec : this.classDec.fields) {
                    if (fDec.modifiers.isUnknown() || !fDec.modifiers.isStatic() || !fDec.modifiers.isConstant()) {
                        continue;
                    }

                    String primTypeStr = getPrimFieldType(fDec);
                    String typeStr = getFieldTypeStr(fDec);
                    String fName = fDec.name.real();

                    addLine("public static final " + typeStr + " " + fName + " = T." + fName + ".get" + primTypeStr + "Safe()");
                }

                // Static fields
                for (FieldDeclaration fDec : this.classDec.fields) {
                    if (fDec.modifiers.isUnknown() || !fDec.modifiers.isStatic() || fDec.modifiers.isConstant()) {
                        continue;
                    }
                    String primTypeStr = getPrimFieldType(fDec);
                    String typeStr = getFieldTypeStr(fDec);
                    String fName = fDec.name.real();

                    // Getter
                    addLine("public static " + typeStr + " " + fName + "() {");
                    addLine("return T." + fDec.name.real() + ".get" + primTypeStr + "()");
                    addLine("}");

                    // Setter
                    addLine("public static void " + fName + "_set(" + typeStr + " value) {");
                    addLine("T." + fDec.name.real() + ".set" + primTypeStr + "(value)");
                    addLine("}");
                }

                // Static methods
                for (MethodDeclaration mDec : this.classDec.methods) {
                    if (mDec.modifiers.isUnknown() || !mDec.modifiers.isStatic()) {
                        continue;
                    }
                    addMethodBody(mDec);
                }

                // Local methods
                for (MethodDeclaration mDec : this.classDec.methods) {
                    if (mDec.modifiers.isUnknown() || mDec.modifiers.isStatic()) {
                        continue;
                    }
                    addMethodBody(mDec);
                }

                // Local fields
                for (FieldDeclaration fDec : this.classDec.fields) {
                    if (fDec.modifiers.isUnknown() || fDec.modifiers.isStatic()) {
                        continue;
                    }
                    String primTypeStr = getPrimFieldType(fDec);
                    String typeStr = getFieldTypeStr(fDec);
                    String fName = getPropertyName(fDec);

                    // Getter
                    addLine("public " + typeStr + " get" + fName + "() {");
                    addLine("return T." + fDec.name.real() + ".get" + primTypeStr + "(instance)");
                    addLine("}");

                    // Setter
                    addLine("public void set" + fName + "(" + typeStr + " value) {");
                    addLine("T." + fDec.name.real() + ".set" + primTypeStr + "(instance, value)");
                    addLine("}");
                }
            }

            addLine("public static class " + className() + " extends " + extendedClassType + " {");
            {
                // Constructor declaring where the class should be initialized
                addLine("protected " + className() + "() {");
                addLine("init(" + className() + ".class, \"" + this.classDec.type.typePath + "\")");
                addLine("}");
                addLine();

                // Static Fields
                boolean hasStaticFields = false;
                for (FieldDeclaration fDec : this.classDec.fields) {
                    if (fDec.modifiers.isUnknown() || !fDec.modifiers.isStatic()) {
                        continue;
                    }
                    String fieldTypeStr = "Template.StaticField";
                    if (fDec.type.cast != null) {
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
                    addLine("public final " + fieldTypeStr + " " + fDec.name.real() + " = new " + fieldTypeStr + "()");
                    hasStaticFields = true;
                }
                if (hasStaticFields) {
                    addLine();
                }

                // Local fields
                boolean hasLocalFields = false;
                for (FieldDeclaration fDec : this.classDec.fields) {
                    if (fDec.modifiers.isUnknown() || fDec.modifiers.isStatic()) {
                        continue;
                    }
                    String fieldTypeStr = "Template.Field";
                    if (fDec.type.cast != null) {
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
                    addLine("public final " + fieldTypeStr + " " + fDec.name.real() + " = new " + fieldTypeStr + "()");
                    hasLocalFields = true;
                }
                if (hasLocalFields) {
                    addLine();
                }

                // Static methods
                boolean hasStaticMethods = false;
                for (MethodDeclaration mDec : this.classDec.methods) {
                    if (mDec.modifiers.isUnknown() || !mDec.modifiers.isStatic()) {
                        continue;
                    }
                    String methodTypeStr = "Template.StaticMethod" + getMethodAppend(mDec);
                    addLine("public final " + methodTypeStr + " " + mDec.name.real() + " = new " + methodTypeStr + "()");
                    hasStaticMethods = true;
                }
                if (hasStaticMethods) {
                    addLine();
                }

                // Local methods
                boolean hasLocalMethods = false;
                for (MethodDeclaration mDec : this.classDec.methods) {
                    if (mDec.modifiers.isUnknown() || mDec.modifiers.isStatic()) {
                        continue;
                    }
                    String methodTypeStr = "Template.Method" + getMethodAppend(mDec);
                    addLine("public final " + methodTypeStr + " " + mDec.name.real() + " = new " + methodTypeStr + "()");
                    hasLocalMethods = true;
                }
                if (hasLocalMethods) {
                    addLine();
                }
            }
            addLine("}");
        }

        addLine("}");

        // Insert package path and imports at the top
        String resultStr = this.builder.toString();
        this.builder.setLength(0);
        addLine("package " + packagePath);
        addLine();
        for (String importPath : this.imports.values()) {
            addLine("import " + importPath);
        }
        this.builder.append(resultStr);

        try {
            File sourceFileDir = new File(this.rootDir, packagePath.replace('.', File.separatorChar));
            sourceFileDir.mkdirs();
            File sourceFile = new File(sourceFileDir, handleName() + ".java");
            BufferedWriter writer = new BufferedWriter(new FileWriter(sourceFile));
            try {
                writer.write(this.builder.toString());
            } catch (Throwable t) {
                t.printStackTrace();
            }
            writer.close();
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    private void addMethodBody(MethodDeclaration mDec) {
        String methodStr = "public ";
        if (mDec.modifiers.isStatic()) {
            methodStr += "static ";
        }
        methodStr += getTypeStrCast(mDec.returnType);
        methodStr += " " + mDec.name.real() + "(";
        for (int i = 0; i < mDec.parameters.parameters.length; i++) {
            if (i > 0) {
                methodStr += ", ";
            }
            methodStr += getTypeStrCast(mDec.parameters.parameters[i].type);
            methodStr += " " + mDec.parameters.parameters[i].name.real();
        }
        addLine(methodStr + ") {");

        String bodyStr = "";
        if (!void.class.equals(mDec.returnType.type)) {
            bodyStr += "return ";
        }
        bodyStr += "T." + mDec.name.real() + ".invoke(";
        if (!mDec.modifiers.isStatic()) {
            bodyStr += "instance";
            if (mDec.parameters.parameters.length > 0) {
                bodyStr += ", ";
            }
        }
        for (int i = 0; i < mDec.parameters.parameters.length; i++) {
            if (i > 0) {
                bodyStr += ", ";
            }
            bodyStr += mDec.parameters.parameters[i].name.real();
        }
        bodyStr += ")";
        addLine(bodyStr);
        addLine("}");
    }

    private String getMethodAppend(MethodDeclaration mDec) {
        String app = "";
        boolean hasConversion = false;
        if (mDec.returnType.cast != null) {
            hasConversion = true;
        }
        for (int i = 0; i < mDec.parameters.parameters.length; i++) {
            if (mDec.parameters.parameters[i].type.cast != null) {
                hasConversion = true;
                break;
            }
        }
        if (hasConversion) {
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

    private TypeDeclaration getFieldType(FieldDeclaration fDec) {
        if (fDec.type.cast != null) {
            return fDec.type.cast;
        } else {
            return fDec.type;
        }
    }

    private String getFieldTypeStr(FieldDeclaration fDec) {
        return getTypeStr(getFieldType(fDec));
    }

    private String getTypeStrCast(TypeDeclaration type) {
        return (type.cast != null) ? getTypeStr(type.cast) : getTypeStr(type);
    }
    
    // gets the type string while automatically adding/resolving imports
    private String getTypeStr(TypeDeclaration type) {
        if (type.isBuiltin()) {
            return type.typeName;
        }
        String typeName = type.typePath.substring(type.typePath.lastIndexOf('.') + 1);
        String oldImport = this.imports.get(typeName);
        String fullType;
        if (oldImport != null) {
            if (oldImport.equals(type.typePath)) {
                fullType = typeName;
            } else {
                fullType = type.typePath;
            }
        } else {
            this.imports.put(typeName, type.typePath);
            fullType = typeName;
        }
        if (type.isWildcard) {
            fullType = "? extends " + fullType;
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

    private String getPropertyName(FieldDeclaration fDec) {
        String name = fDec.name.real();
        if (name.isEmpty()) {
            return "UNKNOWN";
        } else {
            return Character.toUpperCase(name.charAt(0)) + name.substring(1);
        }
    }

    private String getPrimFieldType(FieldDeclaration fDec) {
        Class<?> fType = getFieldType(fDec).type;
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

    private String handleName() {
        return this.classDec.type.typeName + "Handle";
    }

    private String className() {
        return this.classDec.type.typeName + "Class";
    }

    private void addLine() {
        this.builder.append('\n');
    }

    private void addLine(String line) {
        if (line.endsWith("}")) {
            this.indent(-1);
        }
        if (line.endsWith("{")) {
            this.builder.append('\n');
        }
        for (int i = 0; i < indent; i++) {
            this.builder.append("    ");
        }
        this.builder.append(line);
        if (line.endsWith("{")) {
            this.indent(1);
        } else if (!line.endsWith("}")) {
            this.builder.append(';');
        }
        this.builder.append('\n');
    }

    private void indent(int indent) {
        this.indent += indent;
    }
}
