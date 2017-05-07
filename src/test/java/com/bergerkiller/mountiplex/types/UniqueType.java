package com.bergerkiller.mountiplex.types;

public class UniqueType {
    public String name;

    public UniqueType() {
        this.name = this.getClass().getSimpleName();
    }

}
