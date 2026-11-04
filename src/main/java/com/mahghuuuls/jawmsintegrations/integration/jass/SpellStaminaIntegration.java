package com.mahghuuuls.jawmsintegrations.integration.jass;

import com.mahghuuuls.jass.api.IItemValueProvider;
import com.mahghuuuls.jass.api.IStaminaService;
import com.mahghuuuls.jass.api.JassApi;
import com.mahghuuuls.jass.api.PreparationResult;
import com.mahghuuuls.jass.api.PreparedStaminaAction;
import com.mahghuuuls.jass.api.SettlementOutcome;
import com.mahghuuuls.jass.api.SettlementResult;
import com.mahghuuuls.jass.api.StaminaPublicState;
import com.mahghuuuls.jawms.api.CastAttemptContext;
import com.mahghuuuls.jawms.api.CastAttemptOutcome;
import com.mahghuuuls.jawms.api.CastCostPreparation;
import com.mahghuuuls.jawms.api.CastCostReleaseReason;
import com.mahghuuuls.jawms.api.CastCostSettlement;
import com.mahghuuuls.jawms.api.CastParticipantOutcome;
import com.mahghuuuls.jawms.api.ICastCostParticipant;
import com.mahghuuuls.jawms.api.ICastCostReservation;
import com.mahghuuuls.jawms.api.ManaApi;
import com.mahghuuuls.jawmsintegrations.Tags;
import com.mahghuuuls.jawmsintegrations.config.IntegrationConfigSnapshot;
import com.mahghuuuls.jawmsintegrations.config.IntegrationConfigSnapshot.SpellStaminaConfig;
import electroblob.wizardry.item.ItemWand;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.relauncher.Side;

import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.List;
import java.util.UUID;

/** Adapts a single accepted Wizardry wand effect to its JASS preparation and actual receipt. */
public final class SpellStaminaIntegration implements ICastCostParticipant, SpellStaminaAccess {
    static final ResourceLocation ID = new ResourceLocation(Tags.MOD_ID, "spell_stamina");
    private static final ResourceLocation DISCRETE = new ResourceLocation(Tags.MOD_ID, "wand_cast");
    private static final ResourceLocation CONTINUOUS = new ResourceLocation(Tags.MOD_ID, "wand_channel");

    private final IStaminaService stamina;
    private final SpellStaminaConfig config;
    private final SpellStaminaDiagnostics diagnostics;
    private volatile boolean active;

    SpellStaminaIntegration(IStaminaService stamina, SpellStaminaConfig config, boolean verbose) {
        this.stamina = stamina;
        this.config = config;
        this.diagnostics = new SpellStaminaDiagnostics(stamina, verbose);
    }

    public static SpellStaminaAccess initialize(IntegrationConfigSnapshot config) throws ReflectiveOperationException {
        IStaminaService stamina = JassApi.getStaminaService();
        verifyContract(stamina);
        SpellStaminaIntegration integration =
                new SpellStaminaIntegration(stamina, config.getSpellStamina(), config.getDiagnostics().isEnabled());
        integration.register(
                participant -> ManaApi.registerCastCostParticipant(ID, participant),
                provider -> JassApi.registerItemValueProvider(ID, provider),
                FMLCommonHandler.instance()::getEffectiveSide,
                () -> {
                    if (config.getDiagnostics().isEnabled()) MinecraftForge.EVENT_BUS.register(integration.diagnostics);
                });
        return integration;
    }

