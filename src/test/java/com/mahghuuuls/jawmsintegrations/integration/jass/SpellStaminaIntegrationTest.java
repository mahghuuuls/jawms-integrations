package com.mahghuuuls.jawmsintegrations.integration.jass;

import com.mahghuuuls.jass.api.IItemValueProvider;
import com.mahghuuuls.jass.api.IStaminaService;
import com.mahghuuuls.jass.api.PreparationResult;
import com.mahghuuuls.jass.api.PreparedStaminaAction;
import com.mahghuuuls.jass.api.SettlementOutcome;
import com.mahghuuuls.jass.api.SettlementResult;
import com.mahghuuuls.jass.api.StaminaPublicState;
import com.mahghuuuls.jawms.api.CastAttemptContext;
import com.mahghuuuls.jawms.api.CastCostPreparation;
import com.mahghuuuls.jawms.api.CastCostReleaseReason;
import com.mahghuuuls.jawms.api.CastCostSettlement;
import com.mahghuuuls.jawms.api.ICastCostParticipant;
import com.mahghuuuls.jawmsintegrations.config.IntegrationConfigSnapshot.SpellStaminaConfig;
import electroblob.wizardry.spell.Spell;
import electroblob.wizardry.util.SpellModifiers;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Bootstrap;
import net.minecraft.item.EnumAction;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

final class SpellStaminaIntegrationTest {
    @Test
    void sendsBothKindsToJassWithTheirOwnPriceAndDoesNotPayDuringPreparation() {
        Probe claim = new Probe(PreparationResult.ACCEPTED, SettlementOutcome.PAID, 12);
        List<String> calls = new ArrayList<>();
        SpellStaminaIntegration integration = adapter(new SpellStaminaConfig(true, 12, 8.25),
                calls, claim, true);

        CastCostPreparation discrete = integration.prepareWand(context(false), null);
        assertEquals(0, claim.settles);
        assertEquals(12, discrete.getReservation().settle().getAmount());
        CastCostPreparation continuous = integration.prepareWand(context(true), null);
        continuous.getReservation().release(CastCostReleaseReason.FALSE_EFFECT);

        assertEquals(java.util.Arrays.asList("prepareDiscreteAction:12.0", "prepareContinuousTick:8.25"), calls);
        assertEquals(1, claim.settles);
        assertEquals(1, claim.releases);
    }

    @Test
    void zeroUsesOnlyNativePermissionAndReturnsFreeWithoutCallingPricing() {
        for (boolean continuous : new boolean[] {false, true}) {
            for (boolean allowed : new boolean[] {false, true}) {
                List<String> calls = new ArrayList<>();
                SpellStaminaIntegration integration = adapter(new SpellStaminaConfig(true, 0, 0),
                        calls, null, allowed);

                CastCostPreparation result = integration.prepareWand(context(continuous), null);
                assertEquals(java.util.Collections.singletonList("checkActionStart"), calls);
                if (allowed) {
                    assertEquals(CastCostSettlement.Status.FREE, result.getReservation().settle().getStatus());
                } else {
                    assertEquals(CastCostPreparation.Status.REFUSED, result.getStatus());
                }
            }
        }
    }

    @Test
    void actualPartialTerminalPaymentAndInvalidationRemainDistinct() {
        Probe terminal = new Probe(PreparationResult.ACCEPTED, SettlementOutcome.PAID_STOP, 0.125);
        CastCostSettlement paid = new SpellStaminaIntegration.Reservation(terminal, 0.125,
                detail -> fail(detail)).settle();
        assertTrue(paid.mustStopAfterSuccess());
        assertEquals(0.125, paid.getAmount());

        Probe invalid = new Probe(PreparationResult.ACCEPTED, SettlementOutcome.INVALIDATED, 0);
        assertFalse(new SpellStaminaIntegration.Reservation(invalid, 12, detail -> fail(detail))
                .settle().isAmountKnown());
        Probe wrongAmount = new Probe(PreparationResult.ACCEPTED, SettlementOutcome.PAID, 0);
        assertThrows(IllegalStateException.class,
                () -> new SpellStaminaIntegration.Reservation(wrongAmount, 12, detail -> {}).settle());
    }

    @Test
    void reportsKnownPositiveShortPaymentButAcceptsNormalPoolRounding() {
        List<String> faults = new ArrayList<>();
        Probe shortPayment = new Probe(PreparationResult.ACCEPTED, SettlementOutcome.PAID, 5);
        assertThrows(IllegalStateException.class,
                () -> new SpellStaminaIntegration.Reservation(shortPayment, 12, faults::add).settle());
        assertTrue(faults.get(0).contains("actual=5.0, protected payable=12.0"));
        assertEquals(1, shortPayment.settles);

        double actual = 40.0 - 39.6;
        Probe rounded = new Probe(PreparationResult.ACCEPTED, SettlementOutcome.PAID, actual);
        rounded.state = new StaminaPublicState(39.6, 60);
        assertEquals(actual, new SpellStaminaIntegration.Reservation(rounded, 0.4,
                detail -> fail(detail)).settle().getAmount());
    }

