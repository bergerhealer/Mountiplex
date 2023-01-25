package com.bergerkiller.mountiplex.gradle;

import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;

public abstract class MountiplexExtension {
    public abstract Property<String> getSource();

    public abstract Property<String> getGenerated();

    public abstract MapProperty<String, String> getVariables();
}
