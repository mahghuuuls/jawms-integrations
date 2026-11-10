package example.statusworksproof;

import com.mahghuuuls.jawms.api.ManaApi;
import com.mahghuuuls.jawms.api.ManaContribution;
import com.mahghuuuls.jawms.api.ManaContributionPublicationResult;
import com.mahghuuuls.jawms.api.ManaContributionPublicationStatus;
import com.mahghuuuls.jawms.api.ManaProfileGeneration;
import com.mahghuuuls.jawms.api.ManaProfileReadiness;
import com.mahghuuuls.jawms.api.ManaReadinessProvider;
import com.mahghuuuls.statusworks.api.RegistrationResult;
import com.mahghuuuls.statusworks.api.StatusworksApi;
import com.mahghuuuls.statusworks.api.contribution.ContributionGroup;
import com.mahghuuuls.statusworks.api.contribution.ContributionGroupType;
import com.mahghuuuls.statusworks.api.contribution.ContributionType;
import com.mahghuuuls.statusworks.api.contribution.GroupAvailability;
import com.mahghuuuls.statusworks.api.definition.Contribution;
import com.mahghuuuls.statusworks.api.definition.ExpirationMode;
import com.mahghuuuls.statusworks.api.definition.StatusDefinition;
import com.mahghuuuls.statusworks.api.definition.ValueForm;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

@Mod(modid = NativeProof.ID, name = "Statusworks Native Proof", version = "1",
        acceptedMinecraftVersions = "[1.12.2]",
        dependencies = "required-after:jawms@[1.5.0,);required-after:statusworks@[1.3.0,)")
public final class NativeProof {
    public static final String ID = "statusworks_native_proof";
    public static final Logger LOG = LogManager.getLogger(ID);
    private static final String MAX_TYPE = ID + ":max";
    private static final String PRODUCTION_TYPE = "jawmsintegrations:max_mana_flat";
    private static final ResourceLocation PRODUCTION_PROVIDER = new ResourceLocation("jawmsintegrations", "statusworks");
    private static final String CURSE = ID + ":curse";
    private static final String CURSE_VALUE_KEY = "status:" + CURSE + "#0";
    private static final String BOOST = ID + ":boost";
    private static final String LOOP_TYPE = ID + ":loop";
    private static final String LOOP = ID + ":pulse";
    private static final ResourceLocation SEED_CAUSE = new ResourceLocation(ID, "seed");
    private static final int BASE_MAX = Integer.getInteger("statusworksProof.baseMaximum", 120);
    private static final int SEED = 110;
    private static final int STALE_SEED = 80;
    private static final String SECONDARY_NAME = "MahghuuulsPeer";
    private static final int PENALTY = -20;
    private static NativeProof replay;

