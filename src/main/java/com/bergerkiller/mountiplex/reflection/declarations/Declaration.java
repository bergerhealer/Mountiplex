package com.bergerkiller.mountiplex.reflection.declarations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Level;

import com.bergerkiller.mountiplex.MountiplexUtil;
import com.bergerkiller.mountiplex.reflection.declarations.parsers.context.DeclarationParserContext;
import com.bergerkiller.mountiplex.reflection.declarations.parsers.ParserStringBuffer;
import com.bergerkiller.mountiplex.reflection.util.ExtendedClassWriter;
import com.bergerkiller.mountiplex.reflection.util.StringBuffer;

import javassist.CannotCompileException;
import javassist.NotFoundException;

/**
 * Base class for Declaration implementations
 */
public abstract class Declaration {
    private final ParserStringBuffer _postfix = new ParserStringBuffer();
    private String _longDeclare = null;
    protected final StringBuffer _initialDeclaration;
    private final ClassResolver _resolver;
    private List<String> _errors = Collections.emptyList();
    private List<String> _warnings = Collections.emptyList();
    private boolean _loggedErrorsAndWarnings = true;

    public Declaration(ClassResolver resolver) {
        this._resolver = resolver;
        this._initialDeclaration = StringBuffer.EMPTY;
        this._postfix.set(StringBuffer.EMPTY);
    }

    public Declaration(ClassResolver resolver, String initialDeclaration) {
        this(resolver, StringBuffer.of(initialDeclaration));
    }

    public Declaration(ClassResolver resolver, StringBuffer initialDeclaration) {
        this._resolver = resolver;
        this._initialDeclaration = initialDeclaration;
        this._postfix.set(initialDeclaration);
    }

    /**
     * Gets the {@link ClassResolver} used to resolve Class types from name
     * 
     * @return class resolver
     */
    public final ClassResolver getResolver() {
        return this._resolver;
    }

    protected final ModifierDeclaration nextModifier() {
        return updatePostfix(new ModifierDeclaration(this._resolver, this._postfix.get()));
    }

    protected final NameDeclaration nextName() {
        return updatePostfix(new NameDeclaration(this._resolver, this._postfix.get()));
    }

    protected final NameDeclaration nextName(int optionalIdx) {
        return updatePostfix(new NameDeclaration(this._resolver, this._postfix.get(), optionalIdx));
    }

    protected final TypeDeclaration nextType() {
        return updatePostfix(TypeDeclaration.parse(this._resolver, this._postfix.get()));
    }

    protected final ParameterDeclaration nextParameter(int paramIdx) {
        return updatePostfix(new ParameterDeclaration(this._resolver, this._postfix.get(), paramIdx));
    }

    protected final ParameterListDeclaration nextParameterList() {
        return updatePostfix(new ParameterListDeclaration(this._resolver, this._postfix.get()));
    }

    protected final ClassDeclaration nextClass() {
        return updatePostfix(new ClassDeclaration(this._resolver.clone(), this._postfix.get()));
    }

    /**
     * Updates the text that exists after this declaration, by taking
     * over the information from the last child declaration.
     * 
     * @param lastDeclaration
     */
    protected final <T extends Declaration> T updatePostfix(T lastDeclaration) {
        this._postfix.set(lastDeclaration.getPostfix());
        return lastDeclaration;
    }

    /**
     * Gets the text put after this declaration.
     * If this declaration invalid according to {@link #isValid()} this function returns null.
     * 
     * @return declaration postfix
     */
    public final StringBuffer getPostfix() {
        return _postfix.get();
    }

    /**
     * Gets a mutable version of {@link #getPostfix()}, which includes helper methods
     * for parsing. The postfix of this declaration can be updated this way.
     *
     * @return mutable declaration postfix, used for parsing
     */
    protected final ParserStringBuffer getParserPostfix() {
        return _postfix;
    }

    /**
     * Sets the text that exists after this declaration.
     * To mark this declaration as invalid, pass a null postfix.
     * Implementation use only.
     *
     * @param postfix to set to
     */
    protected final void setPostfix(StringBuffer postfix) {
        this._postfix.set(postfix);
    }

    /**
     * Checks whether the declaration is in a valid syntax
     * 
     * @return True if the syntax is valid, False if not
     */
    public final boolean isValid() {
        return !_postfix.isNull();
    }

    /**
     * Marks this declaration as invalid because of a syntax error
     */
    protected final void setInvalid() {
        this._postfix.set(null);
    }

    /**
     * Checks whether all the Class types could be resolved for this Declaration
     * 
     * @return True if this declaration was fully resolved, False if not
     */
    public abstract boolean isResolved();

    /**
     * Gets a List of #warning directives encountered while parsing the template
     *
     * @return warnings
     */
    public final List<String> getTemplateWarnings() {
        return this._warnings;
    }

    /**
     * Gets a List of #error directives encountered while parsing the template
     *
     * @return errors
     */
    public final List<String> getTemplateErrors() {
        return this._errors;
    }

