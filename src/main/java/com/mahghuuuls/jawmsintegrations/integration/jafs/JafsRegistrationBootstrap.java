package com.mahghuuuls.jawmsintegrations.integration.jafs;

import java.lang.reflect.InvocationTargetException;
import java.util.function.Consumer;

/** Optional product entry is linked only after compatible enabled evidence. */
public final class JafsRegistrationBootstrap {
    static final String ENTRY =
            "com.mahghuuuls.jawmsintegrations.integration.jafs.JafsIntegration";

    private JafsRegistrationBootstrap() {
    }

    public static FoodSourceAccess register(ClassLoader loader, Consumer<String> failure) {
        if (loader == null || failure == null) throw new IllegalArgumentException("JAFS bootstrap inputs required");
        try {
            return (FoodSourceAccess) Class.forName(ENTRY, true, loader)
                    .getMethod("activate", Consumer.class).invoke(null, failure);
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException exception) {
            throw new IllegalStateException("JAFS activation entry could not be loaded", exception);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            if (cause instanceof Error) throw (Error) cause;
            throw new IllegalStateException("JAFS activation failed", cause);
        }
    }
}

