package com.mahghuuuls.jawmsintegrations.integration.jass;

import com.mahghuuuls.jass.api.IStaminaService;
import com.mahghuuuls.jass.api.StaminaPublicState;
import com.mahghuuuls.jawms.api.CastAttemptContext;
import com.mahghuuuls.jawms.api.CastAttemptOutcome;
import com.mahghuuuls.jawms.api.CastParticipantOutcome;
import com.mahghuuuls.jawms.api.ManaApi;
import com.mahghuuuls.jawmsintegrations.JawmsIntegrationsMod;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Keeps only in-flight observations and one summary per active channel. */
public final class SpellStaminaDiagnostics {
    private final IStaminaService stamina;
    private final boolean verbose;
    private final Map<String, Attempt> attempts = new HashMap<>();
    private final Map<UUID, Channel> channels = new HashMap<>();
    private final Map<UUID, Integer> lastDetail = new HashMap<>();
    private final Map<CastAttemptOutcome.Status, Integer> lastWarning =
            new EnumMap<>(CastAttemptOutcome.Status.class);
    private Integer lastSettlementWarning;

    SpellStaminaDiagnostics(IStaminaService stamina, boolean verbose) {
        this.stamina = stamina;
        this.verbose = verbose;
    }

    Attempt begin(CastAttemptContext context) {
        if (!verbose) return null;
        EntityPlayer player = context.getPlayer();
        int tick = player.getServer().getTickCounter();
        UUID playerId = player.getUniqueID();
        Channel previous = channels.get(playerId);
        boolean continuation = context.isContinuous() && previous != null
                && previous.useId.equals(context.getUseId());
        Attempt attempt = new Attempt(player, tick, context.isContinuous(),
                continuation ? null : resources(player));
        attempts.put(context.getAttemptId(), attempt);
        return attempt;
    }

    void permission(Attempt attempt, String permission, double payable) {
        if (attempt != null) {
            attempt.permission = permission;
            attempt.payable = payable;
        }
    }

    void unexpectedSettlement(String attemptId, String detail) {
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        int tick = server == null ? 0 : server.getTickCounter();
        if (lastSettlementWarning == null || tick - lastSettlementWarning >= 200) {
            lastSettlementWarning = tick;
            JawmsIntegrationsMod.LOGGER.warn("Spell stamina inconsistent settlement attempt={} {}", attemptId, detail);
        }
    }

    void resolved(CastAttemptOutcome outcome) {
        CastParticipantOutcome receipt = null;
        for (CastParticipantOutcome candidate : outcome.getParticipants()) {
            if (SpellStaminaBowOwnership.ID.equals(candidate.getParticipantId())) {
                receipt = candidate;
                break;
            }
        }
        if (receipt == null || receipt.getStatus() == CastParticipantOutcome.Status.NOT_APPLICABLE) return;
        Attempt attempt = attempts.remove(outcome.getAttemptId());
        warnIfFault(outcome, receipt, attempt);
        if (attempt == null) return;
        if (attempt.continuous) {
            Channel channel = channels.get(outcome.getPlayerId());
            if (channel != null && !channel.useId.equals(outcome.getUseId())) {
                closeChannel(outcome.getPlayerId(), channel, "superseded", attempt.player);
                channel = null;
            }
            if (channel == null) {
                channel = new Channel(outcome.getUseId(), attempt.before);
                channels.put(outcome.getPlayerId(), channel);
            }
            channel.lastTick = attempt.tick;
            channel.last = outcome;
            channel.receipt = receipt;
            channel.permission = attempt.permission;
            channel.payable = attempt.payable;
            if (outcome.wasEffectSuccessful()) channel.effects++;
            if (receipt.isAmountKnown()) channel.paid += receipt.getAmount();
            else channel.unknown++;
            if (receipt.mustStopAfterSuccess() || !outcome.isAccepted()) {
                closeChannel(outcome.getPlayerId(), channel,
                        receipt.mustStopAfterSuccess() ? "terminal_settlement" : outcome.getStatus().name(),
                        attempt.player);
            }
        } else if (allowDetail(outcome.getPlayerId(), attempt.tick)) {
            JawmsIntegrationsMod.LOGGER.info("Spell stamina discrete {} before=[{}] after=[{}]",
                    describe(outcome, receipt, attempt.permission, attempt.payable),
                    attempt.before, resources(attempt.player));
        }
    }

