package com.mahghuuuls.jawmsintegrations.integration.jafs;

import com.mahghuuuls.jawmsintegrations.config.IntegrationConfigSnapshot;
import com.mahghuuuls.jawmsintegrations.integration.IntegrationCoordinator;
import com.mahghuuuls.jawmsintegrations.integration.IntegrationId;
import com.mahghuuuls.jawmsintegrations.integration.IntegrationState;
import com.mahghuuuls.jawmsintegrations.integration.OptionalIntegrationEvidenceRegistry;
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
    void realReflectiveEntryReachesNativePrevalidationWithoutAnyRegistryWrite() {
        assertFalse(com.mahghuuuls.jafs.api.JafsApi.isBound());
        IllegalStateException nativeRefusal = assertThrows(IllegalStateException.class,
                com.mahghuuuls.jafs.api.JafsApi::isRegistrationFrozen);
        for (boolean enabled : new boolean[]{false, true}) {
            IntegrationCoordinator compatible = coordinator(enabled, "1.0.0",
                    OptionalIntegrationEvidenceRegistry.Evidence.supported("1.0.0"));
            IllegalStateException unavailable = assertThrows(IllegalStateException.class,
                    () -> JafsRegistrationBootstrap.initialize(compatible, getClass().getClassLoader(),
                            ignored -> fail("Prevalidation unexpectedly invoked a runtime failure callback")));
            assertEquals(nativeRefusal.getMessage(), unavailable.getMessage(),
                    "The actual reflection name/signature must reach native contract validation");
        }
        assertFalse(com.mahghuuuls.jafs.api.JafsApi.isBound());
    }

    @Test
    void bootstrapAndItsInspectionPortLoadWithoutTheOptionalNativeApi() throws Exception {
        List<String> requested = new ArrayList<>();
        String bootstrapName = JafsRegistrationBootstrap.class.getName();
        ClassLoader isolated = new ClassLoader(getClass().getClassLoader()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.equals(bootstrapName) || name.startsWith(bootstrapName + "$")) return findClass(name);
                if (name.equals(JafsRegistrationBootstrap.ENTRY) || name.startsWith("com.mahghuuuls.jafs.")) {
                    requested.add(name);
                    throw new ClassNotFoundException(name);
                }
                return super.loadClass(name, resolve);
            }
            @Override
            protected Class<?> findClass(String name) throws ClassNotFoundException {
                requested.add(name);
                if (!name.equals(bootstrapName) && !name.startsWith(bootstrapName + "$")) {
                    throw new ClassNotFoundException(name);
                }
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
        for (boolean enabled : new boolean[]{false, true}) {
            for (OptionalIntegrationEvidenceRegistry.Evidence evidence : new OptionalIntegrationEvidenceRegistry.Evidence[]{
                    OptionalIntegrationEvidenceRegistry.Evidence.absent(),
                    OptionalIntegrationEvidenceRegistry.Evidence.unsupported("0.9.0"),
                    OptionalIntegrationEvidenceRegistry.Evidence.error("rejected"),
                    OptionalIntegrationEvidenceRegistry.Evidence.unknown()}) {
                boolean absent = evidence.getDecision() == OptionalIntegrationEvidenceRegistry.Decision.ABSENT;
                IntegrationCoordinator coordinator = coordinator(enabled, absent ? null : "1.0.0", evidence);
                Object result = bootstrap.getMethod("initialize", IntegrationCoordinator.class,
                                ClassLoader.class, Consumer.class).invoke(null, coordinator, isolated, failure);
                assertSame(coordinator, result.getClass().getMethod("getCoordinator").invoke(result));
                assertNull(result.getClass().getMethod("getFoodSource").invoke(result));
                assertFalse(requested.contains(JafsRegistrationBootstrap.ENTRY));
                assertFalse(requested.stream().anyMatch(name -> name.startsWith("com.mahghuuuls.jafs.")));
            }
        }
        for (boolean enabled : new boolean[]{false, true}) {
            IntegrationCoordinator compatible = coordinator(enabled, "1.0.0",
                    OptionalIntegrationEvidenceRegistry.Evidence.supported("1.0.0"));
            InvocationTargetException thrown = assertThrows(InvocationTargetException.class,
                    () -> bootstrap.getMethod("initialize", IntegrationCoordinator.class,
                                    ClassLoader.class, Consumer.class)
                            .invoke(null, compatible, isolated, failure));
            assertTrue(thrown.getCause() instanceof IllegalStateException);
        }
        assertTrue(requested.contains(JafsRegistrationBootstrap.ENTRY));
        assertFalse(requested.stream().anyMatch(name -> name.startsWith("com.mahghuuuls.jafs.")));
    }

    @Test
    void enabledOutcomePromotesOnlyAfterSuccessAndMismatchedResultsAreRejected() {
        IntegrationCoordinator ready = coordinator(true, "1.0.0",
                OptionalIntegrationEvidenceRegistry.Evidence.supported("1.0.0"));
        FoodSourceAccess source = new FoodSourceAccess() {
            @Override public FoodStatProjection.Result inspect(net.minecraft.entity.player.EntityPlayer player) {
                throw new AssertionError("Initialization queried player state");
            }
            @Override public void clearWarnings() { throw new AssertionError("Initialization cleared warnings"); }
        };
        JafsRegistrationBootstrap.Result result = JafsRegistrationBootstrap.initialize(ready, enabled -> {
            assertTrue(enabled);
            assertEquals(IntegrationState.READY, ready.getStatus(IntegrationId.JAFS).getState());
            return source;
        });
        assertSame(source, result.getFoodSource());
        assertEquals(IntegrationState.ACTIVE, result.getCoordinator().getStatus(IntegrationId.JAFS).getState());
        assertEquals(IntegrationState.READY, ready.getStatus(IntegrationId.JAFS).getState());
        assertThrows(IllegalStateException.class, () -> JafsRegistrationBootstrap.initialize(ready, enabled -> null));
        IntegrationCoordinator disabled = coordinator(false, "1.0.0",
                OptionalIntegrationEvidenceRegistry.Evidence.supported("1.0.0"));
        assertThrows(IllegalStateException.class,
                () -> JafsRegistrationBootstrap.initialize(disabled, enabled -> source));
        assertThrows(IllegalStateException.class, () -> JafsRegistrationBootstrap.initialize(disabled, enabled -> {
            throw new IllegalStateException("partial native registration");
        }));
        assertEquals("Disabled by configuration", disabled.getStatus(IntegrationId.JAFS).getDetail(),
                "Partial native registration cannot advertise metadata success");
    }

    private static IntegrationCoordinator coordinator(boolean enabled, String version,
            OptionalIntegrationEvidenceRegistry.Evidence evidence) {
        IntegrationConfigSnapshot defaults = IntegrationConfigSnapshot.defaults();
        IntegrationConfigSnapshot config = new IntegrationConfigSnapshot(defaults.getQualityTools(),
                defaults.getAncientSpellcraft(), defaults.getCraftTweaker(),
                new IntegrationConfigSnapshot.IntegrationToggleConfig(enabled), defaults.getDiagnostics());
        return IntegrationCoordinator.initialize(config,
                id -> IntegrationId.JAFS.getModId().equals(id) ? version : null,
                id -> id == IntegrationId.JAFS ? evidence : OptionalIntegrationEvidenceRegistry.Evidence.absent());
    }
}
