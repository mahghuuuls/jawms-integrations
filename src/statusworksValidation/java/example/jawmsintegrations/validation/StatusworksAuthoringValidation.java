package example.jawmsintegrations.validation;

import com.mahghuuuls.jawms.api.ManaApi;
import com.mahghuuuls.jawms.api.ManaProviderDescriptor;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.stream.Collectors;

@Mod(modid = "jawmsint_authoring_validation", name = "Statusworks Authoring Validation", version = "dev",
        dependencies = "required-after:jawmsintegrations;after:statusworks")
public final class StatusworksAuthoringValidation {
    private static final Logger LOGGER = LogManager.getLogger("statusworks_authoring_validation");

    @Mod.EventHandler
    public void register(FMLInitializationEvent event) {
        if (Loader.isModLoaded("statusworks")) StatusworksDefinitions.register();
        MinecraftForge.EVENT_BUS.register(this);
    }

    @Mod.EventHandler
    public void registerCommand(FMLServerStartingEvent event) {
        event.registerServerCommand(new AdmissionCommand());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void login(PlayerLoggedInEvent event) {
        if (event.player instanceof EntityPlayerMP) {
            record((EntityPlayerMP) event.player, "login");
        }
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
}
