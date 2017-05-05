package com.bergerkiller.mountiplex.reflection.declarations;

import java.lang.reflect.Modifier;
import java.util.HashMap;

/**
 * The declaration of number of Field or Method modifiers.
 */
public class ModifierDeclaration extends Declaration {
    private static final HashMap<String, Integer> _tokens = new HashMap<String, Integer>();
    private static final int _token_mask = (Modifier.PUBLIC | Modifier.PRIVATE | Modifier.PROTECTED | Modifier.STATIC | Modifier.FINAL | Modifier.VOLATILE | Modifier.TRANSIENT);
    private final int _modifiers;
    private final String _modifiersStr;
    private final boolean _constant;
    private final boolean _unknown;

    static {
        int[] modifiers = new int[] {
                Modifier.ABSTRACT, Modifier.FINAL, Modifier.NATIVE,
                Modifier.PRIVATE, Modifier.PROTECTED, Modifier.PUBLIC, Modifier.STATIC,
                Modifier.STRICT, Modifier.SYNCHRONIZED, Modifier.TRANSIENT, Modifier.VOLATILE
        };
        for (int modifier : modifiers) {
            _tokens.put(Modifier.toString(modifier), modifier);
        }
    }

    public ModifierDeclaration(ClassResolver resolver, int modifiers) {
        super(resolver);
        this._modifiers = modifiers;
        this._modifiersStr = Modifier.toString(modifiers);
        this._constant = false;
        this._unknown = false;
    }

    public ModifierDeclaration(ClassResolver resolver, String declaration) {
        super(resolver, declaration);

        // Invalid declarations are forced by passing null
        if (declaration == null) {
            this._modifiers = 0;
            this._modifiersStr = "";
            this._constant = false;
            this._unknown = false;
            this.setInvalid();
            return;
        }

        boolean isConstant = false;
        boolean isUnknown = false;
        int modifiers = 0;
        String modifiersStr = "";
        String postfix = declaration;
        while (true) {
            // Trim spaces from start of String
            int startIdx = 0;
            while (startIdx < postfix.length() && postfix.charAt(startIdx) == ' ') {
                startIdx++;
            }

            // Find the first space from the current position
            int spaceIdx = postfix.indexOf(' ', startIdx);
            if (spaceIdx != -1) {
                String token = postfix.substring(startIdx, spaceIdx);
                Integer m = _tokens.get(token);
                boolean validToken = false;
                if (m != null) {
                    modifiers |= m.intValue();
                    validToken = true;
                } else if (token.equals("unknown")) {
                    isUnknown = true;
                    validToken = true;
                } else if (token.equals("constant")) {
                    //isConstant = true;
                    //validToken = true;
                }
                if (validToken) {
                    if (modifiersStr.length() > 0) {
                        modifiersStr += " ";
                    }
                    modifiersStr += token;
                    postfix = postfix.substring(spaceIdx + 1);
                    continue;
                }
            }

            // Not a modifier; update postfix
            postfix = postfix.substring(startIdx);
            break;
        }
        isConstant = Modifier.isFinal(modifiers); // do we need a 'constant' modifier?
        this._modifiers = modifiers;
        this._modifiersStr = modifiersStr;
        this._constant = isConstant;
        this._unknown = isUnknown;
        this.setPostfix(postfix);
    }

    /**
     * Gets whether the custom 'unknown' modifier is set.
     * This modifier indicates that the upcoming declaration has no known purpose,
     * and should be omitted from the exposed API.
     * 
     * @return True if unknown, False if not
     */
    public final boolean isUnknown() {
        return this._unknown;
    }

    /**
     * Gets whether the final modifier is set
     * 
     * @return True if final, False if not
     */
    public final boolean isFinal() {
        return Modifier.isFinal(this._modifiers);
    }

    /**
     * Gets whether the static modifier is set
     * 
     * @return True if static, False if not
     */
    public final boolean isStatic() {
        return Modifier.isStatic(this._modifiers);
    }

    /**
     * Gets whether the custom 'constant' modifier is set.
     * This modifier indicates that the value is not supposed to be changed at runtime.
     * It is a level up from 'final'
     * @return
     */
    public final boolean isConstant() {
        return this._constant;
    }

    @Override
    public final boolean match(Declaration modifier) {
        if (modifier instanceof ModifierDeclaration) {
            return (this._modifiers & _token_mask) == (((ModifierDeclaration) modifier)._modifiers & _token_mask);
        }
        return false;
    }

    @Override
    public final String toString(boolean identity) {
        if (!isValid()) {
            return "??[" + _initialDeclaration + "]??";
        }
        return _modifiersStr;
    }

    @Override
    public boolean isResolved() {
        return true;
    }

    @Override
    protected void debugString(StringBuilder str, String indent) {
        str.append(indent).append("Modifier {\n");
        str.append(indent).append("  declaration=").append(this._initialDeclaration).append('\n');
        str.append(indent).append("  postfix=").append(this.getPostfix()).append('\n');
        str.append(indent).append("  modifiersStr=").append(this._modifiersStr).append('\n');
        str.append(indent).append("  modifiers=").append(this._modifiers).append('\n');
        str.append(indent).append("}\n");
    }

}
