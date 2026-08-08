package com.bergerkiller.mountiplex.reflection.declarations;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import com.bergerkiller.mountiplex.MountiplexUtil;
import com.bergerkiller.mountiplex.reflection.util.asm.MPLType;

/**
 * Attempts to find the longest common sequence from two
 * sequences of field declarations
 */
public class FieldLCSResolver {
    private final ClassResolver resolver;
    private final Class<?> declaringClass;
    private final FieldDeclaration[] fields;
    private FieldDeclaration[] realFields = null;

    /**
     * Called by the ClassDeclaration to resolve all of the fields defined inside against the actual fields declared in the
     * Class and superclasses. During resolving it first calls resolveName() on the field. If that already returns a discovered
     * field, then that one is returned. If not, it is then matched against all the real fields.
     *
     * @param resolver ClassResolver (configures logging rules)
     * @param declaringClass Type of the ClassDeclaration (where fields are declared, search start)
     * @param fields All the fields to resolve. Array is written to with the resolved field instances.
     */
    static void resolve(ClassResolver resolver, Class<?> declaringClass, FieldDeclaration[] fields) {
        // Short-circuit
        if (fields.length == 0) {
            return;
        }

        (new FieldLCSResolver(resolver, declaringClass, fields)).resolve();
    }

    private FieldLCSResolver(ClassResolver resolver, Class<?> declaringClass, FieldDeclaration[] fields) {
        this.resolver = resolver;
        this.declaringClass = declaringClass;
        this.fields = fields;
    }

    private void resolve() {
        // First perform name resolution
        // Skip fields that are already discovered (remapped fields for example)
        List<FieldDeclarationReference> remainingInputFields = new ArrayList<>();
        for (int i = 0; i < this.fields.length; i++) {
            FieldDeclaration nameResolved = this.fields[i].resolveName();
            if (nameResolved.isDiscovered()) {
                this.fields[i] = nameResolved;
                continue;
            }

            remainingInputFields.add(new FieldDeclarationReference(nameResolved, i));
        }

        // If nothing to match, skip everything else
        if (remainingInputFields.isEmpty()) {
            return;
        }

        // Match
        FieldDeclaration[] realFields = this.getRealFields();
        List<Pair> pairs = lcs(remainingInputFields, realFields);

        // Register all successful pairs
        Iterator<FieldLCSResolver.Pair> succIter = pairs.iterator();
        while (succIter.hasNext()) {
            FieldLCSResolver.Pair pair = succIter.next();
            if (pair.a != null && pair.b != null) {
                applyLCSResult(pair.a, pair.b);
                succIter.remove();
            }
        }

        // For remaining fields we could not match, try to match a real field exactly
        // This is needed when fields are for some reason out of order
        succIter = pairs.iterator();
        while (succIter.hasNext()) {
            FieldLCSResolver.Pair pair = succIter.next();
            if (pair.a == null) {
                continue;
            }

            for (FieldDeclaration realField : realFields) {
                if (pair.a.field.match(realField)) {
                    applyLCSResult(pair.a, realField);
                    succIter.remove();
                    break;
                }
            }
        }

        // Log all fields we could not find in our template
        // The fields in the underlying Class are not important (yet)
        for (FieldLCSResolver.Pair failPair : pairs) {
            if (failPair.b == null && failPair.a != null && !failPair.a.field.modifiers.isOptional()) {
                if (failPair.bb.length > 0) {
                    logAlternatives("field", failPair.bb, failPair.a.field, false);
                } else {
                    logAlternatives("field", realFields, failPair.a.field, false);
                }
            }
        }
    }

    private void applyLCSResult(FieldDeclarationReference ref, FieldDeclaration field) {
        ref.field.copyFieldFrom(field);
        this.fields[ref.index] = ref.field;
    }

