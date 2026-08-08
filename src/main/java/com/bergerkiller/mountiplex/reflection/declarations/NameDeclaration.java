package com.bergerkiller.mountiplex.reflection.declarations;

import com.bergerkiller.mountiplex.MountiplexUtil;
import com.bergerkiller.mountiplex.reflection.declarations.parsers.ParserStringBuffer;
import com.bergerkiller.mountiplex.reflection.util.StringBuffer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Declaration for a method or field name
 */
public class NameDeclaration extends Declaration {
    private final String _value;
    private final String _alias;
    private final String _firstReal;

    public NameDeclaration(ClassResolver resolver, String value, String alias) {
        super(resolver);
        this._value = value;
        this._alias = alias;
        this._firstReal = computeFirstReal(alias, value);
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
            this._value = "";
            this._alias = null;
            this._firstReal = "";
            this.setInvalid();
            return;
        }

        // Locate the name
        int startIdx = -1;
        StringBuffer name = null;
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
                this._value = "arg" + optionalIdx;
                this._alias = null;
                this._firstReal = this._value;
            } else {
                this._value = "";
                this._alias = null;
                this._firstReal = "";
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
        int alias_idx = name.lastIndexOf(':');
        if (alias_idx == -1) {
            // No alias at all
            this._value = name.toString();
            this._alias = null;
            this._firstReal = this._value;
        } else {
            // Handle alias, also extract the first real alias in case multiple are specified
            StringBuffer alias = name.substring(0, alias_idx);
            name = name.substring(alias_idx + 1);
            this._value = name.toString();
            this._alias = alias.toString();
            this._firstReal = computeFirstReal(this._alias, this._value);
        }
    }

    /**
     * Gets the name value. This is the actual, current name
     * of the declaration, and is likely obfuscated.
     * 
     * @return name
     */
    public final String value() {
        return _value;
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
        return _alias != null ? _alias : _value;
    }

    /**
     * Resolvers can include additional aliases to the alias field. This methods extracts
     * the very first alias specified in a template, if any. This makes this value more
     * useful than just for logging.
     *
     * @return first real name
     */
    public final String firstReal() {
        return _firstReal;
    }

    private static String computeFirstReal(String alias, String name) {
        if (alias != null) {
            int index = alias.indexOf(':');
            if (index != -1) {
                return alias.substring(0, index);
            } else {
                return alias;
            }
        } else {
            return name;
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
    	return _value.length() <= 2;
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
        for (int cidx = 0; cidx < this._value.length(); cidx++) {
            if (this._value.charAt(cidx) != '?') {
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
    	if (n._value.equals(this._value)) {
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
    		return MountiplexUtil.similarity(n._value, this._value);
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
                return other._value.equals(this._value);
            }
        }
        return false;
    }

    private boolean matchAlias(String otherAlias) {
        return this.firstReal().equals(otherAlias);
    }

    @Override
    public String toString(boolean identity) {
        if (!isValid()) {
            return "??[" + _initialDeclaration + "]??";
        }
        if (_alias == null) {
            return _value;
        } else {
            return _alias + ":" + _value;
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
        str.append(indent).append("  name=").append(this._value).append('\n');
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
    public NameDeclaration setValue(NameDeclaration newName) {
        // Return newName if this name has no aliases, and already equals the first alias
        if (!this.hasAlias() && this.value().equals(newName.firstReal())) {
            return newName;
        }

        // If the new name has no alias, or the alias already equals this name's value, then
        // perform an optimized method that avoids parsing text.
        if (!newName.hasAlias() || newName.alias().equals(this.value())) {
            return setValueExact(newName.value());
        }

        // Extract alias and value part to append
        String aliasToAppend = newName.alias();

        // Omit the first alias if it already equals this name's value (duplicate aliases)
        int firstAliasEnd = aliasToAppend.indexOf(':');
        if (firstAliasEnd != -1) {
            String firstAlias = aliasToAppend.substring(0, firstAliasEnd);
            if (firstAlias.equals(this.value())) {
                aliasToAppend = aliasToAppend.substring(firstAliasEnd + 1);
            }
        }

        return new NameDeclaration(this.getResolver(), newName.value(),
                this.alias() + ":" + this.value() + ":" + aliasToAppend);
    }

    /**
     * Changes the name {@link #value()}, preserving the original alias. If no alias was set, then the
     * original name becomes the alias. If the new name is equal to the current value, then
     * this same declaration is returned.
     * 
     * @param newName The new name, can not be null
     * @return new name declaration with the name changed
     */
    public NameDeclaration setValue(String newName) {
        // In case somebody is specifying a stringified name that has aliases,
        // defer to the NameDeclaration rename logic to avoid breakage.
        int aliasEnd = newName.lastIndexOf(':');
        if (aliasEnd != -1) {
            return setValue(new NameDeclaration(this.getResolver(),
                    newName.substring(aliasEnd + 1), /* value */
                    newName.substring(0, aliasEnd) /* alias */
            ));
        }

        // Value specified (no :)
        return setValueExact(newName);
    }

    private NameDeclaration setValueExact(String newValue) {
        if (newValue.equals(this.value())) {
            return this;
        } else if (this.hasAlias()) {
            return new NameDeclaration(this.getResolver(), newValue, this.alias() + ":" + this.value());
        } else {
            return new NameDeclaration(this.getResolver(), newValue, this.value());
        }
    }
}
