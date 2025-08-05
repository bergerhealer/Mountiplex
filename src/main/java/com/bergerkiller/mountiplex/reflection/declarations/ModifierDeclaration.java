package com.bergerkiller.mountiplex.reflection.declarations;

import java.lang.reflect.Modifier;
import java.util.HashMap;

import com.bergerkiller.mountiplex.reflection.util.StringBuffer;

/**
 * The declaration of number of Field or Method modifiers.
 */
public class ModifierDeclaration extends Declaration {
    private static final HashMap<StringBuffer, Integer> _tokens = new HashMap<StringBuffer, Integer>();
    private static final int _token_mask = (Modifier.PUBLIC | Modifier.PRIVATE | Modifier.PROTECTED | Modifier.STATIC | Modifier.VOLATILE | Modifier.TRANSIENT);
    private final int _modifiers;
    private final String _modifiersStr;
    private final boolean _final;
    private final boolean _unknown;
    private final boolean _optional;
    private final boolean _readonly;
    private final boolean _rawtype;

    static {
        int[] modifiers = new int[] {
                Modifier.ABSTRACT, Modifier.FINAL, Modifier.NATIVE,
                Modifier.PRIVATE, Modifier.PROTECTED, Modifier.PUBLIC, Modifier.STATIC,
                Modifier.STRICT, Modifier.SYNCHRONIZED, Modifier.TRANSIENT, Modifier.VOLATILE
        };
        for (int modifier : modifiers) {
            _tokens.put(StringBuffer.of(Modifier.toString(modifier)), modifier);
        }
    }

    public ModifierDeclaration(ClassResolver resolver, int modifiers) {
        super(resolver);
        this._modifiers = modifiers;
        this._modifiersStr = Modifier.toString(modifiers);
        this._final = Modifier.isFinal(this._modifiers);
        this._unknown = false;
        this._optional = false;
        this._rawtype = false;
        this._readonly = false;
    }

    @Deprecated
    public ModifierDeclaration(ClassResolver resolver, String declaration) {
        this(resolver, StringBuffer.of(declaration));
    }

    public ModifierDeclaration(ClassResolver resolver, StringBuffer declaration) {
        super(resolver, declaration);

        // Invalid declarations are forced by passing null
        if (declaration == null) {
            this._modifiers = 0;
            this._modifiersStr = "";
            this._final = false;
            this._unknown = false;
            this._optional = false;
            this._rawtype = false;
            this._readonly = false;
            this.setInvalid();
            return;
        }

        boolean isUnknown = false;
        boolean isOptional = false;
        boolean isReadonly = false;
        boolean isRawtype = false;
        int modifiers = 0;
        String modifiersStr = "";
        StringBuffer postfix = declaration;
        while (true) {
            // Trim spaces from start of String
            int startIdx = 0;
            while (startIdx < postfix.length() && postfix.charAt(startIdx) == ' ') {
                startIdx++;
            }

            // Find the first space from the current position
            int spaceIdx = postfix.indexOf(' ', startIdx);
            if (spaceIdx != -1) {
                StringBuffer token = postfix.substring(startIdx, spaceIdx);
                Integer m = _tokens.get(token);
                boolean validToken = false;
                if (m != null) {
                    modifiers |= m.intValue();
                    validToken = true;
                } else if (token.equals("unknown")) {
                    isUnknown = true;
                    validToken = true;
                } else if (token.equals("optional")) {
                    isOptional = true;
                    validToken = true;
                } else if (token.equals("readonly")) {
                    isReadonly = true;
                    validToken = true;
                } else if (token.equals("rawtype")) {
                    isRawtype = true;
                    validToken = true;
                }
                if (validToken) {
                    if (m != null) {
                        if (modifiersStr.length() > 0) {
                            modifiersStr += " ";
                        }
                        modifiersStr += token;
                    }
                    postfix = postfix.substring(spaceIdx + 1);
                    continue;
                }
            }

            // Not a modifier; update postfix
            postfix = postfix.substring(startIdx);
            break;
        }

        this._final = Modifier.isFinal(modifiers);
        this._modifiers = modifiers;
        this._modifiersStr = modifiersStr;
        this._unknown = isUnknown;
        this._optional = isOptional;
        this._readonly = isReadonly;
        this._rawtype = isRawtype;
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
     * Gets whether the custom 'readonly' modifier is set.
     * This modifier indicates that the upcoming declaration refers to an object that can
     * only be read from, not written to. For field declarations, this means only getting the
     * field is possible, setting is not.<br>
     * <br>
     * In generated code, no setter code will be included.
     * 
     * @return True if readonly, False if not
     */
    public final boolean isReadonly() {
        return this._readonly;
    }

    /**
     * Gets whether the custom 'optional' modifier is set.
     * This modifier indicates that the upcoming declaration does not have to be present
     * on the server at all times, and will require User-code switching to work with.<br>
     * <br>
     * In generated code, all declarations names will have <i>opt_</i> prefixed, and no
     * getter/setter code will be included.
     * 
     * @return True if optional, False if not
     */
    public final boolean isOptional() {
        return this._optional;
    }

    /**
     * Gets whether the custom 'rawtype' modifier is set.
     * This modifier indicates that the upcoming declaration contains raw unparameterized types,
     * and the warning for it must be ignored.
     * 
     * @return True if a raw type is used
     */
    public final boolean isRawtype() {
        return this._rawtype;
    }

    /**
     * Gets whether the final modifier is set
     * 
     * @return True if final, False if not
     */
    public final boolean isFinal() {
        return this._final;
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
     * Gets whether the public modifier is set
     * 
     * @return True if public, False if not
     */
    public final boolean isPublic() {
        return Modifier.isPublic(this._modifiers);
    }

    @Override
    public double similarity(Declaration other) {
    	if (!(other instanceof ModifierDeclaration)) {
    		return 0.0;
    	}
    	ModifierDeclaration m = (ModifierDeclaration) other;

    	// Obtain the total number of modifiers used in both declarations
    	int allModifiers = (m._modifiers | this._modifiers) & _token_mask;
    	int numberTotal = 0;
    	int numberMatched = 0;
    	for (int b = 0; b < 31; b++) {
    		int mask = (1 << b);
    		if ((allModifiers & mask) != 0) {
    			numberTotal++;
    			if ((m._modifiers & mask) == (this._modifiers & mask)) {
    				numberMatched++;
    			}
    		}
    	}

    	// Turn this into a double value. Do handle /div0
    	if (numberTotal == 0) {
    		return 1.0;
    	} else {
    		return (double) numberMatched / (double) numberTotal;
    	}
    }

    @Override
    public final boolean match(Declaration modifier) {
        if (!(modifier instanceof ModifierDeclaration)) {
            return false;
        }
        ModifierDeclaration other = (ModifierDeclaration) modifier;
        if ((this._modifiers & _token_mask) != (other._modifiers & _token_mask)) {
            return false;
        }
        return true;
    }

    /**
     * Gets the protection level:
     * <ul>
     *     <li>0: public</li>
     *     <li>1: protected</li>
     *     <li>2: package-private</li>
     *     <li>3: private</li>
     * </ul>
     *
     * @return Protection level
     */
    public int getProtectionLevel() {
        if (Modifier.isPublic(_modifiers)) {
            return 0; // public
        } else if (Modifier.isProtected(_modifiers)) {
            return 1; // protected
        } else if (!Modifier.isPrivate(_modifiers)) {
            return 2; // package-private
        } else {
            return 3; // private
        }
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