    /**
     * Logs any warnings encountered during the parsing of this declaration, and
     * only logs them once. If template errors were declared, those are logged once
     * and a {@link TemplateError} is thrown every time.
     * 
     * @throws TemplateError
     */
    public final void checkTemplateErrors() {
        if (!this._loggedErrorsAndWarnings) {
            this._loggedErrorsAndWarnings = true;

            // Log warnings
            if (!this._warnings.isEmpty()) {
                if (this._warnings.size() > 1) {
                    MountiplexUtil.LOGGER.log(Level.SEVERE, "Warnings in template for declaring class: " + this._resolver.getDeclaredClassName());
                    MountiplexUtil.LOGGER.log(Level.WARNING, "Multiple template warnings for " +
                            getTemplateLogIdentity() + ":");
                    for (String warning : this._warnings) {
                        MountiplexUtil.LOGGER.log(Level.WARNING, "  - " + warning);
                    }
                } else {
                    MountiplexUtil.LOGGER.log(Level.SEVERE, "Warning in template for declaring class: " + this._resolver.getDeclaredClassName());
                    MountiplexUtil.LOGGER.log(Level.WARNING, "Template warning for " +
                            getTemplateLogIdentity() + ": " + this._warnings.get(0));
                }
            }

            // Format multiple errors so the exception isn't so long and weird
            if (!this._errors.isEmpty()) {
                if (this._errors.size() > 1) {
                    MountiplexUtil.LOGGER.log(Level.SEVERE, "Errors in template for declaring class: " + this._resolver.getDeclaredClassName());
                    MountiplexUtil.LOGGER.log(Level.SEVERE, "Multiple template errors for " + getTemplateLogIdentity() + ":");
                    for (String error : this._errors) {
                        MountiplexUtil.LOGGER.log(Level.SEVERE, "  - " + error);
                    }
                } else {
                    MountiplexUtil.LOGGER.log(Level.SEVERE, "Error in template for declaring class: " + this._resolver.getDeclaredClassName());
                }
            }
        }

        if (!this._errors.isEmpty()) {
            if (this._errors.size() > 1) {
                throw new TemplateError("Multiple template errors for " + getTemplateLogIdentity());
            } else {
                throw new TemplateError("Template error for " + getTemplateLogIdentity() +
                        ": " + this._errors.get(0));
            }
        }
    }

    /**
     * Gets a human-readable name to be used when logging warnings and errors about this declaration
     *
     * @return template identity
     */
    protected String getTemplateLogIdentity() {
        return this.toString(false);
    }

    /**
     * Checks if the declaration specified matches this declaration.
     * 
     * @param declaration to check against
     * @return True if matching, False if not
     */
    public abstract boolean match(Declaration declaration);

    /**
     * Creates a stringified version of this Declaration.
     * When identity is true, all names should be fully declared to
     * allow for identity ( {@link #equals(Object)} ) checks. Extra metadata
     * information such as aliases and casting should be omitted.
     * 
     * @param identity whether to create an identity representation
     * @return stringified version of this Declaration
     */
    public abstract String toString(boolean identity);

    /**
     * Gets a human-readable String representation of this Declaration
     * 
     * @return declaration String
     */
    @Override
    public final String toString() {
        return toString(false);
    }

    /**
     * Gets a debug String showing deep nested information about this parsed declaration.
     * If generic types are showing hidden differences, this method can show this difference.
     * 
     * @return debug string
     */
    public String debugString() {
        StringBuilder str = new StringBuilder();
        debugString(str, "");
        return str.toString();
    }

    protected abstract void debugString(StringBuilder str, String indent); // must implement

    /**
     * Computes the similarity between this declaration and another.
     * The higher the return value, the more similar the declarations are.
     * A return value of 1.0 indicates the declarations are equal.
     * A return value of 0.0 indicates the declarations are completely different,
     * or could not be compared.
     * 
     * @param other to compute a difference for
     * @return difference between this declaration and the other (0.0 - 1.0)
     */
    public abstract double similarity(Declaration other);

    /**
     * Attempts to discover the actual declaration object this declaration references by
     * looking it up in the internal registry.
     * 
     * @return found matching declaration, null if one does not exist
     */
    public Declaration discover() {
        if (!this.isValid() || !this.isResolved()) {
            return null;
        }
        return this;
    }

    /**
     * When {@link #discover()} fails and we really need this declaration,
     * this method may be called to log all the alternatives that slightly
     * resemble this declaration.
     */
    public void discoverAlternatives() {
    }

