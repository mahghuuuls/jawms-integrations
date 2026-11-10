package example.jawmsintegrations.validation;

import com.mahghuuuls.jawms.api.ManaApi;
import com.mahghuuuls.jawms.api.ManaProfileExplanation;
import com.mahghuuuls.jawms.api.ManaProfileReadiness;
import com.mahghuuuls.jawms.api.ManaProviderDescriptor;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.MobEffects;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppedEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Mod(modid = "jawmsint_authoring_validation", name = "Statusworks Authoring Validation", version = "dev",
        dependencies = "required-after:jawmsintegrations;after:statusworks")
public final class StatusworksAuthoringValidation {
    private static final Logger LOGGER = LogManager.getLogger("statusworks_authoring_validation");
    private static final boolean GAMEPLAY = Boolean.getBoolean("statusworksProof.gameplayDefinitions");
    private EntityPlayer observedClient;
    private final Map<UUID, EntityPlayerMP> live = new HashMap<>();
    private final Map<UUID, EntityPlayerMP> retired = new HashMap<>();
    private final Map<UUID, String> observedProfiles = new HashMap<>();

    @Mod.EventHandler
    public void register(FMLInitializationEvent event) {
        if (Loader.isModLoaded("statusworks")) {
            StatusworksDefinitions.register();
            if (GAMEPLAY) StatusworksDefinitions.registerGameplayDefinitions();
        }
        MinecraftForge.EVENT_BUS.register(this);
    }

