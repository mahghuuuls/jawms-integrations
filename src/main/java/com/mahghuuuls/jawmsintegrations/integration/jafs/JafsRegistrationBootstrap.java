package com.mahghuuuls.jawmsintegrations.integration.jafs;

import com.mahghuuuls.jawmsintegrations.integration.IntegrationCoordinator;
import com.mahghuuuls.jawmsintegrations.integration.IntegrationId;
import com.mahghuuuls.jawmsintegrations.integration.IntegrationState;

import java.lang.reflect.InvocationTargetException;
import java.util.function.Consumer;

/** Contains optional linkage and initialization outcomes without exposing the native API. */
public final class JafsRegistrationBootstrap {
    static final String ENTRY =
            "com.mahghuuuls.jawmsintegrations.integration.jafs.JafsIntegration";

    private JafsRegistrationBootstrap() {
    }

    public static Result initialize(IntegrationCoordinator coordinator, ClassLoader loader,
                                    Consumer<String> failure) {
        if (loader == null || failure == null) throw new IllegalArgumentException("JAFS bootstrap inputs required");
        return initialize(coordinator, effectsEnabled -> register(loader, effectsEnabled, failure));
    }

    static Result initialize(IntegrationCoordinator coordinator, Initializer initializer) {
        if (coordinator == null || initializer == null) {
            throw new IllegalArgumentException("JAFS initialization inputs required");
        }
        IntegrationState state = coordinator.getStatus(IntegrationId.JAFS).getState();
        if (state != IntegrationState.READY && state != IntegrationState.DISABLED) {
            return new Result(coordinator, null);
        }
        boolean effectsEnabled = state == IntegrationState.READY;
        FoodSourceAccess source = initializer.initialize(effectsEnabled);
        if (effectsEnabled && source == null || !effectsEnabled && source != null) {
            throw new IllegalStateException("JAFS initialization returned an inconsistent effect mode");
        }
        IntegrationCoordinator initialized = effectsEnabled
                ? coordinator.withActive(IntegrationId.JAFS,
                        "Active-food JAWMS properties and contextual provider registered")
                : coordinator.withDisabledDetail(IntegrationId.JAFS,
                        "Property metadata available; mana effects disabled; no food contribution applied");
        return new Result(initialized, source);
    }

    private static FoodSourceAccess register(ClassLoader loader, boolean effectsEnabled,
                                              Consumer<String> failure) {
        try {
            return (FoodSourceAccess) Class.forName(ENTRY, true, loader)
                    .getMethod("initialize", boolean.class, Consumer.class).invoke(null, effectsEnabled, failure);
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException exception) {
            throw new IllegalStateException("JAFS activation entry could not be loaded", exception);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            if (cause instanceof Error) throw (Error) cause;
            throw new IllegalStateException("JAFS activation failed", cause);
        }
    }

    @FunctionalInterface
    interface Initializer {
        FoodSourceAccess initialize(boolean effectsEnabled);
    }

    public static final class Result {
        private final IntegrationCoordinator coordinator;
        private final FoodSourceAccess foodSource;

        private Result(IntegrationCoordinator coordinator, FoodSourceAccess foodSource) {
            this.coordinator = coordinator;
            this.foodSource = foodSource;
        }

        public IntegrationCoordinator getCoordinator() { return coordinator; }
        public FoodSourceAccess getFoodSource() { return foodSource; }
    }
}

