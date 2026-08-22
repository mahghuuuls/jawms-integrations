package com.mahghuuuls.jawmsintegrations.integration.crafttweaker;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/** String-named optional boundary that keeps CraftTweaker classes out of common loading. */
public final class CraftTweakerRegistrationBootstrap {

    static final String REGISTRAR =
            "com.mahghuuuls.jawmsintegrations.integration.crafttweaker.zen.CraftTweakerRegistrar";

    private CraftTweakerRegistrationBootstrap() {
    }

    public static void register(ClassLoader loader) {
        if (loader == null) {
            throw new IllegalArgumentException("CraftTweaker registrar class loader is required");
        }
        try {
            Class<?> registrar = Class.forName(REGISTRAR, true, loader);
            Method register = registrar.getMethod("register");
            register.invoke(null);
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException exception) {
            throw new IllegalStateException("CraftTweaker registrar could not be loaded", exception);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            if (cause instanceof Error) throw (Error) cause;
            throw new IllegalStateException("CraftTweaker registrar failed", cause);
        } catch (LinkageError exception) {
            throw new IllegalStateException("CraftTweaker registrar could not be linked", exception);
        }
    }
}