    @Mod.EventHandler
    public void registerCommand(FMLServerStartingEvent event) {
        event.registerServerCommand(new AdmissionCommand());
        if (GAMEPLAY) event.registerServerCommand(new ManaCommand());
        if (GAMEPLAY) event.registerServerCommand(new EligibilityCommand());
        if (GAMEPLAY) event.registerServerCommand(new PermissionCommand());
        if (GAMEPLAY) event.registerServerCommand(new LifecycleCommand());
        if (GAMEPLAY) event.registerServerCommand(new RespawnCommand());
        if (GAMEPLAY) event.registerServerCommand(new NativeSnapshotCommand());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.player instanceof EntityPlayerMP) {
            record((EntityPlayerMP) event.player, "login");
            track(event.player, "login");
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void respawn(PlayerEvent.PlayerRespawnEvent event) {
        track(event.player, "respawn");
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        track(event.player, "dimension");
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (GAMEPLAY && live.remove(event.player.getUniqueID(), event.player)) {
            retired.put(event.player.getUniqueID(), (EntityPlayerMP) event.player);
            observedProfiles.remove(event.player.getUniqueID());
        }
    }

    @Mod.EventHandler
    public void stopped(FMLServerStoppedEvent event) {
        live.clear();
        retired.clear();
        observedProfiles.clear();
    }

    private void track(EntityPlayer entity, String phase) {
        if (!GAMEPLAY || !(entity instanceof EntityPlayerMP) || entity.world.isRemote) return;
        EntityPlayerMP player = (EntityPlayerMP) entity;
        EntityPlayerMP previous = live.put(player.getUniqueID(), player);
        if (previous != null && previous != player) {
            retired.put(player.getUniqueID(), previous);
            observedProfiles.remove(player.getUniqueID());
        }
        LOGGER.info("STATUSWORKS_LIFECYCLE phase={} player={} object={} dimension={} tick={}",
                phase, player.getUniqueID(), System.identityHashCode(player), player.dimension,
                player.getServer().getTickCounter());
    }

    @SubscribeEvent
    public void clientTick(TickEvent.PlayerTickEvent event) {
        if (GAMEPLAY && event.phase == TickEvent.Phase.END && event.side.isClient() && observedClient != event.player) {
            observedClient = event.player;
            LOGGER.info("STATUSWORKS_ELIGIBILITY kind=client group={}", StatusworksDefinitions.availability(event.player));
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void observeAccepted(TickEvent.ServerTickEvent event) {
        if (!GAMEPLAY || event.phase != TickEvent.Phase.END) return;
        for (EntityPlayerMP player : live.values()) {
            if (ManaApi.getProfileReadiness(player) != ManaProfileReadiness.READY
                    && ManaApi.getProfileReadiness(player) != ManaProfileReadiness.LEGACY) continue;
            ManaProfileExplanation profile = ManaApi.explainManaProfile(player);
            String values = acceptedValues(profile);
            if (!values.equals(observedProfiles.put(player.getUniqueID(), values))) {
                LOGGER.info("STATUSWORKS_ACCEPTED phase=server_end tick={} evaluatedTick={} player={} current={} {}",
                        player.getServer().getTickCounter(), profile.getEvaluatedWorldTick(), player.getUniqueID(),
                        ManaApi.getManaService().getCurrentMana(player), values);
            }
        }
    }

    private static String acceptedValues(ManaProfileExplanation profile) {
        return "maximum=" + profile.getFinalMaximumMana() + " regeneration=" + profile.getFinalRegeneration()
                + " lockoutTicks=" + profile.getFinalLockoutTicks() + " efficiency=" + profile.getGlobalSpellEfficiency()
                + " schools=" + profile.getElementSpellEfficiency();
    }

    private static void record(EntityPlayerMP player, String phase) {
        LOGGER.info("ADMISSION_READINESS phase={} player={} state={} providers={}",
                phase, player.getUniqueID(), ManaApi.getProfileReadiness(player),
                ManaApi.getRegisteredProviders().stream()
                        .map(ManaProviderDescriptor::getProviderId)
                        .collect(Collectors.toList()));
    }

    private static final class AdmissionCommand extends CommandBase {
        @Override
        public String getName() {
            return "admissionproof";
        }

        @Override
        public String getUsage(ICommandSender sender) {
            return "/admissionproof <player>";
        }

        @Override
        public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
            if (args.length != 1) throw new WrongUsageException(getUsage(sender));
            record(getPlayer(server, sender, args[0]), "operator");
        }
    }

    private static final class ManaCommand extends CommandBase {
        private static final ResourceLocation CAUSE = new ResourceLocation("jawmsint_authoring_validation", "seed");

        @Override public String getName() { return "statusmanatest"; }

        @Override public String getUsage(ICommandSender sender) { return "/statusmanatest <player> [seed]"; }

        @Override
        public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
            if (args.length < 1 || args.length > 2) throw new WrongUsageException(getUsage(sender));
            EntityPlayerMP player = getPlayer(server, sender, args[0]);
            if (args.length == 2 && !ManaApi.getManaService()
                    .setCurrentMana(player, parseInt(args[1], 0), CAUSE).isSuccessful()) {
                throw new CommandException("JAWMS did not accept the test seed.");
            }
            LOGGER.info("STATUSWORKS_MANA tick={} player={} readiness={} current={} maximum={} speed={}",
                    server.getTickCounter(), player.getUniqueID(), ManaApi.getProfileReadiness(player),
                    ManaApi.getManaService().getCurrentMana(player), ManaApi.getManaService().getMaximumMana(player),
                    player.isPotionActive(MobEffects.SPEED));
        }
    }

    private static final class EligibilityCommand extends CommandBase {
        @Override public String getName() { return "statuseligibility"; }

        @Override public String getUsage(ICommandSender sender) { return "/statuseligibility <player>"; }

        @Override
        public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
            if (args.length != 1) throw new WrongUsageException(getUsage(sender));
            EntityPlayerMP player = getPlayer(server, sender, args[0]);
            LOGGER.info("STATUSWORKS_ELIGIBILITY {}", StatusworksDefinitions.verifyNonPlayers(player));
        }
    }

    private static final class PermissionCommand extends CommandBase {
        @Override public String getName() { return "statuspermissions"; }

        @Override public String getUsage(ICommandSender sender) { return "/statuspermissions <player> <allowed|denied>"; }

