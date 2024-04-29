package com.bergerkiller.mountiplex.reflection.declarations;

import com.bergerkiller.mountiplex.MountiplexUtil;
import com.bergerkiller.mountiplex.reflection.declarations.parsers.ParserStringBuffer;
import com.bergerkiller.mountiplex.reflection.util.StringBuffer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

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

    @Deprecated
    public NameDeclaration(ClassResolver resolver, String declaration) {
        this(resolver, StringBuffer.of(declaration));
    }

    @Deprecated
    public NameDeclaration(ClassResolver resolver, String declaration, int optionalIdx) {
        this(resolver, StringBuffer.of(declaration), optionalIdx);
    }

    public NameDeclaration(ClassResolver resolver, StringBuffer declaration) {
        this(resolver, declaration, -1);
    }

    public NameDeclaration(ClassResolver resolver, StringBuffer declaration, int optionalIdx) {
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
        StringBuffer name = null;
        StringBuffer alias = null;
        for (int cidx = 0; cidx < declaration.length(); cidx++) {
            char c = declaration.charAt(cidx);

            // Ignore spaces at the start
            if (startIdx == -1 && c == ' ') {
                continue;
            }

            // Note: allow < and > because of <init>
            boolean validNameChar;
            if (c == '<') {
                validNameChar = declaration.substringEquals(cidx, cidx + 6, "<init>") ||
                        declaration.substringEquals(cidx, cidx + 16, "<record_changer>");
            } else if (c == '>') {
                validNameChar = declaration.substringEquals(cidx - 5, cidx + 1, "<init>") ||
                        declaration.substringEquals(cidx - 15, cidx + 1, "<record_changer>");
            } else {
                validNameChar = !MountiplexUtil.containsChar(c, ParserStringBuffer.INVALID_NAME_CHARACTERS);
            }

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
            this.setPostfix(StringBuffer.EMPTY);
        }

        // Check for alias (:)
        int alias_idx = name.indexOf(':');
        if (alias_idx != -1) {
            alias = name.substring(0, alias_idx);
            name = name.substring(alias_idx + 1);
        }

        this._name = (name == null) ? null : name.toString();
        this._alias = (alias == null) ? null : alias.toString();
    }

    /**
     * Gets the name value. This is the actual, current name
     * of the declaration, and is likely obfuscated.
     * 
     * @return name
     */
    public final String value() {
        return _name;
    }

    /**
     * Gets the alias used for this name. Is null if no alias is used.
     * This is a more human-readable version of the name, if available.
     * May include multiple : if multiple aliases exist.
     * 
     * @return name alias
     */
    public final String alias() {
        return _alias;
    }

    /**
     * Returns the {@link #alias} if an alias is specified, otherwise returns the normal {@link #value()}.
     * Only useful for debugging and logging! May include multiple : if multiple aliases
     * exist.
     * 
     * @return real name
     */
    public final String real() {
        return _alias != null ? _alias : _name;
    }

    /**
     * Resolvers can include additional aliases to the alias field. This methods extracts
     * the very first alias specified in a template, if any. This makes this value more
     * useful than just for logging.
     *
     * @return first real name
     */
    public final String firstReal() {
        if (_alias != null) {
            int index = _alias.indexOf(':');
            if (index != -1) {
                return _alias.substring(0, index);
            } else {
                return _alias;
            }
        } else {
            return _name;
        }
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
            NameDeclaration other = (NameDeclaration) declaration;

            // When an alias-only name is used ('clear:???'), we allow comparing between aliases.
            // This allows for matching two declarations both referring to the same, renamed method
            // Runtime-created declarations (from Reflection methods) can have aliases too because of remapping!
            // matchAlias takes care of that by looking for the 'top' alias, denoted by a ':' when remapped.
            if (this.isAliasOnly()) {
                // getName() == getName:???
                return other.matchAlias(this._alias);
            } else if (other.isAliasOnly()) {
                // getName() == getName:???
                return this.matchAlias(other._alias);
            } else {
                return other._name.equals(this._name);
            }
        }
        return false;
    }

    private boolean matchAlias(String otherAlias) {
        String self_name = this.real();
        int top_alias_end = self_name.indexOf(':');
        if (top_alias_end != -1) {
            self_name = self_name.substring(0, top_alias_end);
        }
        return self_name.equals(otherAlias);
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

    /**
     * Changes the name {@link #value()}, preserving the original alias. If no alias was set, then the
     * original name becomes the alias. If the new name is equal to the current value, then
     * this same declaration is returned. If the new name also includes aliases, then those are
     * includes in the alias result.
     *
     * @param newName The new name, can not be null
     * @return new name declaration with the name changed
     */
    public NameDeclaration rename(NameDeclaration newName) {
        if (!newName.hasAlias()) {
            return rename(newName.value());
        }

        // Obtain all known aliases. Omit the alias that is equal to this name.
        List<String> aliases = new ArrayList<>(Arrays.asList(newName.alias().split(":")));
        if (aliases.get(0).equals(this.value())) {
            aliases.remove(0);
        }
        if (aliases.isEmpty()) {
            return rename(newName.value());
        }

        if (newName.value().equals(this.value())) {
            return this; // No need to keep the original aliases
        }

        aliases.add(0, this.value());
        return new NameDeclaration(this.getResolver(), newName.value(),
                String.join(":", aliases));
    }

    /**
     * Changes the name {@link #value()}, preserving the original alias. If no alias was set, then the
     * original name becomes the alias. If the new name is equal to the current value, then
     * this same declaration is returned.
     * 
     * @param newName The new name, can not be null
     * @return new name declaration with the name changed
     */
    public NameDeclaration rename(String newName) {
        if (newName.equals(this.value())) {
            return this;
        } else if (this.hasAlias()) {
            return new NameDeclaration(this.getResolver(), newName, this.alias() + ":" + this.value());
        } else {
            return new NameDeclaration(this.getResolver(), newName, this.value());
        }
    }
}
