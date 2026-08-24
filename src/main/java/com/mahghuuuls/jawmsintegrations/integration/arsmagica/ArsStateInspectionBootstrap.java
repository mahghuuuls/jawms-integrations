package com.mahghuuuls.jawmsintegrations.integration.arsmagica;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/** String-named optional boundary that keeps Ars classes out of common loading. */
public final class ArsStateInspectionBootstrap {

    static final String REGISTRAR =
            "com.mahghuuuls.jawmsintegrations.integration.arsmagica.optional.ArsStateReaderRegistrar";

    private ArsStateInspectionBootstrap() {
    }

    public static void activate(ClassLoader loader) {
        if (loader == null) {
            throw new IllegalArgumentException("Ars state reader class loader is required");
        }
        try {
            Class<?> registrar = Class.forName(REGISTRAR, true, loader);
            Method register = registrar.getMethod("register");
            register.invoke(null);
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException exception) {
            throw new IllegalStateException("Ars state reader could not be loaded", exception);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            if (cause instanceof Error) throw (Error) cause;
            throw new IllegalStateException("Ars state reader failed", cause);
        } catch (LinkageError exception) {
            throw new IllegalStateException("Ars state reader could not be linked", exception);
        }
    }
}