    private static void verifyContract(IStaminaService stamina) throws ReflectiveOperationException {
        if (JassApi.isProviderRegistrationFrozen()) {
            throw new IllegalStateException("The JASS provider registration window is already closed");
        }
        verifyClaimContract(PreparedStaminaAction.class, SettlementResult.class);
        requireMethod(IItemValueProvider.class, "ownsBowUse", boolean.class, ItemStack.class);
        requireMethod(IStaminaService.class, "getState", StaminaPublicState.class, EntityPlayer.class);
        requireMethod(StaminaPublicState.class, "getStamina", double.class);
        requireMethod(StaminaPublicState.class, "getMaximumStamina", double.class);
        requireMethod(StaminaPublicState.class, "getDebt", double.class);
        requireMethod(ICastCostParticipant.class, "prepare", CastCostPreparation.class, CastAttemptContext.class);
        requireMethod(ICastCostParticipant.class, "onAttemptResolved", void.class, CastAttemptOutcome.class);
        requireMethod(ICastCostReservation.class, "isValid", boolean.class);
        requireMethod(ICastCostReservation.class, "settle", CastCostSettlement.class);
        requireMethod(ICastCostReservation.class, "release", void.class, CastCostReleaseReason.class);
        requireMethod(CastAttemptContext.class, "getPlayer", EntityPlayer.class);
        requireMethod(CastAttemptContext.class, "getStack", ItemStack.class);
        requireMethod(CastAttemptContext.class, "getHand", EnumHand.class);
        requireMethod(CastAttemptContext.class, "isContinuous", boolean.class);
        requireMethod(CastAttemptContext.class, "getAttemptId", String.class);
        requireMethod(CastAttemptContext.class, "getUseId", String.class);
        requireMethod(CastAttemptOutcome.class, "getPlayerId", UUID.class);
        requireMethod(CastAttemptOutcome.class, "getUseId", String.class);
        requireMethod(CastAttemptOutcome.class, "getAttemptId", String.class);
        requireMethod(CastAttemptOutcome.class, "getSpellId", ResourceLocation.class);
        requireMethod(CastAttemptOutcome.class, "getHand", EnumHand.class);
        requireMethod(CastAttemptOutcome.class, "getStatus", CastAttemptOutcome.Status.class);
        requireMethod(CastAttemptOutcome.class, "getParticipants", List.class);
        requireMethod(CastAttemptOutcome.class, "isAccepted", boolean.class);
        requireMethod(CastAttemptOutcome.class, "wasEffectInvoked", boolean.class);
        requireMethod(CastAttemptOutcome.class, "wasEffectSuccessful", boolean.class);
        requireMethod(CastAttemptOutcome.class, "isLocalAmountKnown", boolean.class);
        requireMethod(CastAttemptOutcome.class, "getPaidMana", int.class);
        requireMethod(CastAttemptOutcome.class, "getPaidHunger", int.class);
        requireMethod(CastParticipantOutcome.class, "getParticipantId", ResourceLocation.class);
        requireMethod(CastParticipantOutcome.class, "getStatus", CastParticipantOutcome.Status.class);
        requireMethod(CastParticipantOutcome.class, "getFailure", CastParticipantOutcome.Failure.class);
        requireMethod(CastParticipantOutcome.class, "isAmountKnown", boolean.class);
        requireMethod(CastParticipantOutcome.class, "getAmount", double.class);
        requireMethod(CastParticipantOutcome.class, "mustStopAfterSuccess", boolean.class);
        requireFactory(CastCostPreparation.class, "notApplicable", CastCostPreparation.class);
        requireFactory(CastCostPreparation.class, "refused", CastCostPreparation.class, String.class);
        requireFactory(CastCostPreparation.class, "accepted", CastCostPreparation.class, ICastCostReservation.class);
        requireFactory(CastCostSettlement.class, "paid", CastCostSettlement.class, double.class, boolean.class);
        requireFactory(CastCostSettlement.class, "free", CastCostSettlement.class, boolean.class);
        requireFactory(CastCostSettlement.class, "invalidated", CastCostSettlement.class);
        Class<?>[] preparation = {EntityPlayer.class, ResourceLocation.class, double.class,
                boolean.class, ItemStack.class, EnumHand.class};
        requireImplementation(stamina, "prepareDiscreteAction", PreparedStaminaAction.class, preparation);
        requireImplementation(stamina, "prepareContinuousTick", PreparedStaminaAction.class, preparation);
        requireImplementation(stamina, "checkActionStart", boolean.class, EntityPlayer.class, ResourceLocation.class,
                ItemStack.class, EnumHand.class);
    }

