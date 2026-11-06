package com.mahghuuuls.jawmsintegrations.validation;

import com.mahghuuuls.jafs.api.JafsApi;
import com.mahghuuuls.jafs.api.PropertyOperation;
import com.mahghuuuls.jafs.api.PropertySpec;
import com.mahghuuuls.jafs.api.PropertyValue;
import com.mahghuuuls.jawms.api.ManaApi;
import com.mahghuuuls.jawms.api.ManaContribution;
import com.mahghuuuls.jawms.api.ManaContributionExplanation;
import com.mahghuuuls.jawms.api.ManaProfileExplanation;
import com.mahghuuuls.jawmsintegrations.integration.stat.ManaStatProjection;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.FMLCommonHandler;
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
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/** Development-only observer: public reads and bounded logging, no mana dirty/set/getState calls. */
@Mod(modid = "jawmsintegrationstest", name = "JAFS reconciliation observer", version = "dev",
        dependencies = "required-after:jawmsintegrations;required-after:jafs")
public final class JafsTimingProbe {
    private static final Logger LOG = LogManager.getLogger("JAFS reconciliation observer");
    private final Map<UUID, Long> pending = new HashMap<>();
    private long tick;
    private long until;

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        JafsApi.registerProperty(PropertySpec.builder("jawmsintegrationstest:tick_probe")
                .operation(PropertyOperation.FLAT, -Double.MAX_VALUE, Double.MAX_VALUE).build(),
                (player, flat, percent) -> {
                    if (tick >= until) return;
                    pending.put(player.getUniqueID(), tick);
                    LOG.info("JAFS_PROBE callback seq={} worldTick={} dimension={} player={}",
                            tick, player.world.getTotalWorldTime(), player.dimension, player.getUniqueID());
                });
        MinecraftForge.EVENT_BUS.register(this);
    }

    @Mod.EventHandler
    public void starting(FMLServerStartingEvent event) {
        event.registerServerCommand(new CommandBase() {
            @Override public String getName() { return "jafs-probe"; }
            @Override public String getUsage(ICommandSender sender) { return "/jafs-probe <ticks:0..12000>"; }
            @Override public int getRequiredPermissionLevel() { return 2; }
            @Override public void execute(MinecraftServer server, ICommandSender sender, String[] args)
                    throws CommandException {
                if (args.length != 1) throw new net.minecraft.command.WrongUsageException(getUsage(sender));
                int duration = parseInt(args[0], 0, 12000);
                pending.clear();
                until = tick + duration;
                LOG.info("JAFS_PROBE armed seq={} ticks={}", tick, duration);
            }
        });
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void tick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.START) { tick++; return; }
        if (tick >= until) { pending.clear(); return; }
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        Iterator<Map.Entry<UUID, Long>> iterator = pending.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Long> entry = iterator.next();
            EntityPlayerMP player = server.getPlayerList().getPlayerByUUID(entry.getKey());
            if (player == null) { iterator.remove(); continue; }
            try {
                ManaStatProjection.Result current = ManaStatProjection.project(stat -> {
                    PropertyValue value = JafsApi.getFoodService().getPropertyValue(player, stat.getId());
                    return new ManaStatProjection.Totals(value.flat(), value.percent());
                });
                ManaProfileExplanation profile = ManaApi.explainManaProfile(player);
                ManaContribution accepted = ManaContribution.EMPTY;
                for (ManaContributionExplanation contribution : profile.getContributions()) {
                    if ("jawmsintegrations:jafs_foods".equals(contribution.getSourceId().toString())
                            && contribution.hasAcceptedContribution()) accepted = contribution.getAcceptedContribution();
                }
                boolean matches = fields(current.getContribution()).equals(fields(accepted));
                long elapsed = tick - entry.getValue();
                LOG.info("JAFS_PROBE end seq={} callbackSeq={} elapsed={} player={} dimension={} "
                                + "worldTick={} acceptedTick={} maximum={} regen={} delayTicks={} matches={} "
                                + "nativeProjected={} accepted={}", tick, entry.getValue(), elapsed,
                        player.getUniqueID(), player.dimension, player.world.getTotalWorldTime(),
                        profile.getEvaluatedWorldTick(), profile.getFinalMaximumMana(),
                        profile.getFinalRegeneration(), profile.getFinalLockoutTicks(), matches,
                        fields(current.getContribution()), fields(accepted));
                if (matches || elapsed >= 1) iterator.remove();
            } catch (RuntimeException | LinkageError unavailable) {
                LOG.info("JAFS_PROBE unavailable seq={} callbackSeq={} player={} reason={}",
                        tick, entry.getValue(), entry.getKey(), unavailable.getClass().getSimpleName());
                if (tick - entry.getValue() >= 1) iterator.remove();
            }
        }
    }

    private static String fields(ManaContribution value) {
        return value.getFlatMaximumMana() + "/" + value.getMaximumManaIncrease() + "/"
                + value.getMaximumManaReduction() + "/" + value.getFlatRegeneration() + "/"
                + value.getRegenerationIncrease() + "/" + value.getRegenerationReduction() + "/"
                + value.getFlatLockoutSeconds() + "/" + value.getLockoutIncrease() + "/"
                + value.getLockoutReduction() + "/" + value.getGlobalSpellEfficiency() + "/"
                + value.getElementSpellEfficiency();
    }

    @SubscribeEvent
    public void logout(PlayerEvent.PlayerLoggedOutEvent event) { pending.remove(event.player.getUniqueID()); }

    @Mod.EventHandler
    public void stopped(FMLServerStoppedEvent event) { pending.clear(); tick = 0; until = 0; }
}
