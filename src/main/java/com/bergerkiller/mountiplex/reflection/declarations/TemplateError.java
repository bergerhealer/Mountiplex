package com.bergerkiller.mountiplex.reflection.declarations;

/**
 * Error thrown when an #error directive is hit
 */
public class TemplateError extends IllegalStateException {
    private static final long serialVersionUID = 7429146143115133423L;

    public TemplateError(String message) {
        super(message);
    }
}