    private void warnIfFault(CastAttemptOutcome outcome, CastParticipantOutcome receipt, Attempt attempt) {
        switch (outcome.getStatus()) {
            case INVALID_AFTER_EFFECT:
            case SETTLEMENT_FAULT:
            case CLEANUP_FAULT:
                break;
            default:
                if (receipt.getFailure() == CastParticipantOutcome.Failure.NONE) return;
        }
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        int tick = server == null ? 0 : server.getTickCounter();
        Integer previous = lastWarning.get(outcome.getStatus());
        if (previous == null || tick - previous >= 200) {
            lastWarning.put(outcome.getStatus(), tick);
            JawmsIntegrationsMod.LOGGER.warn("Spell stamina cast fault {}", describe(outcome, receipt,
                    attempt == null ? "unavailable" : attempt.permission,
                    attempt == null ? Double.NaN : attempt.payable));
        }
    }

    private boolean allowDetail(UUID player, int tick) {
        Integer previous = lastDetail.get(player);
        if (previous != null && tick - previous < 20) return false;
        lastDetail.put(player, tick);
        return true;
    }

    private static String describe(CastAttemptOutcome outcome, CastParticipantOutcome receipt,
                                   String permission, double payable) {
        return "player=" + outcome.getPlayerId() + " source=wand spell=" + outcome.getSpellId()
                + " hand=" + outcome.getHand() + " use=" + outcome.getUseId()
                + " attempt=" + outcome.getAttemptId() + " result=" + outcome.getStatus()
                + " effectInvoked=" + outcome.wasEffectInvoked() + " effectSuccessful=" + outcome.wasEffectSuccessful()
                + " permission=" + permission
                + " payable=" + (Double.isNaN(payable) ? "unavailable" : payable)
                + " staminaResult=" + receipt.getStatus() + " fault=" + receipt.getFailure()
                + " staminaPaid=" + (receipt.isAmountKnown() ? receipt.getAmount() : "unknown")
                + " manaPaid=" + (outcome.isLocalAmountKnown() ? outcome.getPaidMana() : "unknown")
                + " hungerPaid=" + (outcome.isLocalAmountKnown() ? outcome.getPaidHunger() : "unknown");
    }

    private String resources(EntityPlayer player) {
        String staminaValue;
        try {
            StaminaPublicState state = stamina.getState(player);
            staminaValue = state == null ? "unavailable" : Double.toString(state.getStamina());
        } catch (RuntimeException | LinkageError unavailable) {
            staminaValue = "unavailable";
        }
        String manaValue;
        try {
            // A diagnostic read must not accept a pending loaded profile after session loss.
            ManaApi.explainManaProfile(player);
            manaValue = Integer.toString(ManaApi.getManaService().getCurrentMana(player));
        } catch (RuntimeException | LinkageError unavailable) {
            manaValue = "unavailable";
        }
        return "mana=" + manaValue + ", stamina=" + staminaValue;
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (!verbose || event.player.world.isRemote || event.phase != TickEvent.Phase.END) return;
        EntityPlayer player = event.player;
        UUID id = player.getUniqueID();
        Channel channel = channels.get(id);
        if (channel != null && player.getServer().getTickCounter() - channel.lastTick > 1) {
            closeChannel(id, channel, "no_further_effect", player);
        }
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) attempts.clear();
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.player.getUniqueID();
        attempts.values().removeIf(attempt -> attempt.player == event.player);
        Channel channel = channels.get(id);
        if (channel != null) closeChannel(id, channel, "logout", event.player);
        lastDetail.remove(id);
    }

    private void closeChannel(UUID id, Channel channel, String stop, EntityPlayer player) {
        channels.remove(id);
        if (channel.effects > 0 || allowDetail(id, player.getServer().getTickCounter())) {
            JawmsIntegrationsMod.LOGGER.info(
                    "Spell stamina continuous stop={} effects={} knownStaminaPaid={} unknownAmounts={} {} before=[{}] after=[{}]",
                    stop, channel.effects, channel.paid, channel.unknown,
                    describe(channel.last, channel.receipt, channel.permission, channel.payable),
                    channel.before, resources(player));
        }
    }

    void serverStopped() {
        attempts.clear();
        channels.clear();
        lastDetail.clear();
        lastWarning.clear();
        lastSettlementWarning = null;
    }

    static final class Attempt {
        final EntityPlayer player;
        final int tick;
        final boolean continuous;
        final String before;
        String permission = "unavailable";
        double payable = Double.NaN;

        Attempt(EntityPlayer player, int tick, boolean continuous, String before) {
            this.player = player;
            this.tick = tick;
            this.continuous = continuous;
            this.before = before;
        }
    }

    private static final class Channel {
        final String useId;
        final String before;
        int lastTick;
        int effects;
        double paid;
        int unknown;
        CastAttemptOutcome last;
        CastParticipantOutcome receipt;
        String permission;
        double payable;

        Channel(String useId, String before) {
            this.useId = useId;
            this.before = before;
        }
    }
}
