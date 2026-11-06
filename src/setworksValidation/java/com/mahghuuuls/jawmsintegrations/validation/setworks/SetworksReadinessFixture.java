package com.mahghuuuls.jawmsintegrations.validation.setworks;

import com.mahghuuuls.jawms.api.ManaApi;
import com.mahghuuuls.jawms.api.ManaContribution;
import com.mahghuuuls.jawms.api.ManaContributionPublicationResult;
import com.mahghuuuls.jawms.api.ManaContributionPublicationStatus;
import com.mahghuuuls.jawms.api.ManaMutationResult;
import com.mahghuuuls.jawms.api.ManaProfileGeneration;
import com.mahghuuuls.jawms.api.ManaProfileExplanation;
import com.mahghuuuls.jawms.api.ManaProfileReadiness;
import com.mahghuuuls.jawms.api.ManaPublicState;
import com.mahghuuuls.jawms.api.ManaReadinessProvider;
import com.mahghuuuls.jawms.internal.mana.ManaLifecycle;
import com.mahghuuuls.setworks.api.BonusBuilder;
import com.mahghuuuls.setworks.api.DefinitionSource;
import com.mahghuuuls.setworks.api.PieceBuilder;
import com.mahghuuuls.setworks.api.RegistrationResult;
import com.mahghuuuls.setworks.api.SetBuilder;
import com.mahghuuuls.setworks.api.SetworksApi;
import com.mahghuuuls.setworks.core.effect.Rebuild;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.CommandSenderWrapper;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.DamageSource;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.event.FMLServerStartedEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppedEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Disposable integration fixture. All mana mutation is confined to setup or an explicit refusal check. */
@Mod(modid = SetworksReadinessFixture.ID, name = "Setworks mana validation", version = "dev",
        dependencies = "required-after:jawms@[1.4.1,);required-after:setworks@[1.1.0,);required-after:jawmsintegrations")
public final class SetworksReadinessFixture {
    public static final String ID = "setworksvalidation";
    public static final Logger LOG = LogManager.getLogger(ID);
    private static final ResourceLocation PEER = new ResourceLocation(ID, "peer");
    private static final ResourceLocation CAUSE = new ResourceLocation(ID, "operator_seed");
    private static final String PLAYER = System.getProperty("setworksvalidation.player", "Mahghuuuls");
    private static final ThreadLocal<EntityPlayerMP> CALLBACK_PLAYER = new ThreadLocal<>();
    private static Case active;
    private static GroupBoundary groupBoundary;
    private static long nativeCallbacks;