    static void verifyClaimContract(Class<?> action, Class<?> receipt) throws NoSuchMethodException {
        requireMethod(action, "getResult", PreparationResult.class);
        requireMethod(action, "getPayableAmount", double.class);
        requireMethod(action, "isValid", boolean.class);
        requireMethod(action, "settle", receipt);
        requireMethod(action, "release", void.class);
        requireMethod(receipt, "getOutcome", SettlementOutcome.class);
        requireMethod(receipt, "getAmountPaid", double.class);
        requireMethod(receipt, "getNewState", StaminaPublicState.class);
        requireMethod(receipt, "mustStop", boolean.class);
    }

    private static void requireMethod(Class<?> owner, String name, Class<?> returns, Class<?>... parameters)
            throws NoSuchMethodException {
        requireSignature(owner, name, returns, false, parameters);
    }

    private static void requireFactory(Class<?> owner, String name, Class<?> returns, Class<?>... parameters)
            throws NoSuchMethodException {
        requireSignature(owner, name, returns, true, parameters);
    }

    private static void requireSignature(Class<?> owner, String name, Class<?> returns, boolean isStatic,
                                         Class<?>... parameters) throws NoSuchMethodException {
        java.lang.reflect.Method method = owner.getMethod(name, parameters);
        if (method.getReturnType() != returns
                || java.lang.reflect.Modifier.isStatic(method.getModifiers()) != isStatic) {
            throw new NoSuchMethodException(owner.getName() + "." + name + " has an incompatible signature");
        }
    }

    private static void requireImplementation(IStaminaService service, String method, Class<?> returns,
                                              Class<?>... parameters)
            throws ReflectiveOperationException {
        requireMethod(IStaminaService.class, method, returns, parameters);
        Class<?> api = IStaminaService.class.getMethod(method, parameters).getDeclaringClass();
        if (service.getClass().getMethod(method, parameters).getDeclaringClass() == api) {
            throw new IllegalStateException("JASS does not implement " + method);
        }
    }

    void register(Consumer<ICastCostParticipant> registerCast, Consumer<IItemValueProvider> registerItems,
                  Supplier<Side> effectiveSide, Runnable registerDiagnostics) {
        registerCast.accept(this);
        registerItems.accept(new IItemValueProvider() {
            @Override
            public boolean ownsBowUse(ItemStack stack) {
                return active && effectiveSide.get() == Side.SERVER && isWand(stack);
            }
        });
        registerDiagnostics.run();
        // A provider may retain its callback even if a later registration fails.
        active = true;
    }

    @Override
    public CastCostPreparation prepare(CastAttemptContext context) {
        if (!active || !(context.getPlayer() instanceof EntityPlayerMP)
                || context.getPlayer() instanceof FakePlayer || context.getPlayer().world.isRemote) {
            return CastCostPreparation.notApplicable();
        }
        ItemStack stack = context.getStack();
        if (!isWand(stack)) return CastCostPreparation.notApplicable();

        return prepareWand(context, stack);
    }

    CastCostPreparation prepareWand(CastAttemptContext context, ItemStack stack) {
        boolean continuous = context.isContinuous();
        ResourceLocation action = continuous ? CONTINUOUS : DISCRETE;
        double price = continuous ? config.getContinuousCostPerSecond() : config.getDiscreteCost();
        SpellStaminaDiagnostics.Attempt observation = diagnostics.begin(context);
        try {
            if (price == 0) {
                boolean allowed = stamina.checkActionStart(context.getPlayer(), action, stack, context.getHand());
                diagnostics.permission(observation, allowed ? "zero_price" : "zero_price_refused", 0);
                return allowed ? CastCostPreparation.accepted(new Reservation(null, 0, null))
                        : CastCostPreparation.refused("stamina_start_refused");
            }
            PreparedStaminaAction prepared = continuous
                    ? stamina.prepareContinuousTick(context.getPlayer(), action, price, false, stack, context.getHand())
                    : stamina.prepareDiscreteAction(context.getPlayer(), action, price, false, stack, context.getHand());
            boolean handedOff = false;
            try {
                PreparationResult result = prepared.getResult();
                if (result == PreparationResult.REFUSED) {
                    diagnostics.permission(observation, "refused", 0);
                    return CastCostPreparation.refused("stamina_start_refused");
                }
                double payable = prepared.getPayableAmount();
                diagnostics.permission(observation,
                        result == PreparationResult.FREE ? "jass_free_or_exempt" : "accepted", payable);
                String attemptId = context.getAttemptId();
                CastCostPreparation accepted = CastCostPreparation.accepted(new Reservation(prepared, payable,
                        detail -> diagnostics.unexpectedSettlement(attemptId, detail)));
                handedOff = true;
                return accepted;
            } finally {
                if (!handedOff) prepared.release();
            }
        } catch (LinkageError incompatible) {
            throw new IllegalStateException("JASS preparation API became unavailable", incompatible);
        }
    }

