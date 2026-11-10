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
    private static final String CURSE = ID + ":curse";
    private static final String CURSE_VALUE_KEY = "status:" + CURSE + "#0";
    private static final String LOOP_TYPE = ID + ":loop";
    private static final String LOOP = ID + ":pulse";
    private static final ResourceLocation SEED_CAUSE = new ResourceLocation(ID, "seed");
    private static final int BASE_MAX = 120;
    private static final int SEED = 110;
    private static final int PENALTY = -20;

    private ManaReadinessProvider statusSlot;
    private ManaReadinessProvider peerSlot;
    private final Map<EntityPlayerMP, Integer> attachedAt = new WeakHashMap<>();
    private UUID armedPlayer;
    private Mode armedMode;
    private Session session;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        if (!Boolean.getBoolean("statusworksProof.enabled")) return;
        statusSlot = ManaApi.registerReadinessProvider(new ResourceLocation(ID, "status"), this::verify);
        peerSlot = ManaApi.registerReadinessProvider(new ResourceLocation(ID, "peer"));
        registered(StatusworksApi.registerContributionType(new MaxType()));
        registered(StatusworksApi.registerContributionType(new PulseType()));
        registered(StatusworksApi.registerStatus(StatusDefinition.builder(CURSE).expiration(ExpirationMode.NONE)
                .contribute(Contribution.type(MAX_TYPE, ValueForm.FIXED, PENALTY)).build()));
        registered(StatusworksApi.registerStatus(StatusDefinition.builder(LOOP).expiration(ExpirationMode.NONE)
                .contribute(Contribution.type(LOOP_TYPE, ValueForm.FIXED, 1)).build()));
        MinecraftForge.EVENT_BUS.register(this);
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        if (statusSlot != null) event.registerServerCommand(new ArmCommand());
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
    }

    @SubscribeEvent
    public void logout(PlayerEvent.PlayerLoggedOutEvent event) {
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
                if (active.stage == Stage.RECORDED && executingTick >= 8) withdraw(active, executingTick);
                if (active.faultOn && executingTick == active.mode.clearAt) {
                    active.faultOn = false;
                    active.mark("fault_cleared", read(active.player) + " executingElapsed=" + executingTick);
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
            active.generation = statusSlot.getGeneration(player).orElse(null);
            ManaProfileGeneration peerGeneration = peerSlot.getGeneration(player).orElse(null);
            if (active.generation == null || peerGeneration == null) return;
            active.peerGeneration = peerGeneration;
            require(readiness == ManaProfileReadiness.PENDING, "new generation already decided");
            if (active.mode != Mode.IDLE) {
                require(StatusworksApi.statuses().count(player, CURSE) == 0, "dirty seed status");
                StatusworksApi.statuses().apply(player, CURSE, 1);
            }
            active.publishInitial(active.mode == Mode.IDLE ? 0 : PENALTY);
            require(ManaApi.getProfileReadiness(player) == ManaProfileReadiness.PENDING,
                    "status slot was recorded but the peer did not hold acceptance");
            active.mark("peer_open", ManaProfileReadiness.PENDING);
            active.stage = Stage.RECORDED;
            if (active.mode == Mode.IDLE) {
                active.completePeer();
                active.stage = Stage.REPLACED;
            }
            return;
        }
        require(statusSlot.getGeneration(player).orElse(null) == active.generation, "status generation changed");
        require(peerSlot.getGeneration(player).orElse(null) == active.peerGeneration, "peer generation changed");
        if (active.stage == Stage.WITHDRAWN) {
            if (!active.heldSaved) {
                require(readiness == ManaProfileReadiness.PENDING, "peer completed but first acceptance was not held");
                ContributionGroup held = read(player);
                require(held.availability() == active.mode.heldAvailability() && !active.suspended,
                        "the stale publication did not remain recorded through the player tick: " + held);
                require(active.elapsed() == active.withdrawalTick, "first hold was not observed in the withdrawal tick");
                active.mark("held_after_player_tick", held);
                active.save("held");
                active.heldSaved = true;
            }
            if (active.mode.late() && active.elapsed() == 99) {
                require(readiness == ManaProfileReadiness.PENDING, "expected PENDING at elapsed tick 99");
                active.mark("deadline_99", readiness);
            }
            if (active.mode.late() && active.elapsed() == 100) {
                require(readiness == ManaProfileReadiness.UNAVAILABLE, "expected UNAVAILABLE at elapsed tick 100");
                active.mark("deadline_100", readiness);
                active.save("timeout");
            }
            require(readiness != ManaProfileReadiness.READY, "stale result accepted before EMPTY replacement");
            if (active.suspended && read(player).isAvailable()) {
                require(!active.faultOn, "group became available with fault still armed");
                if (active.mode == Mode.DEFERRED) {
                    require(active.elapsed() == active.withdrawalTick + 1, "native drain missed the next server tick");
                }
                active.replaceWithEmpty();
                active.replacementTick = active.elapsed();
                active.stage = Stage.REPLACED;
            }
            return;
        }
        if (active.stage == Stage.REPLACED && readiness == ManaProfileReadiness.READY) {
            int maximum = ManaApi.getManaService().getMaximumMana(player);
            int current = ManaApi.getManaService().getCurrentMana(player);
            int expectedMax = active.mode == Mode.HEALTHY ? BASE_MAX + PENALTY : BASE_MAX;
            require(maximum == expectedMax, "unexpected accepted maximum " + maximum);
            if (active.mode != Mode.IDLE) {
                require(current == Math.min(SEED, expectedMax), "unexpected accepted mana " + current);
                require(active.mode.late() ? active.elapsed() > 100 : active.elapsed() < 100,
                        "acceptance on wrong side of deadline");
                if (active.mode != Mode.HEALTHY) {
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
        if (active.mode == Mode.HEALTHY) {
            active.completePeer();
            active.stage = Stage.REPLACED;
            return;
        }
        EntityPlayerMP player = active.player;
        if (active.mode == Mode.DEFERRED) {
            active.pulses = 8;
            StatusworksApi.statuses().apply(player, LOOP, 1);
            require(active.pulses == 0, "native loop did not reach its withdrawal");
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
        return StatusworksApi.contributions().group(player, MAX_TYPE);
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

    private enum Stage { JOINED, RECORDED, WITHDRAWN, REPLACED, DONE, FAILED }

    private enum Mode {
        IDLE(0), HEALTHY(0), FALSE_EARLY(28), THROW_EARLY(28), FALSE_LATE(121), THROW_LATE(121), DEFERRED(0);

        final int clearAt;

        Mode(int clearAt) {
            this.clearAt = clearAt;
        }

        boolean late() {
            return clearAt > 100;
        }

        GroupAvailability heldAvailability() {
            return this == DEFERRED ? GroupAvailability.PENDING : GroupAvailability.FAILED;
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
        boolean faultOn;
        boolean heldSaved;
        int pulses;
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

        boolean invalidate() {
            if (faultOn) {
                mark("fault_before_suspend", mode);
                if (mode == Mode.THROW_EARLY || mode == Mode.THROW_LATE) {
                    throw new IllegalStateException("proof fault before suspension");
                }
                return false;
            }
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
                active.mark("native_loop_withdrawal", active.elapsed());
                StatusworksApi.statuses().clear(entity, CURSE);
            } else if (present) {
                StatusworksApi.statuses().clear(entity, LOOP);
            } else {
                StatusworksApi.statuses().apply(entity, LOOP, 1);
            }
        }
    }

    private final class ArmCommand extends CommandBase {
        public String getName() { return "statusproof"; }

        public String getUsage(ICommandSender sender) { return "/statusproof <healthy|false_early|throw_early|false_late|throw_late|deferred>"; }

        public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
            EntityPlayerMP player = getCommandSenderAsPlayer(sender);
            if (args.length != 1 || session == null || session.player != player || session.stage != Stage.DONE
                    || armedPlayer != null) throw new CommandException(getUsage(sender));
            Mode mode;
            try {
                mode = Mode.valueOf(args[0].toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                throw new CommandException(getUsage(sender));
            }
            if (mode == Mode.IDLE) throw new CommandException(getUsage(sender));
            try {
                StatusworksApi.statuses().clear(player, CURSE);
                StatusworksApi.statuses().clear(player, LOOP);
                if (!session.candidate.isEmpty()) session.replaceWithEmpty();
                require(ManaApi.getManaService().getMaximumMana(player) == BASE_MAX, "wrong base maximum");
                require(ManaApi.getManaService().setCurrentMana(player, SEED, SEED_CAUSE).isSuccessful(),
                        "seed mutation refused");
                session.save("seed-" + mode.name().toLowerCase(Locale.ROOT));
                armedMode = mode;
                armedPlayer = player.getUniqueID();
                session.mark("armed_rejoin", mode);
            } catch (IOException | RuntimeException failure) {
                throw new CommandException("Could not arm proof: " + failure.getMessage());
            }
        }
    }
}
