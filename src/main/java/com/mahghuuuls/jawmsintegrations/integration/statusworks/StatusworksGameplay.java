package com.mahghuuuls.jawmsintegrations.integration.statusworks;

import com.mahghuuuls.jawms.api.ConfiguredFlaskSnapshot;
import com.mahghuuuls.jawms.api.ManaApi;
import com.mahghuuuls.jawms.api.ManaContribution;
import com.mahghuuuls.jawms.api.ManaContributionDisposition;
import com.mahghuuuls.jawms.api.ManaContributionExplanation;
import com.mahghuuuls.jawms.api.ManaContributionPublicationResult;
import com.mahghuuuls.jawms.api.ManaContributionPublicationStatus;
import com.mahghuuuls.jawms.api.ManaProfileGeneration;
import com.mahghuuuls.jawms.api.ManaProfileReadiness;
import com.mahghuuuls.jawms.api.ManaReadinessProvider;
import com.mahghuuuls.jawmsintegrations.JawmsIntegrationsMod;
import com.mahghuuuls.jawmsintegrations.Tags;
import com.mahghuuuls.statusworks.api.ContributionService;
import com.mahghuuuls.statusworks.api.StatusworksApi;
import com.mahghuuuls.statusworks.api.contribution.ContributionGroupType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import static com.mahghuuuls.jawms.api.ManaContributionPublicationStatus.ALREADY_ACCEPTED;
import static com.mahghuuuls.jawms.api.ManaContributionPublicationStatus.INITIAL_RECORDED;
import static com.mahghuuuls.jawms.api.ManaContributionPublicationStatus.SUSPENDED;
import static com.mahghuuuls.jawms.api.ManaContributionPublicationStatus.UPDATE_RECORDED;

/** Owns live Statusworks settlement and the one JAWMS readiness slot. */
public final class StatusworksGameplay implements StatusworksSourceAccess {
    private static final ResourceLocation SOURCE = new ResourceLocation(Tags.MOD_ID, "statusworks");
    private static final int MAX_WARNINGS_PER_CONTEXT = 64;

    private final ContributionService contributions;
    private final boolean diagnostics;
    private final Map<UUID, Context> players = new HashMap<>();
    private final Set<Context> pending = new LinkedHashSet<>();
    private final List<EntityPlayer> replacements = new ArrayList<>();
    private ManaReadinessProvider provider;
    private long rulesRevision = ConfiguredFlaskSnapshot.UNKNOWN_REVISION;

    private StatusworksGameplay(ContributionService contributions, boolean diagnostics) {
        this.contributions = Objects.requireNonNull(contributions, "Statusworks contribution service");
        this.diagnostics = diagnostics;
    }

    static StatusworksSourceAccess activate(boolean diagnostics) {
        StatusworksGameplay gameplay = new StatusworksGameplay(StatusworksApi.contributions(), diagnostics);
        StatusworksAuthoring.registerTypes(id -> gameplay.new GroupType(id));
        try {
            MinecraftForge.EVENT_BUS.register(gameplay);
            gameplay.provider = ManaApi.registerReadinessProvider(SOURCE, gameplay::verify);
            return gameplay;
        } catch (RuntimeException | LinkageError failure) {
            MinecraftForge.EVENT_BUS.unregister(gameplay);
            throw failure;
        }
    }

    @SubscribeEvent
    public void login(PlayerEvent.PlayerLoggedInEvent event) {
        schedule(event.player);
    }

    @SubscribeEvent
    public void respawn(PlayerEvent.PlayerRespawnEvent event) {
        // Forge fires this before the connection points to the replacement, including on End return.
        replacements.add(event.player);
    }

