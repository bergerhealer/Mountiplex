package com.bergerkiller.mountiplex.types;

import java.util.ArrayList;
import java.util.Collection;

public class CustomListType<T> extends ArrayList<T> {
    private static final long serialVersionUID = -6386699042462305544L;

    public CustomListType(Collection<T> input) {
        super(input);
    }
}
