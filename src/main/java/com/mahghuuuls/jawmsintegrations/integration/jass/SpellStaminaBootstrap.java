package com.mahghuuuls.jawmsintegrations.integration.jass;

import com.mahghuuuls.jawmsintegrations.JawmsIntegrationsMod;
import com.mahghuuuls.jawmsintegrations.config.IntegrationConfigSnapshot;
import com.mahghuuuls.jawmsintegrations.integration.IntegrationCoordinator;
import com.mahghuuuls.jawmsintegrations.integration.IntegrationId;
import com.mahghuuuls.jawmsintegrations.integration.IntegrationState;
import com.mahghuuuls.jawmsintegrations.integration.JawmsCompatibility;
import net.minecraftforge.fml.common.FMLCommonHandler;

import java.lang.reflect.InvocationTargetException;
import java.util.function.Consumer;

/** Keeps optional API classes out of the common lifecycle. */
public final class SpellStaminaBootstrap {
    private static final IntegrationId ID = IntegrationId.SPELL_STAMINA;
    private static final String ENTRY =
            "com.mahghuuuls.jawmsintegrations.integration.jass.SpellStaminaIntegration";
    private static final String BOW_OWNERSHIP =
            "com.mahghuuuls.jawmsintegrations.integration.jass.SpellStaminaBowOwnership";
    private static final String MINIMUM_JAWMS = "1.2.0";
    private static final String MINIMUM_MANA_API = "1.7";
    private static final String MINIMUM_STAMINA_API = "1.2";

    private SpellStaminaBootstrap() {}

    public static Result initialize(IntegrationCoordinator coordinator,
                                    IntegrationConfigSnapshot config, JawmsCompatibility.Status jawms,
                                    Consumer<LinkageError> contractLoss) {
        IntegrationState state = coordinator.getStatus(ID).getState();
        boolean clientOnly = state == IntegrationState.DISABLED
                && FMLCommonHandler.instance().getSide().isClient();
        if (state != IntegrationState.READY && !clientOnly) {
            return new Result(coordinator, null);
        }
        if (!clientOnly && (!JawmsCompatibility.meetsMinimum(jawms.getModVersion(), MINIMUM_JAWMS)
                || !JawmsCompatibility.meetsMinimum(jawms.getApiVersion(), MINIMUM_MANA_API))) {
            return new Result(coordinator.withUnsupported(ID,
                    "Requires JAWMS 1.2.0 or newer with casting API 1.7 or newer"), null);
        }
        try {
            ClassLoader loader = SpellStaminaBootstrap.class.getClassLoader();
            Class<?> api = Class.forName("com.mahghuuuls.jass.api.JassApi", false, loader);
            Object version = api.getField("API_VERSION").get(null);
            if (!(version instanceof String)
                    || !JawmsCompatibility.meetsMinimum((String) version, MINIMUM_STAMINA_API)) {
                return new Result(coordinator.withUnsupported(ID,
                        "Requires JASS API 1.2 or newer; detected " + version), null);
            }
            if (clientOnly) {
                Class.forName(BOW_OWNERSHIP, true, loader).getMethod("registerClientOnly").invoke(null);
                return new Result(coordinator, null);
            }
            SpellStaminaAccess access = (SpellStaminaAccess) Class.forName(ENTRY, true, loader)
                    .getMethod("initialize", IntegrationConfigSnapshot.class, Consumer.class)
                    .invoke(null, config, contractLoss);
            return new Result(coordinator.withActive(ID,
                    "JASS API " + version + "; wand casting and server bow ownership registered"), access);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof Error && !(cause instanceof LinkageError)) throw (Error) cause;
            return failed(coordinator, cause);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            return failed(coordinator, exception);
        }
    }

    private static Result failed(IntegrationCoordinator coordinator, Throwable cause) {
        String detail = "Spell stamina initialization failed: " + cause.getClass().getSimpleName()
                + ": " + cause.getMessage();
        JawmsIntegrationsMod.LOGGER.warn(detail, cause);
        return new Result(coordinator.withFailure(ID, detail), null);
    }

    public static final class Result {
        private final IntegrationCoordinator coordinator;
        private final SpellStaminaAccess access;

        private Result(IntegrationCoordinator coordinator, SpellStaminaAccess access) {
            this.coordinator = coordinator;
            this.access = access;
        }

        public IntegrationCoordinator getCoordinator() { return coordinator; }
        public SpellStaminaAccess getAccess() { return access; }
    }
}