    private ManaReadinessProvider statusSlot;
    private ManaReadinessProvider peerSlot;
    private final Map<EntityPlayerMP, Integer> attachedAt = new WeakHashMap<>();
    private UUID armedPlayer;
    private Mode armedMode;
    private Session session;
    private EntityPlayerMP secondary;
    private EntityPlayerMP retiring;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        if (!Boolean.getBoolean("statusworksProof.enabled")) return;
        if (Boolean.getBoolean("statusworksProof.productionReplay")) {
            replay = this;
        } else {
            statusSlot = ManaApi.registerReadinessProvider(new ResourceLocation(ID, "status"), this::verify);
            registered(StatusworksApi.registerContributionType(new MaxType()));
        }
        peerSlot = ManaApi.registerReadinessProvider(new ResourceLocation(ID, "peer"));
        registered(StatusworksApi.registerContributionType(new PulseType()));
        registered(StatusworksApi.registerStatus(StatusDefinition.builder(CURSE).expiration(ExpirationMode.NONE)
                .contribute(Contribution.type(maxType(), ValueForm.FIXED, PENALTY)).build()));
        if (replay != null && BASE_MAX == 100) {
            registered(StatusworksApi.registerStatus(StatusDefinition.builder(BOOST).expiration(ExpirationMode.NONE)
                    .contribute(Contribution.type(maxType(), ValueForm.FIXED, -PENALTY)).build()));
        }
        registered(StatusworksApi.registerStatus(StatusDefinition.builder(LOOP).expiration(ExpirationMode.NONE)
                .contribute(Contribution.type(LOOP_TYPE, ValueForm.FIXED, 1)).build()));
        if (replay == null) MinecraftForge.EVENT_BUS.register(this);
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        if (replay == this) {
            // In the replay, observe server END after the production adapter's init-registered subscriber.
            MinecraftForge.EVENT_BUS.register(this);
        }
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        if (peerSlot != null) event.registerServerCommand(new ArmCommand());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void attach(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof EntityPlayerMP) {
            EntityPlayerMP player = (EntityPlayerMP) event.getObject();
            // JAWMS creates login readiness at attachment, before the Forge connection handshake finishes.
            attachedAt.put(player, player.world.getMinecraftServer().getTickCounter());
        }
    }

    @SubscribeEvent
    public void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.player instanceof EntityPlayerMP)) return;
        EntityPlayerMP player = (EntityPlayerMP) event.player;
        Integer generationTick = attachedAt.remove(player);
        if (replay != null && SECONDARY_NAME.equals(player.getName())) {
            secondary = player;
            LOG.info("PROOF_SECONDARY event=login player={} object={} connection={} tick={}",
                    player.getUniqueID(), System.identityHashCode(player), System.identityHashCode(player.connection),
                    player.getServer().getTickCounter());
            return;
        }
        if (generationTick == null) {
            LOG.error("PROOF missing player attachment clock player={}", player.getUniqueID());
            return;
        }
        if (session != null) {
            LOG.error("PROOF unexpected additional connection player={}", player.getUniqueID());
            return;
        }
        Mode mode = player.getUniqueID().equals(armedPlayer) ? armedMode : Mode.IDLE;
        session = new Session(player, mode, generationTick);
        if (mode != Mode.IDLE) {
            armedPlayer = null;
            armedMode = null;
        }
        session.mark("login", "attachTick=" + generationTick + " readiness=" + ManaApi.getProfileReadiness(player));
        if (mode == Mode.RETIRE_PENDING) {
            ContributionGroup oldGroup = read(retiring);
            boolean distinct = retiring != player && retiring.connection != player.connection
                    && retiring.getUniqueID().equals(player.getUniqueID());
            session.mark("retired_group", oldGroup + " distinct=" + distinct
                    + " oldObject=" + System.identityHashCode(retiring)
                    + " newObject=" + System.identityHashCode(player));
            if (!distinct || oldGroup.availability() != GroupAvailability.UNAVAILABLE) session.stage = Stage.FAILED;
        }
    }

    @SubscribeEvent
    public void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (secondary == event.player) secondary = null;
        if (session != null && session.player == event.player) {
            session.mark("logout", session.stage);
            session = null;
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void tick(TickEvent.ServerTickEvent event) {
        Session active = session;
        if (active == null || active.stage == Stage.DONE || active.stage == Stage.FAILED) return;
        try {
            if (event.phase == TickEvent.Phase.START) {
                // Forge posts START before Minecraft advances the server's tick counter.
                int executingTick = active.elapsed() + 1;
                if (active.stage == Stage.RECORDED && executingTick >= (active.mode.deadlineReady() ? 100 : 8)) {
                    withdraw(active, executingTick);
                }
                if (active.stage == Stage.STALE_RECORDED) {
                    active.unknownOrigin = active.mode == Mode.ORIGIN;
                    active.faultOn = !active.unknownOrigin;
                    StatusworksApi.statuses().clear(active.player, CURSE);
                    ContributionGroup blocked = read(active.player);
                    require(active.unknownOrigin ? blocked.values().containsKey("future:example:source#0")
                                    : blocked.availability() == GroupAvailability.FAILED,
                            "replacement fault did not reach the group");
                    active.stage = Stage.STALE_FAILED;
                    active.mark("stale_failed", blocked);
                }
                if (active.faultOn && executingTick == active.mode.clearAt) {
                    active.faultOn = false;
                    active.mark("fault_cleared", read(active.player) + " executingElapsed=" + executingTick);
                }
                if (active.unknownOrigin && executingTick == active.mode.clearAt) {
                    active.unknownOrigin = false;
                    active.mark("origin_restored", read(active.player));
                }
                if (active.mode.longPulse() && executingTick == active.mode.clearAt) {
                    active.pulses = 0;
                    if (active.mode == Mode.POSITIVE_LATE) StatusworksApi.statuses().apply(active.player, BOOST, 1);
                    active.mark("pulse_stopped", "executingElapsed=" + executingTick);
                }
                return;
            }
            observeEnd(active);
            require(active.elapsed() < 175 || active.stage == Stage.DONE, "case exceeded its tick budget");
        } catch (IOException | RuntimeException failure) {
            active.stage = Stage.FAILED;
            active.mark("failed", failure);
            LOG.error("Proof stopped", failure);
        }
    }

    private void observeEnd(Session active) throws IOException {
        EntityPlayerMP player = active.player;
        ManaProfileReadiness readiness = ManaApi.getProfileReadiness(player);
        if (active.stage == Stage.JOINED) {
            if (active.peerGeneration == null) {
                active.peerGeneration = peerSlot.getGeneration(player).orElse(null);
                if (active.peerGeneration == null) return;
                require(readiness == ManaProfileReadiness.PENDING, "new generation already decided");
                if (active.mode.requiresCurse()) {
                    require(StatusworksApi.statuses().count(player, CURSE) == 0, "dirty seed status");
                    StatusworksApi.statuses().apply(player, CURSE, 1);
                }
            }
            int expected = active.mode.requiresCurse() ? PENALTY : 0;
            if (replay == null) {
                active.generation = statusSlot.getGeneration(player).orElse(null);
                if (active.generation == null) return;
                active.publishInitial(expected);
            } else if (active.candidate == null || active.suspended
                    || active.candidate.getFlatMaximumMana() != expected) return;
            require(ManaApi.getProfileReadiness(player) == ManaProfileReadiness.PENDING,
                    "status slot was recorded but the peer did not hold acceptance");
            active.mark("peer_open", ManaProfileReadiness.PENDING);
            active.stage = Stage.RECORDED;
            if (active.mode == Mode.IDLE || active.mode == Mode.RETIRE_PENDING) {
                active.completePeer();
                active.stage = Stage.REPLACED;
            }
            return;
        }
        if (replay == null) {
            require(statusSlot.getGeneration(player).orElse(null) == active.generation, "status generation changed");
        } else {
            require(!active.generationChanged, "production status generation changed");
        }
        require(peerSlot.getGeneration(player).orElse(null) == active.peerGeneration, "peer generation changed");
        if (active.stage == Stage.ACCEPTED_NOOP) {
            require(readiness == ManaProfileReadiness.READY && ManaApi.getManaService().getMaximumMana(player) == BASE_MAX
                            && ManaApi.getManaService().getCurrentMana(player) == STALE_SEED,
                    "unrelated native work changed the accepted profile");
            require(active.revision == active.heldRevision && active.settlements == active.heldSettlements,
                    "unrelated native work repeated settlement or publication after acceptance");
            ContributionGroup group = read(player);
            if (group.isAvailable()) {
                require(group.values().isEmpty() && active.elapsed() <= 1, "accepted no-op missed the next tick");
                active.mark("accepted_no_op_drained", group + " revision=" + active.revision);
                active.inspectProductionStatus();
                active.stage = Stage.DONE;
            } else {
                require(group.availability() == GroupAvailability.PENDING, "unexpected accepted no-op state");
            }
            return;
        }
        if (active.stage == Stage.RECORDED && active.mode.deadlineReady() && active.elapsed() == 99) {
            require(readiness == ManaProfileReadiness.PENDING, "expected PENDING at elapsed tick 99");
            active.mark("deadline_99", readiness);
        }
        if (active.stage == Stage.STALE_APPLIED && active.candidate.getFlatMaximumMana() == PENALTY) {
            require(readiness == ManaProfileReadiness.READY
                    && ManaApi.getManaService().getMaximumMana(player) == BASE_MAX + PENALTY,
                    "stale baseline was not accepted");
            active.mark("stale_baseline", BASE_MAX + PENALTY);
            active.stage = Stage.STALE_RECORDED;
            return;
        }
        if (active.stage == Stage.STALE_FAILED) {
            require(readiness == ManaProfileReadiness.READY, "replacement lost the accepted profile");
            int maximum = ManaApi.getManaService().getMaximumMana(player);
            int current = ManaApi.getManaService().getCurrentMana(player);
            require(current == STALE_SEED, "stale replacement changed mana balance");
            if (active.candidate.isEmpty()) {
                require(read(player).isAvailable() && maximum == BASE_MAX, "stale result did not recover");
                active.mark("stale_recovered", current + "/" + maximum);
                active.stage = Stage.DONE;
            } else {
                require(maximum == BASE_MAX + PENALTY, "stale result was discarded before verified recovery");
                if (!active.heldSaved) {
                    active.mark("stale_retained", current + "/" + maximum);
                    active.inspectProductionStatus();
                    active.save("stale");
                    active.heldSaved = true;
                }
            }
            return;
        }
        if (active.stage == Stage.WITHDRAWN) {
            if (!active.heldSaved) {
                require(readiness == ManaProfileReadiness.PENDING, "peer completed but first acceptance was not held");
                ContributionGroup held = read(player);
                require(held.availability() == active.mode.heldAvailability() && !active.suspended,
                        "the stale publication did not remain recorded through the player tick: " + held);
                require(active.elapsed() == active.withdrawalTick, "first hold was not observed in the withdrawal tick");
                active.mark("held_after_player_tick", held);
                active.inspectProductionStatus();
                active.save("held");
                active.heldSaved = true;
            }
            if (active.mode.late() && active.elapsed() == 99) {
                require(readiness == ManaProfileReadiness.PENDING, "expected PENDING at elapsed tick 99");
                if (active.mode.longPulse()) {
                    require(read(player).availability() == GroupAvailability.PENDING,
                            "unrelated native work drained before the deadline");
                }
                active.mark("deadline_99", readiness);
            }
            if (active.mode.late() && active.elapsed() == 100) {
                require(readiness == ManaProfileReadiness.UNAVAILABLE, "expected UNAVAILABLE at elapsed tick 100");
                active.mark("deadline_100", readiness);
                active.inspectProductionStatus();
                active.save("timeout");
            }
            if (active.mode == Mode.ISOLATION && !active.secondaryAccepted && secondary != null
                    && ManaApi.getProfileReadiness(secondary) == ManaProfileReadiness.READY) {
                require(readiness == ManaProfileReadiness.PENDING
                                && read(player).availability() == GroupAvailability.PENDING,
                        "second player accepted outside the first player's pending window");
                require(read(secondary).isAvailable() && ManaApi.getManaService().getMaximumMana(secondary) == BASE_MAX,
                        "second player did not accept its own verified group");
                active.secondaryAccepted = true;
                active.mark("isolated_peer_accepted", "secondary=" + secondary.getUniqueID()
                        + " object=" + System.identityHashCode(secondary)
                        + " connection=" + System.identityHashCode(secondary.connection)
                        + " maximum=" + ManaApi.getManaService().getMaximumMana(secondary));
            }
            if (active.mode.noop() && read(player).isAvailable()) {
                int drainTick = active.mode.longPulse() ? active.mode.clearAt : active.withdrawalTick + 1;
                require(active.elapsed() == drainTick, "no-op drain missed its expected tick");
                require(readiness != ManaProfileReadiness.READY, "no-op accepted before native drainage");
                require(active.revision == active.heldRevision && active.settlements == active.heldSettlements,
                        "no-op drain repeated a settlement signal or publication");
                require(active.mode != Mode.ISOLATION || active.secondaryAccepted, "missing second-player acceptance");
                active.replacementTick = active.elapsed();
                active.stage = Stage.REPLACED;
                active.mark("no_op_drained", read(player) + " revision=" + active.revision);
                return;
            }
            boolean replacement = replay == null ? active.suspended : active.replacementTick != 0;
            if (replacement && read(player).isAvailable()) {
                require(!active.faultOn, "group became available with fault still armed");
                if (active.mode == Mode.DEFERRED) {
                    int recorded = replay == null ? active.elapsed() : active.replacementTick;
                    require(recorded == active.withdrawalTick + 1, "native drain missed the next server tick");
                }
                if (replay == null) {
                    active.replaceWithEmpty();
                    active.replacementTick = active.elapsed();
                } else {
                    int expected = active.mode == Mode.POSITIVE_LATE ? -PENALTY : 0;
                    require(!active.suspended && active.candidate != null
                                    && active.candidate.getFlatMaximumMana() == expected,
                            "production recovery did not record the verified maximum contribution");
                }
                active.stage = Stage.REPLACED;
            } else {
                require(readiness != ManaProfileReadiness.READY, "stale result accepted before EMPTY replacement");
                return;
            }
        }
        if (active.stage == Stage.REPLACED && readiness == ManaProfileReadiness.READY) {
            int maximum = ManaApi.getManaService().getMaximumMana(player);
            int current = ManaApi.getManaService().getCurrentMana(player);
            int expectedMax = active.mode == Mode.HEALTHY ? BASE_MAX + PENALTY
                    : active.mode == Mode.POSITIVE_LATE ? BASE_MAX - PENALTY : BASE_MAX;
            require(maximum == expectedMax, "unexpected accepted maximum " + maximum);
            if (active.mode != Mode.IDLE) {
                require(current == Math.min(SEED, expectedMax), "unexpected accepted mana " + current);
                require(active.mode == Mode.RETIRE_PENDING || (active.mode.deadlineReady() ? active.elapsed() == 100
                                : active.mode.late() ? active.elapsed() > 100 : active.elapsed() < 100),
                        "acceptance on wrong side of deadline");
                if (active.mode != Mode.HEALTHY && active.mode != Mode.RETIRE_PENDING && !active.mode.deadlineReady()) {
                    require(active.elapsed() == active.replacementTick + 1, "acceptance missed the first eligible tick");
                }
                active.mark("server_ready", current + "/" + maximum);
                active.save("accepted");
            }
            active.stage = Stage.DONE;
            active.mark("done", current + "/" + maximum);
        }
    }

    private void withdraw(Session active, int executingTick) {
        if (active.mode == Mode.HEALTHY || active.mode.deadlineReady()) {
            active.completePeer();
            active.stage = Stage.REPLACED;
            return;
        }
        EntityPlayerMP player = active.player;
        if (active.mode == Mode.DEFERRED || active.mode.noop() || active.mode == Mode.POSITIVE_LATE) {
            active.heldRevision = active.revision;
            active.heldSettlements = active.settlements;
            active.pulses = active.mode.longPulse() ? Integer.MAX_VALUE : 8;
            StatusworksApi.statuses().apply(player, LOOP, 1);
            if (!active.mode.longPulse()) require(active.pulses == 0, "native loop did not reach its withdrawal");
        } else {
            active.faultOn = true;
            StatusworksApi.statuses().clear(player, CURSE);
        }
        ContributionGroup current = read(player);
        GroupAvailability expected = active.mode.heldAvailability();
        require(current.availability() == expected && current.values().isEmpty() && !active.suspended,
                "native read after withdrawal was " + current);
        require(StatusworksApi.statuses().count(player, CURSE) == 0, "curse was not natively withdrawn");
        active.mark("withdrawn", current + " executingElapsed=" + executingTick);
        active.completePeer();
        if (active.mode == Mode.ISOLATION) {
            require(secondary != null && secondary != player
                    && secondary.connection != player.connection
                    && !secondary.getUniqueID().equals(player.getUniqueID()), "second real connection unavailable");
            ManaProfileGeneration generation = peerSlot.getGeneration(secondary).orElse(null);
            require(generation != null && ManaApi.getProfileReadiness(secondary) != ManaProfileReadiness.READY,
                    "second player is not waiting on its test peer");
            long receipt = active.receipt(peerSlot.completeInitial(secondary, generation, ManaContribution.EMPTY),
                    ManaContributionPublicationStatus.INITIAL_RECORDED);
            active.mark("isolated_peer_recorded", "secondary=" + secondary.getUniqueID() + " revision=" + receipt);
        }
        active.withdrawalTick = executingTick;
        active.stage = Stage.WITHDRAWN;
    }

    private boolean verify(EntityPlayer player, ManaProfileGeneration generation,
            long revision, ManaContribution candidate) {
        Session active = session;
        if (active == null || active.player != player || active.generation != generation
                || active.revision != revision || active.suspended || active.candidate != candidate) return false;
        Integer flat = readFlat(read(player));
        return flat != null && flat == candidate.getFlatMaximumMana();
    }

    private static ContributionGroup read(EntityPlayer player) {
        return StatusworksApi.contributions().group(player, maxType());
    }

    private static String maxType() {
        return replay == null ? MAX_TYPE : PRODUCTION_TYPE;
    }

    public static boolean allowProductionInvalidation(String type, EntityLivingBase entity) {
        Session active = replay == null ? null : replay.session;
        return active == null || active.player != entity || !PRODUCTION_TYPE.equals(type) || active.allowInvalidation();
    }

    public static void observeProductionSettlement(String type, EntityLivingBase entity, Map<String, Double> values) {
        Session active = replay == null ? null : replay.session;
        if (active == null || active.player != entity || !PRODUCTION_TYPE.equals(type)) return;
        active.settlements++;
        active.mark("production_settled", values);
    }

    public static ContributionGroup productionRead(Entity entity, String type, ContributionGroup result) {
        Session active = replay == null ? null : replay.session;
        if (active != null && active.player == entity && active.unknownOrigin && PRODUCTION_TYPE.equals(type)
                && result.isAvailable()) {
            return ContributionGroup.available(Collections.singletonMap("future:example:source#0", 20.0D));
        }
        return result;
    }

    public static void observeProductionReceipt(ResourceLocation provider, EntityPlayer player,
            ManaProfileGeneration generation, long expectedRevision, ManaContribution contribution,
            ManaContributionPublicationResult result) {
        if (replay != null && player == replay.retiring && PRODUCTION_PROVIDER.equals(provider)
                && player.getServer().getPlayerList().getPlayerByUUID(player.getUniqueID()) != player) {
            LOG.error("PROOF_RETIRED_RECEIPT player={} status={}", player.getUniqueID(), result.getStatus());
        }
        Session active = replay == null ? null : replay.session;
        if (active == null || active.player != player || !PRODUCTION_PROVIDER.equals(provider)) return;
        active.mark("production_receipt", result.getStatus() + " expected=" + expectedRevision
                + " revision=" + result.getRevision() + " generation=" + System.identityHashCode(generation)
                + " maximumFlat=" + (contribution == null ? "-" : contribution.getFlatMaximumMana()));
        if (!result.getRevision().isPresent()) return;
        if (active.generation != null && active.generation != generation) active.generationChanged = true;
        active.generation = generation;
        active.revision = result.getRevision().getAsLong();
        active.suspended = result.getStatus() == ManaContributionPublicationStatus.SUSPENDED;
        if (active.suspended) return;
        active.candidate = contribution;
        if (active.stage == Stage.WITHDRAWN && contribution != null
                && (active.mode == Mode.POSITIVE_LATE ? contribution.getFlatMaximumMana() == -PENALTY
                        : contribution.isEmpty())) {
            active.replacementTick = active.elapsed();
        }
    }

    private static Integer readFlat(ContributionGroup group) {
        if (!group.isAvailable()) return null;
        if (group.values().isEmpty()) return 0;
        if (group.values().size() == 1 && Double.valueOf(PENALTY).equals(group.values().get(CURSE_VALUE_KEY))) {
            return PENALTY;
        }
        return null;
    }

    private static void registered(RegistrationResult result) {
        require(result.isAccepted(), "fixture registration refused: " + result);
    }

    private static void require(boolean condition, String detail) {
        if (!condition) throw new IllegalStateException(detail);
    }

    private enum Stage {
        JOINED, RECORDED, WITHDRAWN, REPLACED, STALE_APPLIED, STALE_RECORDED, STALE_FAILED, ACCEPTED_NOOP, DONE, FAILED
    }

    private enum Mode {
        IDLE(0), HEALTHY(0), FALSE_EARLY(28), THROW_EARLY(28), FALSE_LATE(121), THROW_LATE(121), DEFERRED(0),
        DEFERRED_NOOP(0), PENDING_LATE(121), ISOLATION(121), DEADLINE_READY(0), STALE(28), ORIGIN(28),
        ACCEPTED_NOOP(0), RETIRE_PENDING(0), POSITIVE_LATE(121);

        final int clearAt;

        Mode(int clearAt) {
            this.clearAt = clearAt;
        }

        boolean late() {
            return clearAt > 100;
        }

        GroupAvailability heldAvailability() {
            return this == DEFERRED || noop() || this == POSITIVE_LATE
                    ? GroupAvailability.PENDING : GroupAvailability.FAILED;
        }

        boolean noop() {
            return this == DEFERRED_NOOP || this == PENDING_LATE || this == ISOLATION;
        }

        boolean longPulse() {
            return this == PENDING_LATE || this == ISOLATION || this == POSITIVE_LATE;
        }

        boolean deadlineReady() {
            return this == DEADLINE_READY;
        }

        boolean requiresCurse() {
            return this != IDLE && this != RETIRE_PENDING && this != POSITIVE_LATE && !noop() && !deadlineReady();
        }

        boolean replacement() {
            return this == STALE || this == ORIGIN;
        }
    }

    private final class Session {
        final EntityPlayerMP player;
        final Mode mode;
        final int generationTick;
        final String id;
        Stage stage = Stage.JOINED;
        ManaProfileGeneration generation;
        ManaProfileGeneration peerGeneration;
        ManaContribution candidate;
        long revision;
        boolean suspended;
        boolean generationChanged;
        boolean faultOn;
        boolean unknownOrigin;
        boolean heldSaved;
        boolean secondaryAccepted;
        int pulses;
        int settlements;
        int heldSettlements;
        long heldRevision;
        int withdrawalTick;
        int replacementTick;

        Session(EntityPlayerMP player, Mode mode, int generationTick) {
            this.player = player;
            this.mode = mode;
            this.generationTick = generationTick;
            this.id = mode.name().toLowerCase(Locale.ROOT) + "-" + UUID.randomUUID();
        }

        int elapsed() {
            return player.getServer().getTickCounter() - generationTick;
        }

        void mark(String event, Object detail) {
            LOG.info("PROOF case={} event={} player={} entity={}#{} connection={} tick={} elapsed={} detail={}",
                    id, event, player.getUniqueID(), player.getName(), player.getEntityId(), System.identityHashCode(player.connection),
                    player.getServer().getTickCounter(), elapsed(), detail);
        }

        void inspectProductionStatus() {
            if (replay != null) {
                MinecraftServer server = player.getServer();
                server.getCommandManager().executeCommand(server, "jawmsintegrations status " + player.getName());
            }
        }

        void publishInitial(int expectedFlat) {
            ContributionGroup group = read(player);
            require(Integer.valueOf(expectedFlat).equals(readFlat(group)), "unexpected initial group " + group);
            candidate = expectedFlat == 0 ? ManaContribution.EMPTY
                    : ManaContribution.builder().flatMaximumMana(expectedFlat).build();
            revision = receipt(statusSlot.completeInitial(player, generation, candidate),
                    ManaContributionPublicationStatus.INITIAL_RECORDED);
            mark("status_recorded", group + " revision=" + revision);
        }

        void completePeer() {
            require(ManaApi.getProfileReadiness(player) == ManaProfileReadiness.PENDING,
                    "acceptance was decided before recording the peer");
            long peerRevision = receipt(peerSlot.completeInitial(player, peerGeneration, ManaContribution.EMPTY),
                    ManaContributionPublicationStatus.INITIAL_RECORDED);
            mark("peer_recorded", "revision=" + peerRevision + " native=" + read(player));
        }

        boolean allowInvalidation() {
            if (faultOn) {
                mark("fault_before_suspend", mode);
                if (mode == Mode.THROW_EARLY || mode == Mode.THROW_LATE) {
                    throw new IllegalStateException("proof fault before suspension");
                }
                return false;
            }
            return true;
        }

        boolean invalidate() {
            if (!allowInvalidation()) return false;
            if (candidate == null || suspended) return true;
            ManaContributionPublicationResult result = statusSlot.suspend(player, generation, revision);
            if (result.getStatus() == ManaContributionPublicationStatus.ALREADY_ACCEPTED) return true;
            if (result.getStatus() != ManaContributionPublicationStatus.SUSPENDED || !result.getRevision().isPresent()) {
                mark("suspension_unconfirmed", result.getStatus());
                return false;
            }
            revision = result.getRevision().getAsLong();
            suspended = true;
            mark("suspended", revision);
            return true;
        }

        void replaceWithEmpty() {
            ContributionGroup current = read(player);
            require(Integer.valueOf(0).equals(readFlat(current)), "replacement was not verified empty: " + current);
            long expectedRevision = revision;
            revision = receipt(statusSlot.update(player, generation, expectedRevision, ManaContribution.EMPTY),
                    ManaContributionPublicationStatus.UPDATE_RECORDED);
            candidate = ManaContribution.EMPTY;
            suspended = false;
            mark("empty_recorded", "usedRevision=" + expectedRevision + " revision=" + revision + " native=" + current);
        }

        void save(String label) throws IOException {
            String mark = id + "-" + label;
            player.getEntityData().setString("StatusworksProofSaveMark", mark);
            player.getServer().getPlayerList().saveAllPlayerData();
            Path world = player.getServerWorld().getSaveHandler().getWorldDirectory().toPath();
            Path source = world.resolve("playerdata").resolve(player.getUniqueID() + ".dat");
            Path evidence = world.resolve("statusworks-proof");
            Files.createDirectories(evidence);
            Path copy = Files.copy(source, evidence.resolve(mark + ".dat"));
            mark("saved", copy);
        }

        private long receipt(ManaContributionPublicationResult result, ManaContributionPublicationStatus expected) {
            require(result.getStatus() == expected && result.getRevision().isPresent(),
                    "expected " + expected + ", found " + result.getStatus());
            return result.getRevision().getAsLong();
        }
    }

    private final class MaxType implements ContributionGroupType {
        public String id() { return MAX_TYPE; }

        public boolean appliesTo(EntityLivingBase entity) { return entity instanceof EntityPlayerMP; }

        public boolean invalidate(EntityLivingBase entity) {
            return session == null || session.player != entity || session.invalidate();
        }

        public void settled(EntityLivingBase entity, Map<String, Double> group) {
            if (session != null && session.player == entity) session.mark("native_settled", group);
        }

        public void onValue(EntityLivingBase entity, String key, double value) {}

        public void onEnd(EntityLivingBase entity, String key) {}
    }

    private final class PulseType implements ContributionType {
        public String id() { return LOOP_TYPE; }

        public boolean appliesTo(EntityLivingBase entity) { return entity instanceof EntityPlayerMP; }

        public void onValue(EntityLivingBase entity, String key, double value) { pulse(entity, true); }

        public void onEnd(EntityLivingBase entity, String key) { pulse(entity, false); }

        private void pulse(EntityLivingBase entity, boolean present) {
            Session active = session;
            if (active == null || active.player != entity || active.pulses == 0) return;
            active.pulses--;
            if (active.pulses == 0) {
                if (active.mode.noop() || active.mode == Mode.ACCEPTED_NOOP) {
                    active.mark("native_loop_unrelated", active.elapsed());
                    if (present) StatusworksApi.statuses().clear(entity, LOOP);
                    else StatusworksApi.statuses().apply(entity, LOOP, 1);
                } else {
                    active.mark("native_loop_withdrawal", active.elapsed());
                    StatusworksApi.statuses().clear(entity, CURSE);
                }
            } else if (present) {
                StatusworksApi.statuses().clear(entity, LOOP);
            } else {
                StatusworksApi.statuses().apply(entity, LOOP, 1);
            }
        }
    }

    private final class ArmCommand extends CommandBase {
        public String getName() { return "statusproof"; }

        public String getUsage(ICommandSender sender) {
            return "/statusproof <healthy|false_early|throw_early|false_late|throw_late|deferred"
                    + (replay == null ? "" : "|deferred_noop|pending_late|isolation|deadline_ready|stale|origin"
                            + "|accepted_noop|retire_pending|positive_late")
                    + "> [player]";
        }

        public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
            if (args.length < 1 || args.length > 2) throw new CommandException(getUsage(sender));
            EntityPlayerMP player = args.length == 2 ? getPlayer(server, sender, args[1]) : getCommandSenderAsPlayer(sender);
            if (session == null || session.player != player || session.stage != Stage.DONE
                    || armedPlayer != null) throw new CommandException(getUsage(sender));
            Mode mode;
            try {
                mode = Mode.valueOf(args[0].toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                throw new CommandException(getUsage(sender));
            }
            if (mode == Mode.IDLE || (replay == null && mode != Mode.HEALTHY && mode != Mode.DEFERRED
                    && mode != Mode.FALSE_EARLY && mode != Mode.THROW_EARLY
                    && mode != Mode.FALSE_LATE && mode != Mode.THROW_LATE)) {
                throw new CommandException(getUsage(sender));
            }
            if (mode == Mode.ISOLATION && secondary == null) {
                throw new CommandException("Connect the second development player before arming isolation.");
            }
            try {
                if (mode == Mode.POSITIVE_LATE) {
                    require(BASE_MAX == 100 && ManaApi.getManaService().getMaximumMana(player) == 120
                            && session.candidate.getFlatMaximumMana() == 20, "positive seed maximum is unavailable");
                    require(ManaApi.getManaService().setCurrentMana(player, SEED, SEED_CAUSE).isSuccessful(),
                            "positive seed mutation refused");
                    session.save("seed-positive_late");
                    StatusworksApi.statuses().clear(player, BOOST);
                    armRejoin(player, mode);
                    return;
                }
                StatusworksApi.statuses().clear(player, CURSE);
                StatusworksApi.statuses().clear(player, LOOP);
                if (!session.candidate.isEmpty()) {
                    if (replay != null) throw new CommandException("Wait for the production adapter to clear the prior case.");
                    session.replaceWithEmpty();
                }
                require(ManaApi.getManaService().getMaximumMana(player) == BASE_MAX, "wrong base maximum");
                if (mode == Mode.ACCEPTED_NOOP || mode == Mode.RETIRE_PENDING) {
                    prepareAcceptedControl(player, mode);
                    return;
                }
                if (mode.replacement()) {
                    require(ManaApi.getManaService().setCurrentMana(player, STALE_SEED, SEED_CAUSE).isSuccessful(),
                            "stale seed mutation refused");
                    Session active = new Session(player, mode, server.getTickCounter());
                    active.stage = Stage.STALE_APPLIED;
                    active.generation = session.generation;
                    active.peerGeneration = session.peerGeneration;
                    active.candidate = session.candidate;
                    active.revision = session.revision;
                    session = active;
                    StatusworksApi.statuses().apply(player, CURSE, 1);
                    active.inspectProductionStatus();
                    return;
                }
                require(ManaApi.getManaService().setCurrentMana(player, SEED, SEED_CAUSE).isSuccessful(),
                        "seed mutation refused");
                session.save("seed-" + mode.name().toLowerCase(Locale.ROOT));
                armRejoin(player, mode);
            } catch (IOException | RuntimeException failure) {
                throw new CommandException("Could not arm proof: " + failure.getMessage());
            }
        }

        private void armRejoin(EntityPlayerMP player, Mode mode) {
            armedMode = mode;
            armedPlayer = player.getUniqueID();
            session.mark("armed_rejoin", mode);
        }

        private void prepareAcceptedControl(EntityPlayerMP player, Mode mode) throws IOException {
            boolean retire = mode == Mode.RETIRE_PENDING;
            int seed = retire ? SEED : STALE_SEED;
            require(ManaApi.getManaService().setCurrentMana(player, seed, SEED_CAUSE).isSuccessful(),
                    "accepted control seed mutation refused");
            if (retire) session.save("seed-retire_pending");
            Session active = new Session(player, mode, player.getServer().getTickCounter());
            active.generation = session.generation;
            active.peerGeneration = session.peerGeneration;
            active.candidate = session.candidate;
            active.revision = session.revision;
            active.heldRevision = active.revision;
            active.stage = retire ? Stage.DONE : Stage.ACCEPTED_NOOP;
            session = active;
            if (retire) StatusworksApi.statuses().apply(player, CURSE, 1);
            active.pulses = 8;
            StatusworksApi.statuses().apply(player, LOOP, 1);
            ContributionGroup pending = read(player);
            require(pending.availability() == GroupAvailability.PENDING
                            && StatusworksApi.statuses().count(player, CURSE) == 0,
                    "native work was not left pending: " + pending);
            require(ManaApi.getProfileReadiness(player) == ManaProfileReadiness.READY
                            && ManaApi.getManaService().getCurrentMana(player) == seed
                            && ManaApi.getManaService().getMaximumMana(player) == BASE_MAX,
                    "accepted profile changed before the pending check");
            active.mark(retire ? "retiring_pending" : "accepted_no_op_pending", pending
                    + " object=" + System.identityHashCode(player) + " revision=" + active.revision);
            active.inspectProductionStatus();
            if (retire) {
                retiring = player;
                armRejoin(player, mode);
            }
        }
    }
}
