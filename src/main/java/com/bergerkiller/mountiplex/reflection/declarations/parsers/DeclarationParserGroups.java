package com.bergerkiller.mountiplex.reflection.declarations.parsers;

import com.bergerkiller.mountiplex.MountiplexUtil;

import java.util.Arrays;
import java.util.stream.Stream;

import static com.bergerkiller.mountiplex.reflection.declarations.parsers.DeclarationParserTypes.*;

/**
 * Groups {@link DeclarationParserTypes} based on different contexts
 */
public class DeclarationParserGroups {

    /**
     * Base group of parsers supported everywhere in the template
     */
    public static final DeclarationParser[] BASE = new DeclarationParser[] {
            COMMENT,
            BOOTSTRAP,
            RESOLVER,
            SET_VARIABLE,
            REQUIREMENT,
            REMAPPING,
            ERROR,
            WARNING
    };

    /**
     * Parsers active in the source area (outside of class definitions)
     */
    public static final DeclarationParser[] SOURCE = Stream.concat(Arrays.stream(BASE), Stream.of(
            PACKAGE,
            IMPORT,
            INCLUDE,
            SET_PATH
    )).toArray(DeclarationParser[]::new);

    /**
     * Parsers active in the class area
     */
    public static final DeclarationParser[] CLASS = Stream.concat(MountiplexUtil.toStream(CODE_BLOCK),
            Arrays.stream(BASE)).toArray(DeclarationParser[]::new);;
}
