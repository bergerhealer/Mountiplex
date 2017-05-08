package com.bergerkiller.mountiplex.reflection.declarations;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;

import com.bergerkiller.mountiplex.MountiplexUtil;

/**
 * Declares the full contents of a Class
 */
public class ClassDeclaration extends Declaration {
    public final ModifierDeclaration modifiers;
    public final TypeDeclaration type;
    public final ConstructorDeclaration[] constructors;
    public final MethodDeclaration[] methods;
    public final FieldDeclaration[] fields;
    public final boolean is_interface;

    public ClassDeclaration(ClassResolver resolver, Class<?> type) {
        super(resolver.clone());
        this.is_interface = type.isInterface();
        this.type = TypeDeclaration.fromClass(type);
        this.modifiers = new ModifierDeclaration(getResolver(), type.getModifiers());

        LinkedList<ConstructorDeclaration> constructors = new LinkedList<ConstructorDeclaration>();
        LinkedList<MethodDeclaration> methods = new LinkedList<MethodDeclaration>();
        LinkedList<FieldDeclaration> fields = new LinkedList<FieldDeclaration>();

        for (java.lang.reflect.Constructor<?> constructor : type.getDeclaredConstructors()) {
            constructors.add(new ConstructorDeclaration(getResolver(), constructor));
        }
        for (java.lang.reflect.Field field : type.getDeclaredFields()) {
            fields.add(new FieldDeclaration(getResolver(), field));
        }
        for (java.lang.reflect.Method method : type.getDeclaredMethods()) {
            methods.add(new MethodDeclaration(getResolver(), method));
        }

        this.constructors = constructors.toArray(new ConstructorDeclaration[constructors.size()]);
        this.methods = methods.toArray(new MethodDeclaration[methods.size()]);
        this.fields = fields.toArray(new FieldDeclaration[fields.size()]);
    }

    public ClassDeclaration(ClassResolver resolver, String declaration) {
        super(resolver.clone(), declaration);

        // Modifiers, stop when invalid
        this.modifiers = nextModifier();
        if (!this.isValid()) {
            this.type = nextType();
            this.constructors = new ConstructorDeclaration[0];
            this.methods = new MethodDeclaration[0];
            this.fields = new FieldDeclaration[0];
            this.is_interface = false;
            return;
        }

        // Class or interface? Then parse class/interface type
        String postfix = this.getPostfix();
        this.is_interface = postfix.startsWith("interface ");
        if (!this.is_interface && !postfix.startsWith("class ")) {
            this.type = nextType();
            this.constructors = new ConstructorDeclaration[0];
            this.methods = new MethodDeclaration[0];
            this.fields = new FieldDeclaration[0];
            this.setInvalid();
            return;
        }
        setPostfix(postfix.substring(this.is_interface ? 10 : 6));
        this.type = nextType();
        if (!this.isValid()) {
            this.constructors = new ConstructorDeclaration[0];
            this.methods = new MethodDeclaration[0];
            this.fields = new FieldDeclaration[0];
            return;
        }

        // Find start of class definitions {
        postfix = getPostfix();
        boolean foundClassStart = false;
        int startIdx = -1;
        for (int cidx = 0; cidx < postfix.length(); cidx++) {
            char c = postfix.charAt(cidx);
            if (c == '{') {
                foundClassStart = true;
            } else if (foundClassStart && !MountiplexUtil.containsChar(c, space_chars)) {
                startIdx = cidx;
                break;
            }
        }
        if (startIdx == -1) {
            this.constructors = new ConstructorDeclaration[0];
            this.methods = new MethodDeclaration[0];
            this.fields = new FieldDeclaration[0];
            this.setInvalid();
            return;
        }
        this.setPostfix(postfix.substring(startIdx));

        LinkedList<ConstructorDeclaration> constructors = new LinkedList<ConstructorDeclaration>();
        LinkedList<MethodDeclaration> methods = new LinkedList<MethodDeclaration>();
        LinkedList<FieldDeclaration> fields = new LinkedList<FieldDeclaration>();
        while ((postfix = getPostfix()) != null && postfix.length() > 0) {
            if (postfix.charAt(0) == '}') {
                trimWhitespace(1);
                break;
            }

            MethodDeclaration mdec = new MethodDeclaration(getResolver(), postfix);
            if (mdec.isValid()) {
                methods.add(mdec);
                setPostfix(mdec.getPostfix());
                trimLine();
                continue;
            }
            ConstructorDeclaration cdec = new ConstructorDeclaration(getResolver(), postfix);
            if (cdec.isValid()) {
                constructors.add(cdec);
                setPostfix(cdec.getPostfix());
                trimLine();
                continue;
            }
            FieldDeclaration fdec = new FieldDeclaration(getResolver(), postfix);
            if (fdec.isValid()) {
                fields.add(fdec);
                setPostfix(fdec.getPostfix());
                trimLine();
                continue;
            }
            break;
        }
        this.constructors = constructors.toArray(new ConstructorDeclaration[constructors.size()]);
        this.methods = methods.toArray(new MethodDeclaration[methods.size()]);
        this.fields = fields.toArray(new FieldDeclaration[fields.size()]);

        // Verify all the fields exist
        if (this.type.isResolved()) {
            resolveFields();
            resolveMethods();
        }
    }