    private FieldDeclaration[] getRealFields() {
        if (realFields == null) {
            Map<String, Field> realRefFieldsByName = new HashMap<>();

            // Find all fields the type contains, including non-private fields declared in super classes
            boolean excludePrivateFields = false;
            Class<?> currentType = this.declaringClass;
            while (currentType != null && currentType != Object.class && !currentType.isInterface()) {
                try {
                    for (java.lang.reflect.Field field : currentType.getDeclaredFields()) {
                        if (!excludePrivateFields || !Modifier.isPrivate(field.getModifiers())) {
                            realRefFieldsByName.putIfAbsent(MPLType.getName(field), field);
                        }
                    }
                } catch (Throwable t) {
                    if (this.resolver.getLogErrors()) {
                        MountiplexUtil.LOGGER.log(Level.SEVERE, "Failed to get declared fields of " + currentType, t);
                    }
                    break;
                }

                currentType = currentType.getSuperclass();
                excludePrivateFields = true;
            }

            this.realFields = new FieldDeclaration[realRefFieldsByName.size()];
            {
                int i = 0;
                for (java.lang.reflect.Field realRefField : realRefFieldsByName.values()) {
                    try {
                        realFields[i] = new FieldDeclaration(resolver, realRefField);
                    } catch (Throwable t) {
                        if (this.resolver.getLogErrors()) {
                            MountiplexUtil.LOGGER.log(Level.WARNING, "Failed to read field " + realRefField, t);
                        }
                    }

                    i++;
                }
            }
        }

        return this.realFields;
    }

    public static <T extends Declaration> void logAlternatives(String category, T[] alternatives, T declaration, boolean isRequirement) {
        if (!declaration.getResolver().getLogErrors()) {
            return;
        }
        if (isRequirement) {
            MountiplexUtil.LOGGER.warning("Requirement was not found in " + declaration.getResolver().getDeclaredClassName() + ":");
        } else {
            MountiplexUtil.LOGGER.warning("A class member of " + declaration.getResolver().getDeclaredClassName() + " was not found!");
        }
        if (alternatives.length == 0) {
            MountiplexUtil.LOGGER.warning("Failed to find " + category + " " + declaration + " (No alternatives)");
        } else {
            ArrayList<T> sorted = new ArrayList<T>(Arrays.asList(alternatives));
            Declaration.sortSimilarity(declaration, sorted);
            MountiplexUtil.LOGGER.warning("Failed to find " + category + " " + declaration + " - Alternatives:");
            int limit = 8;
            for (T alter : sorted) {
                MountiplexUtil.LOGGER.warning("  - " + alter);
                if (--limit == 0) {
                    break;
                }
            }
        }
    }

    public static List<Pair> lcs(FieldDeclaration[] a, FieldDeclaration[] b) {
        List<FieldDeclarationReference> asRefs = new ArrayList<>(a.length);
        for (int i = 0; i < a.length; i++) {
            asRefs.add(new FieldDeclarationReference(a[i], i));
        }
        return lcs(asRefs, b);
    }