    @Override
    public final boolean equals(Object other) {
        if (this == other) {
            return true;
        } else if (other instanceof Declaration) {
            Declaration d = (Declaration) other;
            if (d._longDeclare == null) {
                d._longDeclare = d.toString(true);
            }
            if (this._longDeclare == null) {
                this._longDeclare = this.toString(true);
            }
            return d._longDeclare.equals(this._longDeclare);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        if (this._longDeclare == null) {
            this._longDeclare = this.toString(true);
        }
        return this._longDeclare.hashCode();
    }

    /**
     * Called while decoding a method body to turn the original requirement declaration into valid compilable
     * code that calls the requirements added by {@link #addAsRequirement(ExtendedClassWriter, Requirement, String)}.
     * Extra metadata for later use during {@link #addAsRequirement(ExtendedClassWriter, Requirement, String)}
     * can be stored inside the requirement parameter.
     *
     * @param requirement The requirement that is currently being processed that uses this declaration
     * @param body The full StringBuilder buffer of the body in which source code is being parsed
     * @param instanceName The object token on which this declaration is called
     * @param requirementName The name of the requirement, right of the #-token
     * @param instanceStartIdx Start index into the body, which is the index to the first character of the instance name
     * @param nameEndIdx End index into the body, which is the exclusive index of the last character of the requirement name
     */
    public void modifyBodyRequirement(Requirement requirement, StringBuilder body, String instanceName, String requirementName, int instanceStartIdx, int nameEndIdx) {
        throw new UnsupportedOperationException("Declaration " + toString() + " can not be added as requirement");
    }

    /**
     * Called by the code invoker as part of generating the class used to invoke a runtime-generated
     * method. The declaration should take care to add all the details to the class required
     * to work properly.<br>
     * <br>
     * The writer can be assumed to be writing a singleton class. As such, singleton member fields can be added
     * without problems.
     *
     * @param writer The ExtendedClassWriter to which to add the generated methods/fields/etc. for this requirement
     * @param requirement The requirement that is currently being processed that uses this declaration
     * @param name of the declaration
     */
    public void addAsRequirement(ExtendedClassWriter<?> writer, Requirement requirement, String name)
            throws CannotCompileException, NotFoundException
    {
        throw new UnsupportedOperationException("Declaration " + toString() + " can not be added as requirement");
    }

    /** Context provided to parsers. Can be extended to add more callbacks */
    protected class BaseDeclarationParserContext implements DeclarationParserContext {
        @Override
        public ClassResolver getResolver() {
            return _resolver;
        }

        @Override
        public ParserStringBuffer getBuffer() {
            return _postfix;
        }

        @Override
        public void addWarning(String warning) {
            if (_warnings.isEmpty()) {
                _warnings = new ArrayList<>();
            }
            _warnings.add(warning);
            _loggedErrorsAndWarnings = false;
        }

        @Override
        public void addError(String error) {
            if (_errors.isEmpty()) {
                _errors = new ArrayList<>();
            }
            _errors.add(error);
            _loggedErrorsAndWarnings = false;
        }
    }

    /**
     * Sorts a list of declarations based on the similarity with a compared type.
     * The most similar declarations are sorted to the beginning of the list.
     * 
     * @param compare declaration to compare with
     * @param list to sort
     */
    public static <T extends Declaration> void sortSimilarity(final T compare, List<T> list) {
        Collections.sort(list, createSimilarityComparator(compare));
    }

    /**
     * Sorts an array of declarations based on the similarity with a compared type.
     * The most similar declarations are sorted to the beginning of the array.
     * 
     * @param compare declaration to compare with
     * @param array to sort
     */
    public static <T extends Declaration> void sortSimilarity(final T compare, T[] array) {
        Arrays.sort(array, createSimilarityComparator(compare));
    }

    private static <T extends Declaration> Comparator<T> createSimilarityComparator(final T compare) {
        return (o1, o2) -> {
            double s1 = compare.similarity(o1);
            double s2 = compare.similarity(o2);
            if (s1 == s2) {
                return 0;
            } else if (s1 > s2) {
                return -1;
            } else {
                return 1;
            }
        };
    }

    /**
     * Parses a declaration from a String Buffer into the matching declaration type.
     * This method supports methods, constructors and fields.
     * 
     * @param resolver
     * @param declaration
     * @return parsed declaration, null if none could be found
     */
    public static Declaration parseDeclaration(ClassResolver resolver, String declaration) {
        return parseDeclaration(resolver, StringBuffer.of(declaration));
    }

    /**
     * Parses a declaration from a String Buffer into the matching declaration type.
     * This method supports methods, constructors and fields.
     * 
     * @param resolver
     * @param declaration
     * @return parsed declaration, null if none could be found
     */
    public static Declaration parseDeclaration(ClassResolver resolver, StringBuffer declaration) {
        MethodDeclaration mdec = new MethodDeclaration(resolver, declaration);
        if (mdec.isValid()) {
            return mdec;
        }
        ConstructorDeclaration cdec = new ConstructorDeclaration(resolver, declaration);
        if (cdec.isValid()) {
            return cdec;
        }
        FieldDeclaration fdec = new FieldDeclaration(resolver, declaration);
        if (fdec.isValid()) {
            return fdec;
        }
        return null;
    }
}
