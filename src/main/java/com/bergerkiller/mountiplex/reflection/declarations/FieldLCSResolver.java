package com.bergerkiller.mountiplex.reflection.declarations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import com.bergerkiller.mountiplex.MountiplexUtil;

/**
 * Attempts to find the longest common sequence from two
 * sequences of field declarations
 */
public class FieldLCSResolver {

    public static List<Pair> lcs(FieldDeclaration[] a, FieldDeclaration[] b) {
        // First phase: cut the declaration lists up at the long-name fields
        // These long-names have been decompiled and are expected to be correct
        ArrayList<Pair> pairs = new ArrayList<Pair>();
        ArrayList<Sequence> sequences = new ArrayList<Sequence>();
        Sequence mainSequence = new Sequence(Arrays.asList(a), Arrays.asList(b));
        Sequence skipped = new Sequence();
        for (FieldDeclaration da : mainSequence.a) {
            if (da.name.isObfuscated()) {
                skipped.a.add(da);
                continue;
            }

            FieldDeclaration found = null;
            while (!mainSequence.b.isEmpty()) {
                FieldDeclaration db = mainSequence.b.remove(0);
                if (!da.match(db)) {
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
                    for (FieldDeclaration fd : skipped.a) {
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
            for (FieldDeclaration fd : skipped.a) {
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
            FieldDeclaration[] aa = seq.a.toArray(new FieldDeclaration[0]);
            FieldDeclaration[] bb = seq.b.toArray(new FieldDeclaration[0]);
            if (seq.a.size() > 0 && seq.b.size() > 0) {
                Iterator<FieldDeclaration> fa_iter = seq.a.iterator();
                while (fa_iter.hasNext()) {
                    FieldDeclaration fa = fa_iter.next();
                    Iterator<FieldDeclaration> fb_iter = seq.b.iterator();
                    while (fb_iter.hasNext()) {
                        FieldDeclaration fb = fb_iter.next();
                        if (fa.name.value().equals(fb.name.value())) {
                            // If not actually matching, do not pair up, but still show alternatives!
                            if (fa.match(fb)) {
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
            for (FieldDeclaration fa : seq.a) {
                pairs.add(new Pair(fa, null, aa, bb));
            }
            for (FieldDeclaration fb : seq.b) {
                pairs.add(new Pair(null, fb, aa, bb));
            }
        }

        return pairs;
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

    public static void resolve(FieldDeclaration[] inputFields, FieldDeclaration[] realFields) {
        // Match
        List<Pair> pairs = lcs(inputFields, realFields);

        // Register all successful pairs
        Iterator<FieldLCSResolver.Pair> succIter = pairs.iterator();
        while (succIter.hasNext()) {
            FieldLCSResolver.Pair pair = succIter.next();
            if (pair.a != null && pair.b != null) {
                pair.a.copyFieldFrom(pair.b);
                succIter.remove();
            }
        }

        // Log all fields we could not find in our template
        // The fields in the underlying Class are not important (yet)
        for (FieldLCSResolver.Pair failPair : pairs) {
            if (failPair.b == null && !failPair.a.modifiers.isOptional()) {
                if (failPair.bb.length > 0) {
                    logAlternatives("field", failPair.bb, failPair.a, false);
                } else {
                    logAlternatives("field", realFields, failPair.a, false);
                }
            }
        }
    }

    public static class Pair {
        public final FieldDeclaration a;
        public final FieldDeclaration b;
        public final FieldDeclaration[] aa;
        public final FieldDeclaration[] bb;

        public Pair(FieldDeclaration a, FieldDeclaration b) {
            this.a = a;
            this.b = b;
            this.aa = this.bb = new FieldDeclaration[0];
        }

        public Pair(FieldDeclaration a, FieldDeclaration b,
                FieldDeclaration[] aa, FieldDeclaration[] bb) {
            this.a = a;
            this.b = b;
            this.aa = aa;
            this.bb = bb;
        }
    }

    public static class Sequence {
        public final ArrayList<FieldDeclaration> a = new ArrayList<FieldDeclaration>();
        public final ArrayList<FieldDeclaration> b = new ArrayList<FieldDeclaration>();

        public Sequence() {
        }

        public Sequence(List<FieldDeclaration> a, List<FieldDeclaration> b) {
            this.a.addAll(a);
            this.b.addAll(b);
        }
    }
}
