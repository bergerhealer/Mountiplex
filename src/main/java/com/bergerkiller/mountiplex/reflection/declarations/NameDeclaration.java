package com.bergerkiller.mountiplex.reflection.declarations;

import com.bergerkiller.mountiplex.MountiplexUtil;

/**
 * Declaration for a method or field name
 */
public class NameDeclaration extends Declaration {
    private final String _name;
    private final String _alias;

    public NameDeclaration(ClassResolver resolver, String name, String alias) {
        super(resolver);
        this._name = name;
        this._alias = alias;
    }

    public NameDeclaration(ClassResolver resolver, String declaration) {
        this(resolver, declaration, -1);
    }

    public NameDeclaration(ClassResolver resolver, String declaration, int optionalIdx) {
        super(resolver, declaration);

        // Invalid declarations are forced by passing null
        if (declaration == null) {
            this._name = "";
            this._alias = null;
            this.setInvalid();
            return;
        }

        // Locate the name
        int startIdx = -1;
        String name = null;
        String alias = null;
        for (int cidx = 0; cidx < declaration.length(); cidx++) {
            char c = declaration.charAt(cidx);

            // Ignore spaces at the start
            if (startIdx == -1 && c == ' ') {
                continue;
            }

            boolean validNameChar = !MountiplexUtil.containsChar(c, invalid_name_chars);

            // Verify the first character of the name is valid, and set it
            if (startIdx == -1) {
                if (validNameChar) {
                    startIdx = cidx; 
                } else {
                    break; // not a valid start of the name
                }
            }

            // The first invalid character finishes the name declaration
            if (!validNameChar && name == null) {
                name = declaration.substring(startIdx, cidx);
            }

            // The first non-space after the name starts the next postfix part
            if (name != null && c != ' ') {
                this.setPostfix(declaration.substring(cidx));
                break;
            }
        }

        // Start index not found means the name is invalid
        if (startIdx == -1) {
            // When an optional index is set and no name is available, allow for a fallback name
            if (optionalIdx != -1) {
                this._name = "arg" + optionalIdx;
                this._alias = null;
            } else {
                this._name = "";
                this._alias = null;
                this.setInvalid();
            }
            return;
        }

        // Fallback if no end delimiter found
        if (name == null) {
            name = declaration.substring(startIdx);
            this.setPostfix("");
        }

        // Check for alias (:)
        int alias_idx = name.indexOf(':');
        if (alias_idx != -1) {
            alias = name.substring(0, alias_idx);
            name = name.substring(alias_idx + 1);
        }

        this._name = name;
        this._alias = alias;
    }

    /**
     * Gets the name value
     * 
     * @return name
     */
    public final String value() {
        return _name;
    }

    /**
     * Gets the alias used for this name. Is null if no alias is used.
     * 
     * @return name alias
     */
    public final String alias() {
        return _alias;
    }

    /**
     * Returns the {@link #alias} if an alias is specified, otherwise returns the normal {@link #value()}
     * 
     * @return real name
     */
    public final String real() {
        return _alias != null ? _alias : _name;
    }

    /**
     * Gets whether this Name Declaration has an alias defined
     * 
     * @return True if an alias is set, False if not
     */
    public final boolean hasAlias() {
        return _alias != null;
    }

    /**
     * Gets whether this name is an obfuscated name
     * 
     * @return True if the name is obfuscated (such as 'aB', 'e', '_Y')
     */
    public final boolean isObfuscated() {
    	return _name.length() <= 2;
    }

    /**
     * Gets whether this name denotes only an alias, and no matching name.
     * This is the case when using names like <i>clear:???</i>.
     * 
     * @return True if this name only contains an Alias, False if not
     */
    public final boolean isAliasOnly() {
        if (!this.hasAlias()) {
            return false;
        }
        for (int cidx = 0; cidx < this._name.length(); cidx++) {
            if (this._name.charAt(cidx) != '?') {
                return false;
            }
        }
        return true;
    }

    @Override
    public double similarity(Declaration other) {
    	if (!(other instanceof NameDeclaration)) {
    		return 0.0;
    	}
    	NameDeclaration n = (NameDeclaration) other;
    	if (n._name.equals(this._name)) {
    		return 1.0;
    	}
    	if (n.isObfuscated() && this.isObfuscated()) {
    		// Names are both obfuscated so comparisons do not really make sense here
    		// Return a constant '0.9' to allow for further matching
    		return 0.9;
    	} else if (n.isObfuscated() || this.isObfuscated()) {
    		// One is obfuscated, the other is deobfuscated. A field being deobfuscated
    		// is quite rare, so assume they are not similar (0.1)
    		return 0.1;
    	} else {
    		// Both are deobfuscated, calculate similarity of the two names
    		return MountiplexUtil.similarity(n._name, this._name);
    	}
    }
    
    @Override
    public boolean match(Declaration declaration) {
        if (declaration instanceof NameDeclaration) {
            // When both specify an alias, we check against the alias instead.
            // When an alias-only name is used ('clear:???'), we allow comparing between alias and name.
            // Runtime-created declarations (from Reflection methods) never have aliases
            // This allows for matching two declarations both referring to the same, renamed method
            NameDeclaration other = (NameDeclaration) declaration;
            if (this.hasAlias() && other.hasAlias()) {
                return other._alias.equals(this._alias);
            } else if (this.isAliasOnly()) {
                return this._alias.equals(other._alias) && other.isAliasOnly();
            } else if (other.isAliasOnly()) {
                return false;
            } else {
                return other._name.equals(this._name);
            }
        }
        return false;
    }

    @Override
    public String toString(boolean identity) {
        if (!isValid()) {
            return "??[" + _initialDeclaration + "]??";
        }
        if (_alias == null || identity) {
            return _name;
        } else {
            return _alias + ":" + _name;
        }
    }

    @Override
    public boolean isResolved() {
        return true; // no types to resolve
    }

    @Override
    protected void debugString(StringBuilder str, String indent) {
        str.append(indent).append("Name {\n");
        str.append(indent).append("  declaration=").append(this._initialDeclaration).append('\n');
        str.append(indent).append("  postfix=").append(this.getPostfix()).append('\n');
        str.append(indent).append("  name=").append(this._name).append('\n');
        str.append(indent).append("  alias=").append(this._alias).append('\n');
        str.append(indent).append("}\n");
    }

}
