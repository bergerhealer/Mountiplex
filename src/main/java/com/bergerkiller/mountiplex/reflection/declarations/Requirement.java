package com.bergerkiller.mountiplex.reflection.declarations;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Requirement added using #require that must be included during code generation
 */
public class Requirement {
    public final String name;
    public final Declaration declaration;
    private Map<String, Object> metadata;

    public Requirement(String name, Declaration declaration) {
        this.name = name;
        this.declaration = declaration;
        this.metadata = Collections.emptyMap();
    }

    /**
     * Checks whether a property is stored by the name specified
     * 
     * @param name
     * @return True if the property is stored
     */
    public boolean hasProperty(String name) {
        return metadata.containsKey(name);
    }

    /**
     * Retrieves a property that was previously set using {@link #setProperty(name, value)}
     * 
     * @param name
     * @return property value, null if not stored
     */
    @SuppressWarnings("unchecked")
    public <T> T property(String name) {
        return (T) metadata.get(name);
    }

    /**
     * Stores a 'true' boolean value under a name, for use with {@link #hasProperty(name)} only
     * when the value is not important.
     * 
     * @param name
     */
    public void setProperty(String name) {
        setProperty(name, Boolean.TRUE);
    }

    /**
     * Stores a property value under a key
     * 
     * @param name key
     * @param value property value
     */
    public void setProperty(String name, Object value) {
        if (metadata.isEmpty()) {
            metadata = Collections.singletonMap(name, value);
        } else {
            if (metadata.size() == 1) {
                metadata = new HashMap<String, Object>(metadata);
            }
            metadata.put(name, value);
        }
    }
}
