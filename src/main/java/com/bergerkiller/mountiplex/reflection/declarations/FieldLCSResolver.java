package com.bergerkiller.mountiplex.reflection.declarations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
            if (da.name.value().length() <= 2) {
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
                    if (db.name.value().length() <= 2) {
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
        //TODO!
        

        return pairs;
    }

    public static class Pair {
        public final FieldDeclaration a;
        public final FieldDeclaration b;

        public Pair(FieldDeclaration a, FieldDeclaration b) {
            this.a = a;
            this.b = b;
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
