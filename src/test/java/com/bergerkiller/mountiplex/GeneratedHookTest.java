package com.bergerkiller.mountiplex;

import static org.junit.Assert.*;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Test;

import com.bergerkiller.mountiplex.reflection.util.fast.GeneratedHook;
import com.bergerkiller.mountiplex.reflection.util.fast.Invoker;

/**
 * Tests that the {@link GeneratedHook} works according to spec.
 * This hook is also used by the ClassInterceptor/ClassHook.
 */
@SuppressWarnings("unused")
public class GeneratedHookTest {

    public static class Frog {
        // This is hooked
        public String croak(int arg) {
            return "Frog::croak(" + arg + ")";
        }

        // This isn't
        public String croakNormally(int arg) {
            return "Frog::croakNormal(" + arg + ")";
        }
    }

    public interface Jumper {
        void jump();
    }

    public interface GrassEater {
        boolean eatGrass(String name);
    }

    public static abstract class RabbitBase {
        public void liftEars() {
        }

        public void raisePaws() {
        }

        private void pleaseDontNoticeMe() {
        }

        public static void staticsShouldBeIgnored() {
        }
    }

    public static class Rabbit extends RabbitBase implements Jumper, GrassEater {
        public final void jump() {
        }

        @Override
        public void raisePaws() {
        }

        @Override
        public boolean eatGrass(String name) {
            return false;
        }
    }

    // Creates a frog that can Invoker::croak() rather than Frog::croak()
    private Frog createHookTestFrog() {
        final Invoker<String> croakCallback = (instance, args) -> "Invoker::croak(" + args[0] + ")";

        Class<? extends Frog> generated = GeneratedHook.generate(Frog.class.getClassLoader(), Frog.class, Collections.emptyList(), method -> {
            if (method.getName().equals("croak")) {
                return croakCallback;
            } else {
                return null;
            }
        });

        try {
            return generated.getConstructor().newInstance();
        } catch (Throwable t) {
            throw MountiplexUtil.uncheckedRethrow(t);
        }
    }

    @Test
    public void testHookMethod() {
        Frog instance = createHookTestFrog();

        // Call method normally
        assertEquals("Invoker::croak(1)", instance.croak(1));
    }

    @Test
    public void testHookSuperInvoker() throws Throwable {
        Frog instance = createHookTestFrog();

        // Create two super-invokers, one for a method we have hooked, and one we haven't.
        // We expect both to work fine, where the unhooked method simply calls it.
        Invoker<String> super_croak = GeneratedHook.createSuperInvoker(instance.getClass(),
                Frog.class.getDeclaredMethod("croak", int.class));
        Invoker<String> super_croakNormally = GeneratedHook.createSuperInvoker(instance.getClass(),
                Frog.class.getDeclaredMethod("croakNormally", int.class));

        // Check both invokers work as expected, both invoke() and invokeVA()
        assertEquals("Frog::croak(1)", super_croak.invoke(instance, 1));
        assertEquals("Frog::croak(2)", super_croak.invokeVA(instance, 2));
        assertEquals("Frog::croakNormal(3)", super_croakNormally.invoke(instance, 3));
        assertEquals("Frog::croakNormal(4)", super_croakNormally.invokeVA(instance, 4));

        // Invalid arguments should be handled properly
        try {
            super_croak.invoke(instance, "not_an_int");
            fail("Invoke worked with a String, it should fail!");
        } catch (ClassCastException | IllegalArgumentException ex) { /* expected */ }
        assertEquals("Invoker::croak(5)", instance.croak(5));

        // Check invokeVA also
        try {
            super_croak.invokeVA(instance, "not_an_int");
            fail("Invoke worked with a String, it should fail!");
        } catch (ClassCastException | IllegalArgumentException ex) { /* expected */ }
        assertEquals("Invoker::croak(5)", instance.croak(5));
    }

    @Test
    public void testMethodsMatching() {
        // Generate a rabbit, track what methods we try to override
        // Try to override/hook all methods we can
        // As a result, we should not see the same method come by multiple times
        final Set<String> methodNames = new HashSet<>();
        final AtomicBoolean noDuplicateMethods = new AtomicBoolean(true);
        GeneratedHook.generate(Rabbit.class.getClassLoader(), Rabbit.class, Collections.emptyList(), method -> {
            if (!methodNames.add(method.getName())) {
                System.err.println("Duplicate method detected: " + method);
                noDuplicateMethods.set(false);
            }

            // Return an invoker at all times, so the first match is selected
            return (instance, args) -> null;
        });

        // All overridable, defined in own class or base class
        assertTrue(methodNames.contains("eatGrass"));
        assertTrue(methodNames.contains("liftEars"));
        assertTrue(methodNames.contains("raisePaws"));

        // The jump() is final and should not be overridable
        assertFalse(methodNames.contains("jump"));

        // Private and static should be hidden too
        assertFalse(methodNames.contains("pleaseDontNoticeMe"));
        assertFalse(methodNames.contains("staticsShouldBeIgnored"));

        // Object methods should all be visible
        assertTrue(methodNames.contains("equals"));
        assertTrue(methodNames.contains("hashCode"));

        // There should be no duplicate methods (because of interfaces), should be suppressed
        // If logic is broken, raisePaws() and eatGrass() may show up twice, which would be wrong
        assertTrue(noDuplicateMethods.get());
    }
}