    private static boolean isWand(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof ItemWand;
    }

    @Override
    public void onAttemptResolved(CastAttemptOutcome outcome) {
        if (active) diagnostics.resolved(outcome);
    }

    @Override
    public String inspect(EntityPlayer player) {
        StaminaPublicState state = stamina.getState(player);
        return state == null ? "unavailable (no JASS session)"
                : "current=" + state.getStamina() + ", maximum=" + state.getMaximumStamina()
                + ", debt=" + state.getDebt();
    }

    @Override
    public void serverStopped() {
        diagnostics.serverStopped();
    }

    static final class Reservation implements ICastCostReservation {
        private final PreparedStaminaAction prepared;
        private final double payable;
        private final Consumer<String> reportFault;

        Reservation(PreparedStaminaAction prepared, double payable, Consumer<String> reportFault) {
            this.prepared = prepared;
            this.payable = payable;
            this.reportFault = reportFault;
        }

        @Override
        public boolean isValid() {
            try {
                return prepared == null || prepared.isValid();
            } catch (LinkageError incompatible) {
                throw new IllegalStateException("JASS claim validation API became unavailable", incompatible);
            }
        }

        @Override
        public CastCostSettlement settle() {
            if (prepared == null) return CastCostSettlement.free(false);
            try {
                SettlementResult result = prepared.settle();
                switch (result.getOutcome()) {
                    case PAID:
                    case PAID_STOP:
                        double amount = result.getAmountPaid();
                        if (!Double.isFinite(amount) || amount <= 0 || !matchesPayable(result, amount)) {
                            throw unexpected(result);
                        }
                        return CastCostSettlement.paid(amount, result.mustStop());
                    case FREE:
                        if (payable != 0 || result.getAmountPaid() != 0) throw unexpected(result);
                        return CastCostSettlement.free(false);
                    case INVALIDATED:
                        if (result.getAmountPaid() != 0) throw unexpected(result);
                        return CastCostSettlement.invalidated();
                    default:
                        throw new IllegalStateException("Unknown JASS settlement " + result.getOutcome());
                }
            } catch (LinkageError incompatible) {
                throw new IllegalStateException("JASS settlement API became unavailable", incompatible);
            }
        }

        private boolean matchesPayable(SettlementResult result, double amount) {
            StaminaPublicState state = result.getNewState();
            double scale = Math.max(Math.abs(amount), Math.abs(payable));
            if (state != null) {
                double after = state.getStamina();
                scale = Math.max(scale, Math.max(Math.abs(after), Math.abs(after + amount)));
            }
            // JASS snaps to the floor within 1e-9 and derives the receipt by subtracting pool values.
            return Double.isFinite(scale) && Math.abs(amount - payable) <= Math.max(1.0E-9, 4 * Math.ulp(scale));
        }

        private IllegalStateException unexpected(SettlementResult result) {
            String detail = "JASS " + result.getOutcome() + " reported actual=" + result.getAmountPaid()
                    + ", protected payable=" + payable;
            reportFault.accept(detail);
            return new IllegalStateException(detail);
        }

        @Override
        public void release(CastCostReleaseReason reason) {
            if (prepared != null) {
                try {
                    prepared.release();
                } catch (LinkageError incompatible) {
                    throw new IllegalStateException("JASS release API became unavailable", incompatible);
                }
            }
        }
    }
}