    @SubscribeEvent
    public void changeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        schedule(event.player);
    }

    @SubscribeEvent
    public void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        Context context = players.get(event.player.getUniqueID());
        if (context != null && context.player == event.player) retire(context);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        for (EntityPlayer player : replacements) schedule(player);
        replacements.clear();
        if (players.isEmpty()) return;
        // JAWMS can replace a slot on the same player during a rules reload without a native change signal.
        long revision = ManaApi.getConfiguredFlaskSnapshot().getRevision();
        if (revision != ConfiguredFlaskSnapshot.UNKNOWN_REVISION && revision != rulesRevision) {
            rulesRevision = revision;
            pending.addAll(players.values());
        }
        if (pending.isEmpty()) return;
        ArrayList<Context> work = new ArrayList<>(pending);
        pending.clear();
        for (Context context : work) {
            if (!isCurrent(context)) {
                retire(context);
            } else {
                if (reconcile(context) && isCurrent(context)) pending.add(context);
                trace(context);
            }
        }
    }

    private boolean reconcile(Context context) {
        StatusworksPublication publication = context.publication;
        try {
            ManaProfileGeneration generation = provider.getGeneration(context.player).orElse(null);
            if (generation == null) {
                context.verified = false;
                context.readState = "UNAVAILABLE generation";
                ManaProfileReadiness readiness = ManaApi.getProfileReadiness(context.player);
                return readiness == ManaProfileReadiness.PENDING || readiness == ManaProfileReadiness.UNAVAILABLE;
            }
            publication.bind(generation);
            StatusworksProjection.Read current = read(context.player);
            context.verified = current.failure == null;
            context.readState = current.failure == null
                    ? (current.contribution.isEmpty() ? "AVAILABLE zero" : "AVAILABLE values") : current.failure;
            if (current.failure != null) {
                if (current.failure.startsWith("UNKNOWN_ORIGIN") || current.failure.startsWith("READ_ERROR")) {
                    warn(context, current.failure);
                }
                return true;
            }
            context.invalidated = false;
            for (String problem : current.problems) warn(context, problem);
            if (publication.uncertain) return true;
            if (!StatusworksProjection.same(publication.candidate, current.contribution)) {
                long revision = publication.revision;
                boolean initial = revision == 0;
                if (!publish(context, initial ? INITIAL_RECORDED : UPDATE_RECORDED, current.contribution,
                        () -> initial ? provider.completeInitial(context.player, generation, current.contribution)
                                : provider.update(context.player, generation, revision, current.contribution))) {
                    return true;
                }
            }
            return ManaApi.getProfileReadiness(context.player) != ManaProfileReadiness.READY;
        } catch (RuntimeException | LinkageError failure) {
            context.verified = false;
            context.readState = "READ_ERROR " + failure.getClass().getSimpleName();
            warn(context, "reconciliation " + failure.getClass().getSimpleName());
            return true;
        }
    }

    private boolean invalidate(EntityLivingBase entity) {
        Context context = schedule(entity);
        if (context == null) return true;
        context.verified = false;
        context.invalidated = true;
        context.readState = "AWAITING_SETTLEMENT";
        StatusworksPublication publication = context.publication;
        if (ManaApi.getProfileReadiness(context.player) == ManaProfileReadiness.READY) return true;
        if (publication.uncertain || context.publishing) return false;
        if (publication.candidate == null) return true;
        return publish(context, SUSPENDED, null, () -> provider.suspend(context.player, publication.generation,
                publication.revision));
    }

    private boolean publish(Context context, ManaContributionPublicationStatus expected, ManaContribution contribution,
                            Supplier<ManaContributionPublicationResult> action) {
        StatusworksPublication publication = context.publication;
        ManaProfileGeneration generation = publication.generation;
        context.publishing = true;
        try {
            ManaContributionPublicationResult receipt = action.get();
            if (!isCurrent(context)) {
                return false;
            }
            if (provider.getGeneration(context.player).orElse(null) != generation) {
                if (receipt.getRevision().isPresent()) publication.loseReceipt("generation changed during " + expected);
                return false;
            }
            boolean acknowledged = publication.acknowledge(receipt, expected, contribution);
            if (publication.uncertain) warn(context, publication.outcome);
            return acknowledged || (expected == SUSPENDED && receipt.getStatus() == ALREADY_ACCEPTED);
        } catch (RuntimeException | LinkageError failure) {
            publication.loseReceipt(expected + " " + failure.getClass().getSimpleName());
            warn(context, publication.outcome);
            return false;
        } finally {
            context.publishing = false;
        }
    }

    private boolean verify(EntityPlayer entity, ManaProfileGeneration generation, long revision,
                           ManaContribution candidate) {
        Context context = players.get(entity.getUniqueID());
        return context != null && context.player == entity && isCurrent(context)
                && !context.invalidated && !context.publishing
                && context.publication.verifies(generation, revision, candidate, () -> read(context.player));
    }

    private StatusworksProjection.Read read(EntityPlayerMP player) {
        try {
            return StatusworksProjection.read(id -> contributions.group(player, id));
        } catch (RuntimeException | LinkageError failure) {
            return StatusworksProjection.Read.unverified("READ_ERROR " + failure.getClass().getSimpleName());
        }
    }

    private Context schedule(EntityLivingBase entity) {
        if (provider == null) return null;
        EntityPlayerMP player = livePlayer(entity);
        if (player == null) return null;
        UUID id = player.getUniqueID();
        Context context = players.get(id);
        if (context == null || context.player != player) {
            if (context != null) retire(context);
            context = new Context(player);
            players.put(id, context);
        }
        pending.add(context);
        return context;
    }

    private void retire(Context context) {
        players.remove(context.player.getUniqueID(), context);
        pending.remove(context);
    }

    private boolean isCurrent(Context context) {
        return livePlayer(context.player) != null && players.get(context.player.getUniqueID()) == context;
    }

    private static EntityPlayerMP livePlayer(EntityLivingBase entity) {
        if (!(entity instanceof EntityPlayerMP) || entity instanceof FakePlayer || entity.world.isRemote) return null;
        EntityPlayerMP player = (EntityPlayerMP) entity;
        MinecraftServer server = player.getServer();
        return server != null && server.isCallingFromMinecraftThread()
                && player.connection != null && player.connection.player == player
                && player.connection.getNetworkManager().isChannelOpen()
                && server.getPlayerList().getPlayerByUUID(player.getUniqueID()) == player ? player : null;
    }

    private void warn(Context context, String detail) {
        if (context.warnings.size() < MAX_WARNINGS_PER_CONTEXT && context.warnings.add(detail)) {
            JawmsIntegrationsMod.LOGGER.warn("Statusworks player {}: {}", context.player.getUniqueID(), detail);
        }
    }

    private void trace(Context context) {
        if (!diagnostics || !isCurrent(context)) return;
        StatusworksPublication publication = context.publication;
        String summary = "group=" + context.readState + " receipt=" + publication.outcome
                + " revision=" + publication.revision;
        if (summary.equals(context.lastTrace) && publication.generation == context.lastTracedGeneration) return;
        context.lastTrace = summary;
        context.lastTracedGeneration = publication.generation;
        JawmsIntegrationsMod.LOGGER.info("STATUSWORKS_RECONCILE player={} object={} generation={} tick={} {}",
                context.player.getUniqueID(), System.identityHashCode(context.player),
                System.identityHashCode(publication.generation), context.player.getServer().getTickCounter(), summary);
    }

    @Override
    public PlayerStatus inspect(EntityPlayer entity) {
        Context context = players.get(entity.getUniqueID());
        if (context == null || context.player != entity || !isCurrent(context)) {
            return new PlayerStatus("no current player context", null, null);
        }
        ManaProfileReadiness phase = ManaApi.getProfileReadiness(entity);
        StatusworksProjection.Read current = read(context.player);
        ManaContribution applied = null;
        if (phase == ManaProfileReadiness.READY || phase == ManaProfileReadiness.LEGACY) {
            for (ManaContributionExplanation source : ManaApi.explainManaProfile(entity).getContributions()) {
                if (!SOURCE.equals(source.getSourceId())) continue;
                if (source.getDisposition() == ManaContributionDisposition.DISABLED) {
                    return new PlayerStatus("disabled by JAWMS provider policy", current.contribution, null);
                }
                if (source.getDisposition() == ManaContributionDisposition.EMPTY) applied = ManaContribution.EMPTY;
                else if (source.hasAcceptedContribution()) applied = source.getAcceptedContribution();
                break;
            }
        }
        StatusworksPublication publication = context.publication;
        String detail;
        if (current.failure != null) detail = current.failure;
        else if (publication.uncertain) detail = "unconfirmed receipt";
        else if (publication.candidate == null) detail = "awaiting verified publication";
        else if (!StatusworksProjection.same(publication.candidate, current.contribution)) detail = "candidate mismatch";
        else if (!context.verified) detail = "pending verification";
        else if (applied == null) detail = "awaiting acceptance";
        else detail = applied.isEmpty() ? "verified zero" : "applied";
        if (applied != null && (!context.verified || publication.uncertain
                || !StatusworksProjection.same(applied, current.contribution))) detail = "stale (" + detail + ")";
        detail += "; readiness=" + phase + "; receipt=" + publication.outcome + "; revision=" + publication.revision;
        if (!current.problems.isEmpty()) detail += "; omitted=" + String.join(", ", current.problems);
        return new PlayerStatus(detail, current.contribution, applied);
    }

    @Override
    public void serverStopped() {
        replacements.clear();
        pending.clear();
        players.clear();
        rulesRevision = ConfiguredFlaskSnapshot.UNKNOWN_REVISION;
    }

    private final class GroupType implements ContributionGroupType {
        private final String id;

        private GroupType(String id) {
            this.id = id;
        }

        @Override public String id() { return id; }

        @Override
        public boolean appliesTo(EntityLivingBase entity) {
            return provider != null && entity instanceof EntityPlayerMP
                    && !(entity instanceof FakePlayer) && !entity.world.isRemote;
        }

        @Override
        public boolean invalidate(EntityLivingBase entity) {
            return StatusworksGameplay.this.invalidate(entity);
        }

        @Override
        public void settled(EntityLivingBase entity, Map<String, Double> group) {
            schedule(entity);
        }

        @Override public void onValue(EntityLivingBase entity, String key, double value) {}

        @Override public void onEnd(EntityLivingBase entity, String key) {}
    }

    private static final class Context {
        final EntityPlayerMP player;
        final StatusworksPublication publication = new StatusworksPublication();
        final Set<String> warnings = new HashSet<>();
        boolean verified;
        boolean invalidated;
        boolean publishing;
        String readState = "UNAVAILABLE generation";
        String lastTrace;
        ManaProfileGeneration lastTracedGeneration;

        Context(EntityPlayerMP player) {
            this.player = player;
        }
    }
}
