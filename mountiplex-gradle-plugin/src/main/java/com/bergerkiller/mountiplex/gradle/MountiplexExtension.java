package com.bergerkiller.mountiplex.gradle;

import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;

/**
 * Extends the properties of {@link GenerateReflection}
 */
public abstract class MountiplexExtension {

    /**
     * The source template .txt file. This is the first source .txt file read.
     *
     * @return source template .txt file
     */
    public abstract Property<String> getSource();

    /**
     * The package root where generated Handle classes are placed
     *
     * @return target package
     */
    public abstract Property<String> getGenerated();

    /**
     * Variables used while parsing the template files. Optional.
     *
     * @return variables
     */
    public abstract MapProperty<String, String> getVariables();
}