    @Test
    void validatesLazyClaimAndReceiptMethodsBeforeRegistration() {
        assertDoesNotThrow(() -> SpellStaminaIntegration.verifyClaimContract(
                PreparedStaminaAction.class, SettlementResult.class));
        NoSuchMethodException missing = assertThrows(NoSuchMethodException.class,
                () -> SpellStaminaIntegration.verifyClaimContract(MissingRelease.class, SettlementResult.class));
        assertTrue(missing.getMessage().contains("release"));
        NoSuchMethodException wrongType = assertThrows(NoSuchMethodException.class,
                () -> SpellStaminaIntegration.verifyClaimContract(WrongPayable.class, SettlementResult.class));
        assertTrue(wrongType.getMessage().contains("getPayableAmount"));
    }

    @Test
    void capturedCallbacksAreInertWhenEitherProviderRegistrationThrows() {
        for (boolean failFirst : new boolean[] {true, false}) {
            AtomicReference<ICastCostParticipant> participant = new AtomicReference<>();
            AtomicReference<IItemValueProvider> provider = new AtomicReference<>();
            SpellStaminaIntegration integration = adapter(SpellStaminaConfig.defaults(),
                    new ArrayList<>(), null, true);

            assertThrows(IllegalStateException.class, () -> integration.register(cast -> {
                participant.set(cast);
                if (failFirst) throw new IllegalStateException("cast registration");
            }, items -> {
                provider.set(items);
                throw new IllegalStateException("item registration");
            }, () -> Side.SERVER, () -> fail("No diagnostics should register")));

            assertEquals(CastCostPreparation.Status.NOT_APPLICABLE, participant.get().prepare(null).getStatus());
            if (failFirst) assertNull(provider.get());
            else assertFalse(provider.get().ownsBowUse(null));
        }
    }

    private static SpellStaminaIntegration adapter(SpellStaminaConfig config, List<String> calls,
                                                    Probe claim, boolean allowed) {
        IStaminaService service = (IStaminaService) Proxy.newProxyInstance(
                IStaminaService.class.getClassLoader(), new Class<?>[] {IStaminaService.class},
                (ignored, method, args) -> {
                    assertEquals(EnumHand.OFF_HAND, args[args.length - 1]);
                    if (method.getName().equals("checkActionStart")) {
                        calls.add(method.getName());
                        return allowed;
                    }
                    assertFalse((boolean) args[3]);
                    calls.add(method.getName() + ":" + args[2]);
                    return claim;
                });
        return new SpellStaminaIntegration(service, config, false);
    }

    private static CastAttemptContext context(boolean continuous) {
        Bootstrap.register();
        return new CastAttemptContext(uninitialized(EntityPlayerMP.class), uninitialized(DummySpell.class),
                new ResourceLocation("fixture", "spell"),
                ItemStack.EMPTY, EnumHand.OFF_HAND, continuous, false, 0, "use", "attempt",
                new CastAttemptContext.AcceptedCost(0, 0, 1, 1, 0, 1), 0, 0, 0);
    }

    private static <T> T uninitialized(Class<T> type) {
        try {
            Class<?> unsafe = Class.forName("sun.misc.Unsafe");
            Field field = unsafe.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            return type.cast(unsafe.getMethod("allocateInstance", Class.class).invoke(field.get(null), type));
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }

    private static final class DummySpell extends Spell {
        DummySpell() { super("fixture", "spell", EnumAction.NONE, false); }

        @Override public boolean cast(World world, EntityPlayer caster, EnumHand hand,
                                      int ticks, SpellModifiers modifiers) { return true; }
    }

    private static final class Probe implements PreparedStaminaAction {
        private final PreparationResult preparation;
        private final SettlementOutcome outcome;
        private final double amount;
        int settles;
        int releases;
        StaminaPublicState state;

        Probe(PreparationResult preparation, SettlementOutcome outcome, double amount) {
            this.preparation = preparation;
            this.outcome = outcome;
            this.amount = amount;
        }

        @Override public PreparationResult getResult() { return preparation; }
        @Override public double getFinalCost() { return amount; }
        @Override public boolean isValid() { return outcome != SettlementOutcome.INVALIDATED; }
        @Override public SettlementResult settle() {
            settles++;
            return new SettlementResult(outcome, amount, state);
        }
        @Override public void release() { releases++; }
    }

    private interface MissingRelease {
        PreparationResult getResult();
        double getPayableAmount();
        boolean isValid();
        SettlementResult settle();
    }

    private interface WrongPayable {
        PreparationResult getResult();
        int getPayableAmount();
    }
}
