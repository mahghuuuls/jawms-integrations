package com.mahghuuuls.jawmsintegrations.integration.crafttweaker;

import com.mahghuuuls.jawms.api.ManaMutationFailure;
import com.mahghuuuls.jawms.api.ManaMutationResult;
import com.mahghuuuls.jawms.api.ManaPublicState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CraftTweakerManaServiceTest {

    private static final ManaPublicState OLD = state(40, 100, 1.5D, 60L, 20L,
            false, true, true, false);
    private static final ManaPublicState NEW = state(65, 100, 1.5D, 60L, 20L,
            false, true, true, false);

    @Test
    void stateUsesOneCoherentImmutableJawmsSnapshot() {
        RecordingBackend backend = new RecordingBackend();
        CraftTweakerManaService service = active(backend);

        CraftTweakerManaState first = service.getState(null);
        backend.state = state(65, 120, 2.25D, 80L, 5L,
                true, false, false, true);
        CraftTweakerManaState second = service.getState(null);

        assertEquals(2, backend.stateCalls);
        assertEquals(40, first.getCurrentMana());
        assertEquals(100, first.getMaximumMana());
        assertEquals(1.5D, first.getEffectiveRegeneration());
        assertEquals(60L, first.getEffectiveLockoutTicks());
        assertEquals(20L, first.getRemainingLockoutTicks());
        assertEquals(3.0D, first.getEffectiveLockoutSeconds());
        assertEquals(1.0D, first.getRemainingLockoutSeconds());
        assertFalse(first.canRegenerateDuringPostCastLockout());
        assertTrue(first.canRegenerateDuringContinuousCasting());
        assertTrue(first.isContinuousCastActive());
        assertFalse(first.isRegenerationEligible());
        assertEquals(65, second.getCurrentMana());
        assertEquals(120, second.getMaximumMana());
        assertNotSame(first, second);
    }

    @Test
    void everyMutationDelegatesOnceWithExactCauseAndResult() {
        RecordingBackend backend = new RecordingBackend();
        CraftTweakerManaService service = active(backend);

        assertResult(service.setCurrentMana(null, 150), 150, 25, "none", true, 65);
        assertCall(backend, "set", 150, CraftTweakerManaService.SET_CAUSE);

        assertResult(service.restoreMana(null, 80), 80, 25, "none", true, 65);
        assertCall(backend, "restore", 80, CraftTweakerManaService.RESTORE_CAUSE);

        backend.failure = ManaMutationFailure.INSUFFICIENT_MANA;
        backend.delta = 0;
        assertResult(service.consumeMana(null, 80), 80, 0,
                "insufficient_mana", false, 40);
        assertCall(backend, "consume", 80, CraftTweakerManaService.CONSUME_CAUSE);

        backend.failure = ManaMutationFailure.NONE;
        backend.delta = -40;
        assertResult(service.drainMana(null, 80), 80, -40, "none", true, 65);
        assertCall(backend, "drain", 80, CraftTweakerManaService.DRAIN_CAUSE);

        assertEquals(4, backend.mutationCalls);
    }

    @Test
    void rejectedFailureAndSuccessfulZeroArePreservedExactly() {
        RecordingBackend backend = new RecordingBackend();
        CraftTweakerManaService service = active(backend);

        backend.failure = ManaMutationFailure.REJECTED;
        backend.delta = 0;
        assertResult(service.setCurrentMana(null, 0), 0, 0, "rejected", false, 40);

        backend.failure = ManaMutationFailure.NONE;
        assertResult(service.restoreMana(null, 0), 0, 0, "none", true, 65);
        assertResult(service.consumeMana(null, 0), 0, 0, "none", true, 65);
        assertResult(service.drainMana(null, 0), 0, 0, "none", true, 65);
    }

    @Test
    void negativeAmountAndInactiveIntegrationFailBeforeJawmsMutation() {
        RecordingBackend backend = new RecordingBackend();
        CraftTweakerManaService active = active(backend);

        IllegalArgumentException negative = assertThrows(IllegalArgumentException.class,
                () -> active.consumeMana(null, -1));
        assertTrue(negative.getMessage().contains("whole non-negative"));
        assertEquals(0, backend.mutationCalls);

        CraftTweakerManaService inactive = new CraftTweakerManaService(false,
                "Disabled by configuration", player -> {
                    throw new AssertionError("inactive validation must not run");
                }, backend);
        IllegalStateException disabled = assertThrows(IllegalStateException.class,
                () -> inactive.restoreMana(null, 5));
        assertTrue(disabled.getMessage().contains("Disabled by configuration"));
        assertEquals(0, backend.mutationCalls);
    }

    @Test
    void invalidContextStopsBeforeAnyJawmsReadOrMutation() {
        RecordingBackend backend = new RecordingBackend();
        CraftTweakerManaService service = new CraftTweakerManaService(true, "",
                player -> { throw new IllegalStateException("wrong logical-server thread"); }, backend);

        assertThrows(IllegalStateException.class, () -> service.getState(null));
        assertThrows(IllegalStateException.class, () -> service.drainMana(null, 1));
        assertEquals(0, backend.stateCalls);
        assertEquals(0, backend.mutationCalls);
    }

    @Test
    void lockoutDelegatesOnceWithFixedCauseAndReturnsCapturedTicks() {
        RecordingBackend backend = new RecordingBackend();
        backend.lockoutTicks = 73L;
        CraftTweakerManaService service = active(backend);

        assertEquals(73L, service.startRegenerationLockout(null));
        assertEquals(1, backend.lockoutCalls);
        assertEquals(CraftTweakerManaService.LOCKOUT_CAUSE, backend.lastCause);
        assertEquals("jawmsintegrations:crafttweaker_regeneration_lockout",
                CraftTweakerManaService.LOCKOUT_CAUSE.toString());
        assertEquals(0, backend.mutationCalls);
    }

    @Test
    void everyOperationForwardsOnlyTheSuppliedPlayerIdentity() throws Exception {
        EntityPlayer first = uninitializedServerPlayer();
        EntityPlayer second = uninitializedServerPlayer();
        assertNotSame(first, second);
        RecordingBackend backend = new RecordingBackend();
        CraftTweakerManaService service = new CraftTweakerManaService(true, "",
                player -> { }, backend);

        service.getState(first);
        assertSame(first, backend.lastPlayer);
        service.setCurrentMana(second, 1);
        assertSame(second, backend.lastPlayer);
        service.restoreMana(first, 1);
        assertSame(first, backend.lastPlayer);
        service.consumeMana(second, 1);
        assertSame(second, backend.lastPlayer);
        service.drainMana(first, 1);
        assertSame(first, backend.lastPlayer);
        service.startRegenerationLockout(second);
        assertSame(second, backend.lastPlayer);
    }

    @Test
    void unsupportedLockoutBecomesClearScriptFacingFailure() {
        RecordingBackend backend = new RecordingBackend();
        backend.lockoutUnsupported = true;
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> active(backend).startRegenerationLockout(null));

        assertTrue(failure.getMessage().contains("does not support"));
        assertEquals(1, backend.lockoutCalls);
    }

    private static CraftTweakerManaService active(RecordingBackend backend) {
        return new CraftTweakerManaService(true, "", player -> { }, backend);
    }

    private static EntityPlayer uninitializedServerPlayer() throws Exception {
        Class<?> unsafeType = Class.forName("sun.misc.Unsafe");
        java.lang.reflect.Field field = unsafeType.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        Object unsafe = field.get(null);
        return (EntityPlayer) unsafeType.getMethod("allocateInstance", Class.class)
                .invoke(unsafe, EntityPlayerMP.class);
    }

    private static void assertResult(CraftTweakerManaMutationResult result,
                                     int requested, int delta, String failure, boolean successful,
                                     int expectedNewMana) {
        assertEquals(successful, result.isSuccessful());
        assertEquals(failure, result.getFailure());
        assertEquals(requested, result.getRequestedAmount());
        assertEquals(delta, result.getActualDelta());
        assertEquals(Math.abs(delta), result.getActualAmount());
        assertEquals(40, result.getOldState().getCurrentMana());
        assertEquals(expectedNewMana, result.getNewState().getCurrentMana());
    }

    private static void assertCall(RecordingBackend backend, String operation,
                                   int amount, ResourceLocation cause) {
        assertEquals(operation, backend.lastOperation);
        assertEquals(amount, backend.lastAmount);
        assertEquals(cause, backend.lastCause);
    }

    private static ManaPublicState state(int current, int maximum, double regeneration,
                                         long effectiveTicks, long remainingTicks,
                                         boolean postCast, boolean continuous,
                                         boolean continuousActive, boolean eligible) {
        return new ManaPublicState(current, maximum, regeneration, effectiveTicks, remainingTicks,
                postCast, continuous, continuousActive, eligible);
    }

    private static final class RecordingBackend implements CraftTweakerManaService.ManaBackend {
        private ManaPublicState state = OLD;
        private ManaMutationFailure failure = ManaMutationFailure.NONE;
        private int delta = 25;
        private int stateCalls;
        private int mutationCalls;
        private int lockoutCalls;
        private long lockoutTicks;
        private boolean lockoutUnsupported;
        private String lastOperation;
        private int lastAmount;
        private ResourceLocation lastCause;
        private EntityPlayer lastPlayer;

        @Override
        public ManaPublicState getState(EntityPlayer player) {
            stateCalls++;
            lastPlayer = player;
            return state;
        }

        @Override
        public ManaMutationResult setCurrentMana(EntityPlayer player, int target,
                                                  ResourceLocation cause) {
            return mutation(player, "set", target, cause);
        }

        @Override
        public ManaMutationResult restoreMana(EntityPlayer player, int amount,
                                               ResourceLocation cause) {
            return mutation(player, "restore", amount, cause);
        }

        @Override
        public ManaMutationResult consumeMana(EntityPlayer player, int amount,
                                               ResourceLocation cause) {
            return mutation(player, "consume", amount, cause);
        }

        @Override
        public ManaMutationResult drainMana(EntityPlayer player, int amount,
                                             ResourceLocation cause) {
            return mutation(player, "drain", amount, cause);
        }

        @Override
        public long startRegenerationLockout(EntityPlayer player, ResourceLocation cause) {
            lockoutCalls++;
            lastPlayer = player;
            lastCause = cause;
            if (lockoutUnsupported) throw new UnsupportedOperationException("unsupported");
            return lockoutTicks;
        }

        private ManaMutationResult mutation(EntityPlayer player, String operation, int amount,
                                              ResourceLocation cause) {
            mutationCalls++;
            lastPlayer = player;
            lastOperation = operation;
            lastAmount = amount;
            lastCause = cause;
            ManaPublicState newState = failure == ManaMutationFailure.NONE ? NEW : OLD;
            return new ManaMutationResult(failure, amount, delta, cause, OLD, newState);
        }
    }
}
