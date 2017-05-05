package com.bergerkiller.mountiplex.reflection.declarations;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;

public class TemplateGenerator {
    private ClassDeclaration classDec = null;
    private File rootDir = null;
    private String path = "";
    private StringBuilder builder = new StringBuilder();
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
        this.indent = 0;
        String packagePath = this.path;
        String extendedHandleType = "Template.Handle";
        String extendedClassType = "Template.Class";

        addLine("package " + packagePath);
        addLine();
        addLine("import " + Template.class.getName());
        addLine("public class " + handleName() + " extends " + extendedHandleType + " {");
        {
            addLine("public static final " + className() + " T = new " + className() + "()");

            for (FieldDeclaration fDec : this.classDec.fields) {
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

            addLine("public static class " + className() + " extends " + extendedClassType + " {");
            {
                // Constructor declaring where the class should be initialized
                addLine("protected " + className() + "() {");
                addLine("init(" + className() + ".class, \"" + this.classDec.type.typePath + "\")");
                addLine("}");
                addLine();

                // Fields
                for (FieldDeclaration fDec : this.classDec.fields) {
                    String fieldTypeStr = "Template.Field";
                    String primTypeStr = getPrimFieldType(fDec);
                    if (!primTypeStr.isEmpty()) {
                        fieldTypeStr += "." + primTypeStr;
                    } else {
                        fieldTypeStr += "<" + getFieldTypeStr(fDec) + ">";
                    }
                    addLine("public final " + fieldTypeStr + " " + fDec.name.real() + " = new " + fieldTypeStr + "()");
                }
            }
            addLine("}");
        }

        addLine("}");

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

    private String getFieldTypeStr(FieldDeclaration fDec) {
        String typeStr = (fDec.type.isResolved() ? fDec.type.typePath : "Object");
        return typeStr;
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
        if (fDec.type.isResolved()) {
            Class<?> fType = fDec.type.type;
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