        @Override
        public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
            if (args.length != 2 || (!"allowed".equals(args[1]) && !"denied".equals(args[1]))) {
                throw new WrongUsageException(getUsage(sender));
            }
            EntityPlayerMP player = getPlayer(server, sender, args[0]);
            int before = ManaApi.getManaService().getCurrentMana(player);
            ManaProfileReadiness readiness = ManaApi.getProfileReadiness(player);
            ManaProfileExplanation accepted = ManaApi.explainManaProfile(player);
            String nativeState = StatusworksDefinitions.nativeSnapshot(player);
            int overall = server.getCommandManager().executeCommand(player, "jawmsintegrations status");
            int selected = server.getCommandManager().executeCommand(player, "jawmsintegrations status " + player.getName());
            ManaProfileExplanation after = ManaApi.explainManaProfile(player);
            boolean unchanged = readiness == ManaApi.getProfileReadiness(player)
                    && accepted.getEvaluatedWorldTick() == after.getEvaluatedWorldTick()
                    && accepted.getGameplayRulesRevision() == after.getGameplayRulesRevision()
                    && acceptedValues(accepted).equals(acceptedValues(after))
                    && nativeState.equals(StatusworksDefinitions.nativeSnapshot(player));
            LOGGER.info("STATUSWORKS_PERMISSION player={} expected={} overall={} selected={} stateUnchanged={}",
                    player.getUniqueID(), args[1], overall, selected, unchanged);
            boolean allowed = "allowed".equals(args[1]);
            if ((overall > 0) != allowed || (selected > 0) != allowed || !unchanged
                    || before != ManaApi.getManaService().getCurrentMana(player)
                    || accepted.getFinalMaximumMana() != ManaApi.getManaService().getMaximumMana(player)) {
                throw new CommandException("Unexpected permission or mana result.");
            }
        }
    }

    private final class LifecycleCommand extends CommandBase {
        @Override public String getName() { return "statuslifecycle"; }

        @Override public String getUsage(ICommandSender sender) { return "/statuslifecycle <player>"; }

        @Override
        public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
            if (args.length != 1) throw new WrongUsageException(getUsage(sender));
            EntityPlayerMP player = getPlayer(server, sender, args[0]);
            EntityPlayerMP old = retired.get(player.getUniqueID());
            LOGGER.info("STATUSWORKS_LIFECYCLE phase=inspect player={} object={} dimension={} readiness={} group={}"
                            + " retiredObject={} retiredGroup={} tick={}",
                    player.getUniqueID(), System.identityHashCode(player), player.dimension,
                    ManaApi.getProfileReadiness(player), StatusworksDefinitions.availability(player),
                    old == null ? "-" : System.identityHashCode(old),
                    old == null ? "-" : StatusworksDefinitions.availability(old), server.getTickCounter());
        }
    }

    private static final class RespawnCommand extends CommandBase {
        @Override public String getName() { return "statusrespawn"; }

        @Override public String getUsage(ICommandSender sender) { return "/statusrespawn <player>"; }

        @Override
        public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
            if (args.length != 1) throw new WrongUsageException(getUsage(sender));
            EntityPlayerMP player = getPlayer(server, sender, args[0]);
            if (server.getCommandManager().executeCommand(player, "devtool client respawn") != 1) {
                throw new CommandException("Toolkit did not dispatch respawn.");
            }
        }
    }

    private static final class NativeSnapshotCommand extends CommandBase {
        @Override public String getName() { return "statusnative"; }

        @Override public String getUsage(ICommandSender sender) { return "/statusnative <player>"; }

        @Override
        public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
            if (args.length != 1) throw new WrongUsageException(getUsage(sender));
            EntityPlayerMP player = getPlayer(server, sender, args[0]);
            LOGGER.info("STATUSWORKS_NATIVE phase=command tick={} player={} {}",
                    server.getTickCounter(), player.getUniqueID(), StatusworksDefinitions.nativeSnapshot(player));
        }
    }
}