    private Mode queued = Mode.valueOf(System.getProperty("setworksvalidation.case", "ordinary")
            .toUpperCase(Locale.ROOT));
    private ManaReadinessProvider peer;
    private EntityPlayerMP cancelDeath;
    private long transferCallbacks;

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        define("leather", "minecraft:leather_helmet", "head", "20");
        define("iron", "minecraft:iron_helmet", "head", "20");
        define("stack", "minecraft:leather_chestplate", "chest", "5");
        peer = ManaApi.registerReadinessProvider(PEER);
        ManaApi.registerContextualProvider(new ResourceLocation(ID, "in_flight"), this::evaluate);
        MinecraftForge.EVENT_BUS.register(this);
    }

    private static void define(String name, String item, String slot, String amount) {
        String id = ID + ":" + name;
        DefinitionSource source = DefinitionSource.mod(ID);
        require(SetworksApi.registerSet(SetBuilder.set(id).displayName("Validation " + name)
                .piece(PieceBuilder.piece("test").at(slot).accept(item)), source));
        Map<String, String> params = new LinkedHashMap<>();
        params.put("stat", "max_mana");
        params.put("operation", "flat");
        params.put("amount", amount);
        require(SetworksApi.registerBonus(BonusBuilder.bonus(id).requires(id, 1)
                .effect("jawmsintegrations:mana", params), source));
    }

    private static void require(RegistrationResult result) {
        if (!result.isOk()) throw new IllegalStateException(result.message());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void beginLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.player instanceof EntityPlayerMP) || !PLAYER.equalsIgnoreCase(event.player.getName())) return;
        EntityPlayerMP player = (EntityPlayerMP) event.player;
        active = queued == Mode.ORDINARY ? null : new Case(player, queued);
        queued = Mode.ORDINARY;
        if (active != null) LOG.info("CASE_BEGIN case={} player={} tick={}",
                active.mode, player.getUniqueID(), active.start);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.player instanceof EntityPlayerMP)) return;
        snapshot((EntityPlayerMP) event.player, "before_peer");
        if (active == null || active.player != event.player || active.mode == Mode.MISSING) complete(event.player);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void respawn(PlayerEvent.PlayerRespawnEvent event) {
        if (active != null && active.player.getUniqueID().equals(event.player.getUniqueID())) active = null;
        complete(event.player);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void transfer(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (PLAYER.equalsIgnoreCase(event.player.getName())) {
            LOG.info("CASE_TRANSFER_BOUNDARY phase=after player={} entity={} from={} to={} tick={} callbacks={}",
                    event.player.getUniqueID(), event.player.getEntityId(), event.fromDim, event.toDim,
                    event.player.getServer().getTickCounter(), nativeCallbacks - transferCallbacks);
        }
        if (active != null && active.player == event.player) active = null;
        complete(event.player);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void transferBegin(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (PLAYER.equalsIgnoreCase(event.player.getName())) {
            transferCallbacks = nativeCallbacks;
            LOG.info("CASE_TRANSFER_BOUNDARY phase=before player={} entity={} from={} to={} tick={}",
                    event.player.getUniqueID(), event.player.getEntityId(), event.fromDim, event.toDim,
                    event.player.getServer().getTickCounter());
        }
    }

    @SubscribeEvent
    public void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (active != null && active.player == event.player) active = null;
        if (groupBoundary != null && groupBoundary.player == event.player) groupBoundary = null;
    }

    private void complete(EntityPlayer player) {
        if (!(player instanceof EntityPlayerMP)) return;
        ManaProfileGeneration token = peer.getGeneration(player).orElseThrow(
                () -> new IllegalStateException("Fixture peer has no applicable generation"));
        ManaContributionPublicationResult result = peer.completeInitial(player, token, ManaContribution.EMPTY);
        LOG.info("CASE_PEER player={} tick={} status={} revision={}", player.getUniqueID(),
                player.getServer().getTickCounter(), result.getStatus(), result.getRevision());
        if (result.getStatus() != ManaContributionPublicationStatus.INITIAL_RECORDED) {
            throw new IllegalStateException("Fixture peer completion failed: " + result.getStatus());
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.START || active == null) return;
        Case trial = active;
        EntityPlayerMP player = trial.player;
        int elapsed = player.getServer().getTickCounter() - trial.start;
        if (trial.mode == Mode.STALE) return;
        if (elapsed == 5 && (trial.mode == Mode.SUSPENDED || trial.mode == Mode.SENSITIVITY
                || trial.mode == Mode.EMPTY_RECOVERY)) {
            player.setItemStackToSlot(EntityEquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
            LOG.info("CASE_EQUIP case={} elapsed={}", trial.mode, elapsed);
        }
        if (elapsed == 5 && trial.mode == Mode.TRANSFER_RESTORE) {
            try {
                ManaLifecycle.transfer(player, () -> {
                    player.setItemStackToSlot(EntityEquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
                    driveNativeGroup(player);
                    throw new PlannedTransferFailure();
                });
            } catch (PlannedTransferFailure expected) {
                LOG.info("CASE_TRANSFER_RESTORED player={} tick={} dimension={}",
                        player.getUniqueID(), player.getServer().getTickCounter(), player.dimension);
            }
        }
        if (elapsed == 10 && trial.mode != Mode.MISSING) {
            if (trial.mode == Mode.IN_FLIGHT) trial.fireInFlight = true;
            complete(player);
        }
        if (elapsed == 15 && trial.mode != Mode.IN_FLIGHT) {
            quietRebuild(player);
        }
        if (elapsed == 20) {
            snapshot(player, "quiet");
            inspectStatus(player);
            if (trial.mode == Mode.MISSING && ManaApi.getProfileReadiness(player) != ManaProfileReadiness.READY) {
                ManaMutationResult result = ManaApi.getManaService().setCurrentMana(player, 110, CAUSE);
                LOG.info("CASE_EARLY_MUTATION result={}", result.getFailure());
            }
        }
        if (elapsed == 110 && trial.mode != Mode.IN_FLIGHT) {
            snapshot(player, "deadline");
            inspectStatus(player);
            saveSample(trial);
        }
        if (elapsed == 135 && trial.mode != Mode.IN_FLIGHT) {
            trial.fault = false;
            ItemStack recovered = trial.mode == Mode.EMPTY_RECOVERY ? ItemStack.EMPTY : new ItemStack(Items.IRON_HELMET);
            player.setItemStackToSlot(EntityEquipmentSlot.HEAD, recovered);
            LOG.info("CASE_RECOVERY_INPUT case={} elapsed={}", trial.mode, elapsed);
        }
        if (elapsed == 150) {
            snapshot(player, "after_recovery");
            active = null;
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void completedTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || groupBoundary == null) return;
        GroupBoundary pending = groupBoundary;
        int now = pending.player.getServer().getTickCounter();
        if (now <= pending.tick) return;
        groupBoundary = null;
        observeAccepted(pending.player, "next_completed_tick", pending.tick);
    }

    private ManaContribution evaluate(EntityPlayer player) {
        if (active != null && player == active.player && active.fireInFlight) {
            active.fireInFlight = false;
            active.player.setItemStackToSlot(EntityEquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
            LOG.info("CASE_IN_FLIGHT player={} tick={}", player.getUniqueID(), player.getServer().getTickCounter());
            driveNativeGroup(player);
        }
        return ManaContribution.EMPTY;
    }

    private static void driveNativeGroup(EntityPlayer player) {
        // Drive the pinned Setworks listener at a controlled call-stack boundary.
        try {
            Object tracker = tracker();
            tracker.getClass().getMethod("onPlayerTick", TickEvent.PlayerTickEvent.class)
                    .invoke(tracker, new TickEvent.PlayerTickEvent(TickEvent.Phase.END, player));
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Could not exercise the pinned Setworks listener", failure);
        }
    }

    private static void quietRebuild(EntityPlayer player) {
        try {
            Object tracker = tracker();
            java.lang.reflect.Method rebuild = tracker.getClass().getDeclaredMethod(
                    "rebuild", EntityPlayer.class, Rebuild.class, String.class);
            rebuild.setAccessible(true);
            long before = nativeCallbacks;
            rebuild.invoke(tracker, player, Rebuild.SAME_ENTITY, "validation quiet pass");
            LOG.info("CASE_QUIET_REBUILD player={} tick={} callbacks={}", player.getUniqueID(),
                    player.getServer().getTickCounter(), nativeCallbacks - before);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Could not exercise the pinned Setworks rebuild", failure);
        }
    }

    private static Object tracker() throws ReflectiveOperationException {
        Object registrar = field(SetworksApi.class, null, "registrar");
        return field(registrar.getClass(), registrar, "tracker");
    }

    private static Object field(Class<?> owner, Object instance, String name) throws ReflectiveOperationException {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(instance);
    }

    private static void saveSample(Case trial) {
        EntityPlayerMP player = trial.player;
        player.getServer().getPlayerList().saveAllPlayerData();
        Path root = player.getServerWorld().getSaveHandler().getWorldDirectory().toPath();
        Path source = root.resolve("playerdata").resolve(player.getUniqueID() + ".dat");
        Path evidence = root.resolve("setworks-validation");
        try {
            Files.createDirectories(evidence);
            Path saved = evidence.resolve(trial.mode.name().toLowerCase(Locale.ROOT) + "-held-"
                    + player.getUniqueID() + "-" + trial.start + ".dat");
            Files.copy(source, saved);
            LOG.info("CASE_SAVED player={} path={}", player.getUniqueID(), saved);
        } catch (IOException failure) {
            LOG.error("CASE_SAVE_FAILED " + source, failure);
        }
    }

    private static void snapshot(EntityPlayerMP player, String label) {
        ManaProfileReadiness phase = ManaApi.getProfileReadiness(player);
        ManaPublicState state = ManaApi.getManaService().getState(player);
        LOG.info("CASE_SNAPSHOT label={} player={} tick={} phase={} current={} maximum={}",
                label, player.getUniqueID(), player.getServer().getTickCounter(),
                phase, state.getCurrentMana(), state.getMaximumMana());
    }

    private static void inspectStatus(EntityPlayerMP player) {
        int result = player.getServer().getCommandManager().executeCommand(
                player, "jawmsintegrations status " + player.getName());
        LOG.info("CASE_STATUS phase={} result={}", ManaApi.getProfileReadiness(player), result);
    }

    @Mod.EventHandler
    public void starting(FMLServerStartingEvent event) {
        event.registerServerCommand(new FixtureCommand());
    }

    @Mod.EventHandler
    public void started(FMLServerStartedEvent event) {
        if (!Boolean.getBoolean("setworksvalidation.preflight")) return;
        try {
            Class.forName("com.mahghuuuls.jawmsintegrations.integration.setworks.SetworksPlayerGroup");
        } catch (ClassNotFoundException failure) {
            throw new IllegalStateException("Setworks adapter missing from fixture preflight", failure);
        }
        LOG.info("CASE_PREFLIGHT dedicated fixture started; provider and mixin initialization completed");
        FMLCommonHandler.instance().getMinecraftServerInstance().initiateShutdown();
    }

    @Mod.EventHandler
    public void stopped(FMLServerStoppedEvent event) {
        active = null;
        groupBoundary = null;
        nativeCallbacks = 0;
        CALLBACK_PLAYER.remove();
    }

    public static void enterCallback(EntityPlayerMP player) {
        observeCallback(player, "afterGroup", "-");
        if (active != null && active.player == player) CALLBACK_PLAYER.set(player);
    }

    public static void exitCallback() {
        CALLBACK_PLAYER.remove();
    }

    public static void observeCallback(EntityPlayerMP player, String action, String instance) {
        if (!PLAYER.equalsIgnoreCase(player.getName())) return;
        nativeCallbacks++;
        LOG.info("CASE_NATIVE_CALLBACK action={} instance={} player={} entity={} dimension={} tick={} count={}",
                action, instance, player.getUniqueID(), player.getEntityId(), player.dimension,
                player.getServer().getTickCounter(), nativeCallbacks);
    }

    public static void observeNativeGroup(EntityPlayerMP player) {
        if (!PLAYER.equalsIgnoreCase(player.getName()) || ManaApi.getProfileReadiness(player) != ManaProfileReadiness.READY) {
            return;
        }
        int tick = player.getServer().getTickCounter();
        groupBoundary = new GroupBoundary(player, tick);
        observeAccepted(player, "group_complete", tick);
    }

    private static void observeAccepted(EntityPlayerMP player, String boundary, int groupTick) {
        if (ManaApi.getProfileReadiness(player) != ManaProfileReadiness.READY) return;
        ManaProfileExplanation profile = ManaApi.explainManaProfile(player);
        LOG.info("CASE_TIMING boundary={} player={} entity={} groupTick={} tick={} acceptedMaximum={} evaluatedWorldTick={}",
                boundary, player.getUniqueID(), player.getEntityId(), groupTick, player.getServer().getTickCounter(),
                profile.getFinalMaximumMana(), profile.getEvaluatedWorldTick());
    }

    public static boolean failVerification() {
        if (active == null || !active.fault || active.player != CALLBACK_PLAYER.get()) return false;
        LOG.info("CASE_VERIFICATION_FAULT case={} tick={}", active.mode, active.player.getServer().getTickCounter());
        return true;
    }

    public static boolean suppressSuspension(EntityPlayer player) {
        return active != null && active.player == player && active.mode == Mode.SENSITIVITY;
    }

    public static boolean testOldReceipt(EntityPlayer player) {
        return active != null && active.player == player && active.mode == Mode.IN_FLIGHT;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void cancelSelectedDeath(LivingDeathEvent event) {
        if (event.getEntityLiving() == cancelDeath) {
            event.setCanceled(true);
            LOG.info("CASE_DEATH_CANCELLED player={}", cancelDeath.getUniqueID());
        }
    }

    private enum Mode { ORDINARY, MISSING, SUSPENDED, SENSITIVITY, IN_FLIGHT, EMPTY_RECOVERY, TRANSFER_RESTORE, STALE }

    private static final class PlannedTransferFailure extends RuntimeException {
    }

    private static final class GroupBoundary {
        final EntityPlayerMP player;
        final int tick;

        GroupBoundary(EntityPlayerMP player, int tick) {
            this.player = player;
            this.tick = tick;
        }
    }

    private static final class Case {
        final EntityPlayerMP player;
        final Mode mode;
        final int start;
        boolean fault;
        boolean fireInFlight;

        Case(EntityPlayerMP player, Mode mode) {
            this.player = player;
            this.mode = mode;
            this.start = player.getServer().getTickCounter();
            fault = mode != Mode.IN_FLIGHT;
        }
    }

    private final class FixtureCommand extends CommandBase {
        @Override public String getName() { return "setworksfixture"; }
        @Override public int getRequiredPermissionLevel() { return 4; }
        @Override public String getUsage(ICommandSender sender) {
            return "/setworksfixture <seed|arm|snapshot|stale|recover|death_check|transfer|permission> [mode|dimension]";
        }

        @Override
        public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
            EntityPlayerMP player = getCommandSenderAsPlayer(sender);
            if (!PLAYER.equalsIgnoreCase(player.getName()) || args.length == 0) {
                throw new CommandException(getUsage(sender));
            }
            switch (args[0]) {
                case "seed":
                    if (active != null || ManaApi.getProfileReadiness(player) != ManaProfileReadiness.READY
                            || ManaApi.getManaService().getMaximumMana(player) != 120) {
                        throw new CommandException("Seed requires an accepted 120 maximum before the measured generation");
                    }
                    ManaApi.getManaService().setCurrentMana(player, 110, CAUSE);
                    snapshot(player, "seed");
                    server.getPlayerList().saveAllPlayerData();
                    break;
                case "arm":
                    if (args.length != 2) throw new CommandException(getUsage(sender));
                    try {
                        queued = Mode.valueOf(args[1].toUpperCase(Locale.ROOT));
                    } catch (IllegalArgumentException invalid) {
                        throw new CommandException(getUsage(sender));
                    }
                    break;
                case "snapshot":
                    snapshot(player, "operator");
                    break;
                case "stale":
                    active = new Case(player, Mode.STALE);
                    player.setItemStackToSlot(EntityEquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
                    break;
                case "recover":
                    if (active == null || active.mode != Mode.STALE) throw new CommandException("No stale case");
                    active = null;
                    player.setItemStackToSlot(EntityEquipmentSlot.HEAD, ItemStack.EMPTY);
                    break;
                case "death_check":
                    cancelDeath = player;
                    try {
                        player.onDeath(DamageSource.GENERIC);
                    } finally {
                        cancelDeath = null;
                    }
                    snapshot(player, "cancelled_death");
                    break;
                case "transfer":
                    if (args.length != 2 || !("nether".equals(args[1]) || "overworld".equals(args[1]))) {
                        throw new CommandException(getUsage(sender));
                    }
                    player.changeDimension("nether".equals(args[1]) ? -1 : 0);
                    break;
                case "permission":
                    String command = "jawmsintegrations status " + player.getName();
                    int allowed = server.getCommandManager().executeCommand(player, command);
                    int denied = server.getCommandManager().executeCommand(
                            CommandSenderWrapper.create(player).withPermissionLevel(0), command);
                    LOG.info("CASE_PERMISSION operatorResult={} levelZeroResult={}", allowed, denied);
                    if (allowed != 1 || denied != 0) {
                        throw new CommandException("Status permission boundary did not match expectations");
                    }
                    break;
                default:
                    throw new CommandException(getUsage(sender));
            }
            sender.sendMessage(new TextComponentString("Setworks fixture: " + args[0]));
        }
    }
}
