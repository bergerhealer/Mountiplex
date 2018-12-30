package com.bergerkiller.mountiplex.reflection.declarations;

import java.lang.reflect.Type;
import java.util.LinkedList;

import com.bergerkiller.mountiplex.reflection.util.StringBuffer;

/**
 * Declares a list of parameters, surrounded by ()
 */
public class ParameterListDeclaration extends Declaration {
    public final ParameterDeclaration parameters[];

    public ParameterListDeclaration(ClassResolver resolver, Type[] parameters) {
        super(resolver);
        this.parameters = new ParameterDeclaration[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
            this.parameters[i] = new ParameterDeclaration(resolver, parameters[i], "arg" + i);
        }
    }

    public ParameterListDeclaration(ClassResolver resolver, Declaration previous) {
        this(resolver, previous.getPostfix());
    }

    @Deprecated
    public ParameterListDeclaration(ClassResolver resolver, String declaration) {
        this(resolver, StringBuffer.of(declaration));
    }

    public ParameterListDeclaration(ClassResolver resolver, StringBuffer declaration) {
        super(resolver);

        // Invalid declarations are forced by passing null
        if (declaration == null) {
            this.parameters = new ParameterDeclaration[0];
            this.setInvalid();
            return;
        }

        // Find index of first (, only skipping over open spaces
        boolean foundStart = false;
        boolean foundEnd = false;
        for (int cidx = 0; cidx < declaration.length(); cidx++) {
            char c = declaration.charAt(cidx);
            if (!foundStart && c == '(') {
                // Update index
                this.setPostfix(declaration.substring(cidx));
                foundStart = true;
            } else if (c == ')') {
                // Can never have 2x a ), is invalid
                if (foundEnd) {
                    foundStart = false;
                    break;
                }
                // Empty parameter list
                foundEnd = true;
            } else if (c != ' ') {
                // Invalid character. Break lookup.
                if (foundEnd) {
                    this.setPostfix(declaration.substring(cidx));
                }
                break;
            }
        }
        if (!foundStart) {
            this.parameters = new ParameterDeclaration[0];
            this.setInvalid();
            return;
        }
        if (foundEnd) {
            this.parameters = new ParameterDeclaration[0];
            return;
        }

        LinkedList<ParameterDeclaration> params = new LinkedList<ParameterDeclaration>();
        StringBuffer postfix = getPostfix();
        do {
            // Skip first character, will be either ( or ,
            this.setPostfix(postfix.substring(1));

            // Parse next parameter
            params.add(nextParameter(params.size()));

            // Check for end
            postfix = getPostfix();
        } while (postfix != null && postfix.length() > 0 && postfix.charAt(0) == ',');

        // Trim trailing )
        if (postfix != null && postfix.charAt(0) == ')') {
            this.setPostfix(postfix.substring(1));
        }

        this.parameters = params.toArray(new ParameterDeclaration[params.size()]);
    }

    @Override
    public double similarity(Declaration other) {
    	if (!(other instanceof ParameterListDeclaration)) {
    		return 0.0;
    	}
    	ParameterListDeclaration pl = (ParameterListDeclaration) other;

    	// Noth no-args, fully similar
    	if (pl.parameters.length == 0 && this.parameters.length == 0) {
    		return 1.0;
    	}

		// Same number of parameters - simple calculation
    	if (pl.parameters.length == this.parameters.length) {
    		double similarity = 0.0;
    		for (int i = 0; i < this.parameters.length; i++) {
    			similarity += this.parameters[i].similarity(pl.parameters[i]);
    		}
    		similarity /= this.parameters.length;
    		return similarity;
    	}

    	// Compare up to the amount of arguments we have. Missing arguments count towards failure.
    	int minCnt = Math.min(pl.parameters.length, this.parameters.length);
    	double similarity = 0.0;
    	for (int i = 0; i < minCnt; i++) {
    		similarity += this.parameters[i].similarity(pl.parameters[i]);
    	}
    	similarity /= Math.max(pl.parameters.length, this.parameters.length);
    	return similarity;
    }

    @Override
    public boolean match(Declaration declaration) {
        if (declaration instanceof ParameterListDeclaration) {
            ParameterListDeclaration param = (ParameterListDeclaration) declaration;
            if (parameters.length == param.parameters.length) {
                for (int i = 0; i < parameters.length; i++) {
                    if (!parameters[i].match(param.parameters[i])) {
                        return false;
                    }
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public String toString(boolean identity) {
        if (!isValid()) {
            return "??[" + _initialDeclaration + "]??";
        }
        String str = "(";
        for (int i = 0; i < parameters.length; i++) {
            if (i > 0) {
                str += ", ";
            }
            str += parameters[i].toString(identity);
        }
        return str + ")";
    }

    @Override
    public boolean isResolved() {
        for (int i = 0; i < parameters.length; i++) {
            if (!parameters[i].isResolved()) {
                return false;
            }
        }
        return true;
    }

    @Override
    protected void debugString(StringBuilder str, String indent) {
        str.append(indent).append("ParameterList {\n");
        str.append(indent).append("  declaration=").append(this._initialDeclaration).append('\n');
        str.append(indent).append("  postfix=").append(this.getPostfix()).append('\n');
        for (ParameterDeclaration p : this.parameters) {
            p.debugString(str, indent + "  ");
        }
        str.append(indent).append("}\n");
    }
}