    private static List<Pair> lcs(List<FieldDeclarationReference> a, FieldDeclaration[] b) {
        // First phase: cut the declaration lists up at the long-name fields
        // These long-names have been decompiled and are expected to be correct
        ArrayList<Pair> pairs = new ArrayList<Pair>();
        ArrayList<Sequence> sequences = new ArrayList<Sequence>();
        Sequence mainSequence = new Sequence(a, Arrays.asList(b));
        Sequence skipped = new Sequence();
        for (FieldDeclarationReference da : mainSequence.a) {
            if (da.field.name.isObfuscated()) {
                skipped.a.add(da);
                continue;
            }

            FieldDeclaration found = null;
            while (!mainSequence.b.isEmpty()) {
                FieldDeclaration db = mainSequence.b.remove(0);
                if (!da.field.match(db)) {
                    skipped.b.add(db);
                } else {
                    found = db;
                    break;
                }
            }

            pairs.add(new Pair(da, found));
            if (found == null) {
                // Not found. Reset skipped sequence and continue
                mainSequence.b.addAll(skipped.b);
                skipped.b.clear();
            } else {
                // All the exact fields in skipped b are not found as well
                int i = 0;
                while (i < skipped.b.size()) {
                    FieldDeclaration db = skipped.b.get(i);
                    if (db.name.isObfuscated()) {
                        i++;
                    } else {
                        pairs.add(new Pair(null, db));
                        skipped.b.remove(i);
                    }
                }

                // Handle skipped cases with nonexistent pairing
                if (skipped.a.size() > 0 && skipped.b.size() == 0) {
                    for (FieldDeclarationReference fd : skipped.a) {
                        pairs.add(new Pair(fd, null));
                    }
                } else if (skipped.a.size() == 0 && skipped.b.size() > 0) {
                    for (FieldDeclaration fd : skipped.b) {
                        pairs.add(new Pair(null, fd));
                    }
                } else if (skipped.a.size() > 0 && skipped.b.size() > 0) {
                    sequences.add(skipped);
                }
                skipped = new Sequence();
            }
        }
        skipped.b.addAll(mainSequence.b);

        // Handle skipped cases with nonexistent pairing
        if (skipped.a.size() > 0 && skipped.b.size() == 0) {
            for (FieldDeclarationReference fd : skipped.a) {
                pairs.add(new Pair(fd, null));
            }
        } else if (skipped.a.size() == 0 && skipped.b.size() > 0) {
            for (FieldDeclaration fd : skipped.b) {
                pairs.add(new Pair(null, fd));
            }
        } else if (skipped.a.size() > 0 && skipped.b.size() > 0) {
            sequences.add(skipped);
        }

        // Now we have a bunch of sequences we must perform non-exact LCS on
        //TODO! Make this more resillient!
        for (Sequence seq : sequences) {
            FieldDeclarationReference[] aa = seq.a.toArray(new FieldDeclarationReference[0]);
            FieldDeclaration[] bb = seq.b.toArray(new FieldDeclaration[0]);
            if (seq.a.size() > 0 && seq.b.size() > 0) {
                Iterator<FieldDeclarationReference> fa_iter = seq.a.iterator();
                while (fa_iter.hasNext()) {
                    FieldDeclarationReference fa = fa_iter.next();
                    Iterator<FieldDeclaration> fb_iter = seq.b.iterator();
                    while (fb_iter.hasNext()) {
                        FieldDeclaration fb = fb_iter.next();
                        if (fa.field.name.value().equals(fb.name.value())) {
                            // If not actually matching, do not pair up, but still show alternatives!
                            if (fa.field.match(fb)) {
                                pairs.add(new Pair(fa, fb, aa, bb));
                            } else {
                                pairs.add(new Pair(fa, null, aa, bb));
                                pairs.add(new Pair(null, fb, aa, bb));
                            }
                            fa_iter.remove();
                            fb_iter.remove();
                            break;
                        }
                    }
                }
            }
            // Add any missing fields as 'failed'
            for (FieldDeclarationReference fa : seq.a) {
                pairs.add(new Pair(fa, null, aa, bb));
            }
            for (FieldDeclaration fb : seq.b) {
                pairs.add(new Pair(null, fb, aa, bb));
            }
        }

        return pairs;
    }

    /**
     * Field Declaration, with the index in the "fields" array it lives at.
     */
    public static class FieldDeclarationReference {
        public final FieldDeclaration field;
        public final int index;

        public FieldDeclarationReference(FieldDeclaration field, int index) {
            this.field = field;
            this.index = index;
        }
    }

    public static class Pair {
        public final FieldDeclarationReference a;
        public final FieldDeclaration b;
        public final FieldDeclarationReference[] aa;
        public final FieldDeclaration[] bb;

        public Pair(FieldDeclarationReference a, FieldDeclaration b) {
            this.a = a;
            this.b = b;
            this.aa = new FieldDeclarationReference[0];
            this.bb = new FieldDeclaration[0];
        }

        public Pair(
                FieldDeclarationReference a, FieldDeclaration b,
                FieldDeclarationReference[] aa, FieldDeclaration[] bb
        ) {
            this.a = a;
            this.b = b;
            this.aa = aa;
            this.bb = bb;
        }
    }

    public static class Sequence {
        public final ArrayList<FieldDeclarationReference> a = new ArrayList<>();
        public final ArrayList<FieldDeclaration> b = new ArrayList<>();

        public Sequence() {
        }

        public Sequence(List<FieldDeclarationReference> a, List<FieldDeclaration> b) {
            this.a.addAll(a);
            this.b.addAll(b);
        }
    }
}
