package com.bergerkiller.mountiplex;

import java.lang.reflect.Method;

import org.junit.Test;

import com.bergerkiller.mountiplex.reflection.util.FastMethod;
import com.bergerkiller.mountiplex.types.TestObject;

public class FastStaticMethodTest {

    @Test
    public void testStaticMethod() {
        try {
            final Method m1 = TestObject.class.getDeclaredMethod("g", int.class, int.class);
            final Method m2 = TestObject.class.getDeclaredMethod("h", int.class, int.class);
            m1.setAccessible(true);

            final FastMethod<?> m1_fast = new FastMethod<Object>(m1);
            final FastMethod<?> m2_fast = new FastMethod<Object>(m2);

            TestUtil.measure("Reflection private Static Invoke", new Runnable() {
                @Override
                public void run() {
                    try {
                        m1.invoke(null, 2, 6);
                    } catch (Throwable t) {
                        throw MountiplexUtil.uncheckedRethrow(t);
                    }
                }
            });
            TestUtil.measure("Reflection public Static Invoke", new Runnable() {
                @Override
                public void run() {
                    try {
                        m2.invoke(null, 2, 6);
                    } catch (Throwable t) {
                        throw MountiplexUtil.uncheckedRethrow(t);
                    }
                }
            });
            TestUtil.measure("Fast Method private Static Invoke", new Runnable() {
                @Override
                public void run() {
                    m1_fast.invoke(null, 2, 6);
                }
            });
            TestUtil.measure("Fast Method public Static Invoke", new Runnable() {
                @Override
                public void run() {
                    m2_fast.invoke(null, 2, 6);
                }
            });
        } catch (Throwable t) {
            throw MountiplexUtil.uncheckedRethrow(t);
        }
    }

    
}
