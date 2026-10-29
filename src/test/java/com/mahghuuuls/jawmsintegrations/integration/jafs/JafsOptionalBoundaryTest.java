package com.mahghuuuls.jawmsintegrations.integration.jafs;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

final class JafsOptionalBoundaryTest {
    @Test
    void bootstrapAndItsInspectionPortLoadWithoutTheOptionalNativeApi() throws Exception {
        List<String> requested = new ArrayList<>();
        String bootstrapName = JafsRegistrationBootstrap.class.getName();
        ClassLoader isolated = new ClassLoader(getClass().getClassLoader()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.equals(bootstrapName)) return findClass(name);
                if (name.equals(JafsRegistrationBootstrap.ENTRY) || name.startsWith("com.mahghuuuls.jafs.")) {
                    requested.add(name);
                    throw new ClassNotFoundException(name);
                }
                return super.loadClass(name, resolve);
            }
            @Override
            protected Class<?> findClass(String name) throws ClassNotFoundException {
                requested.add(name);
                if (!name.equals(bootstrapName)) throw new ClassNotFoundException(name);
                try (InputStream input = getClass().getResourceAsStream("/" + name.replace('.', '/') + ".class")) {
                    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                    byte[] buffer = new byte[4096];
                    int count;
                    while ((count = input.read(buffer)) != -1) bytes.write(buffer, 0, count);
                    byte[] source = bytes.toByteArray();
                    return defineClass(name, source, 0, source.length);
                } catch (java.io.IOException exception) {
                    throw new ClassNotFoundException(name, exception);
                }
            }
        };
        Class<?> bootstrap = Class.forName(bootstrapName, true, isolated);
        assertSame(isolated, bootstrap.getClassLoader());
        Consumer<String> failure = ignored -> {};
        InvocationTargetException thrown = assertThrows(InvocationTargetException.class,
                () -> bootstrap.getMethod("register", ClassLoader.class, Consumer.class)
                        .invoke(null, isolated, failure));
        assertTrue(thrown.getCause() instanceof IllegalStateException);
        assertTrue(requested.contains(JafsRegistrationBootstrap.ENTRY));
        assertFalse(requested.stream().anyMatch(name -> name.startsWith("com.mahghuuuls.jafs.")));
    }
}