    private void resolveFields() {
        java.lang.reflect.Field[] realRefFields = this.type.type.getDeclaredFields();
        FieldDeclaration[] realFields = new FieldDeclaration[realRefFields.length];
        for (int i = 0; i < realFields.length; i++) {
            try {
                realFields[i] = new FieldDeclaration(getResolver(), realRefFields[i]);
            } catch (Throwable t) {
                MountiplexUtil.LOGGER.log(Level.WARNING, "Failed to read field " + realRefFields[i], t);
            }
        }
        List<FieldLCSResolver.Pair> pairs = FieldLCSResolver.lcs(this.fields, realFields);

        // Register all successful pairs
        Iterator<FieldLCSResolver.Pair> succIter = pairs.iterator();
        while (succIter.hasNext()) {
            FieldLCSResolver.Pair pair = succIter.next();
            if (pair.a != null && pair.b != null) {
                pair.a.field = pair.b.field;
                succIter.remove();
            }
        }

        // Log all fields we could not find in our template
        // The fields in the underlying Class are not important (yet)
        for (FieldLCSResolver.Pair failPair : pairs) {
            if (failPair.b == null) {
                MountiplexUtil.LOGGER.warning("Failed to find field " + failPair.a);
            }
        }
    }

    private void resolveMethods() {
        java.lang.reflect.Method[] realRefMethods = this.type.type.getDeclaredMethods();
        MethodDeclaration[] realMethods = new MethodDeclaration[realRefMethods.length];
        for (int i = 0; i < realMethods.length; i++) {
            try {
                realMethods[i] = new MethodDeclaration(getResolver(), realRefMethods[i]);
            } catch (Throwable t) {
                MountiplexUtil.LOGGER.log(Level.WARNING, "Failed to read field " + realRefMethods[i], t);
            }
        }

        // Connect the methods together
        for (int i = 0; i < this.methods.length; i++) {
            MethodDeclaration method = this.methods[i];
            boolean found = false;
            for (int j = 0; j < realMethods.length; j++) {
                if (realMethods[j].match(method)) {
                    method.method = realMethods[j].method;
                    found = true;
                    break;
                }
            }
            if (!found) {
                MountiplexUtil.LOGGER.warning("Failed to find method " + method);
            }
        }
    }

    @Override
    public boolean isResolved() {
        return false;
    }

    @Override
    public boolean match(Declaration declaration) {
        return false; // don't even bother
    }

    @Override
    public String toString(boolean identity) {
        String str = this.modifiers.toString(identity);
        if (str.length() > 0) {
            str += " ";
        }
        str += this.is_interface ? "interface " : "class ";
        str += this.type.toString(identity);
        str += " {\n";
        for (FieldDeclaration fdec : this.fields) str += "    " + fdec.toString(identity) + "\n";
        for (ConstructorDeclaration cdec : this.constructors) str += "    " + cdec.toString(identity) + "\n";
        for (MethodDeclaration mdec : this.methods) str += "    " + mdec.toString(identity) + "\n";
        str += "}";
        return str;
    }

    @Override
    protected void debugString(StringBuilder str, String indent) {
        
    }

}
