package com.mahghuuuls.jawmsintegrations.integration.setworks;

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
import com.mahghuuuls.setworks.api.IEffectKind;
import com.mahghuuuls.setworks.api.WithdrawReason;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

/** Server-thread adapter for Setworks callbacks and exact JAWMS lifecycle tokens. */
public final class SetworksGameplay implements SetworksManaEffect.Callbacks, SetworksSourceAccess {
    private static final ResourceLocation SOURCE = new ResourceLocation(Tags.MOD_ID, "setworks");

    private final Map<UUID, PlayerContext> players = new HashMap<>();
    private ManaReadinessProvider provider;

    private SetworksGameplay() {
    }

    static SetworksSourceAccess activate(SetworksManaEffect effect) {
        verifyContracts();
        SetworksGameplay gameplay = new SetworksGameplay();
        MinecraftForge.EVENT_BUS.register(gameplay);
        try {
            gameplay.provider = ManaApi.registerReadinessProvider(SOURCE);
        } catch (RuntimeException | LinkageError failure) {
            MinecraftForge.EVENT_BUS.unregister(gameplay);
            throw failure;
        }
        effect.install(gameplay);
        return gameplay;
    }

    private static void verifyContracts() {
        try {
            requireReturn(ManaApi.class.getMethod("registerReadinessProvider", ResourceLocation.class),
                    ManaReadinessProvider.class);
            requireReturn(ManaApi.class.getMethod("getProfileReadiness", EntityPlayer.class),
                    ManaProfileReadiness.class);
            requireReturn(ManaReadinessProvider.class.getMethod("getGeneration", EntityPlayer.class),
                    Optional.class);
            requireReturn(ManaReadinessProvider.class.getMethod("completeInitial", EntityPlayer.class,
                    ManaProfileGeneration.class, ManaContribution.class), ManaContributionPublicationResult.class);
            requireReturn(ManaReadinessProvider.class.getMethod("suspend", EntityPlayer.class,
                    ManaProfileGeneration.class, long.class), ManaContributionPublicationResult.class);
            requireReturn(ManaReadinessProvider.class.getMethod("update", EntityPlayer.class,
                    ManaProfileGeneration.class, long.class, ManaContribution.class), ManaContributionPublicationResult.class);
            requireReturn(ManaContributionPublicationResult.class.getMethod("getStatus"),
                    ManaContributionPublicationStatus.class);
            requireReturn(ManaContributionPublicationResult.class.getMethod("getRevision"),
                    OptionalLong.class);
            IEffectKind.class.getMethod("afterGroup", EntityPlayerMP.class);
            IEffectKind.class.getMethod("withdraw", EntityPlayerMP.class, String.class, Map.class, WithdrawReason.class);
        } catch (NoSuchMethodException missing) {
            throw new LinkageError("Missing Setworks grouping or JAWMS readiness capability", missing);
        }
    }

    private static void requireReturn(Method method, Class<?> type) throws NoSuchMethodException {
        if (method.getReturnType() != type) throw new NoSuchMethodException(method.toString());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void beginLogin(PlayerEvent.PlayerLoggedInEvent event) {
        begin(event.player, true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void beginRespawn(PlayerEvent.PlayerRespawnEvent event) {
        begin(event.player, true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void beginTransfer(PlayerEvent.PlayerChangedDimensionEvent event) {
        begin(event.player, false);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void finishLogin(PlayerEvent.PlayerLoggedInEvent event) {
        finish(event.player);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void finishRespawn(PlayerEvent.PlayerRespawnEvent event) {
        finish(event.player);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void finishTransfer(PlayerEvent.PlayerChangedDimensionEvent event) {
        finish(event.player);
    }

    private void begin(EntityPlayer entity, boolean fresh) {
        EntityPlayerMP player = serverPlayer(entity);
        if (player == null) return;
        PlayerContext context = players.get(player.getUniqueID());
        if (fresh || context == null || !context.matches(player)) {
            context = new PlayerContext(player);
            players.put(player.getUniqueID(), context);
        }
        context.world = player.world;
        context.lifecycle = true;
        context.group.bind(generation(player));
    }

    private void finish(EntityPlayer entity) {
        PlayerContext context = context(entity);
        if (context == null || !context.lifecycle) return;
        context.lifecycle = false;
        context.group.finish(true);
        context.group.publish(generation(context.player));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        EntityPlayerMP player = serverPlayer(event.player);
        if (player == null) return;
        PlayerContext context = players.get(player.getUniqueID());
        if (context != null && context.player == player) players.remove(player.getUniqueID());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !event.side.isServer()) return;
        PlayerContext context = context(event.player);
        if (context != null && !context.lifecycle) publish(context);
    }

    @Override
    public void apply(EntityPlayerMP player, String instanceId, Map<String, String> params) {
        PlayerContext context = context(player);
        if (context != null) context.group.apply(instanceId, params);
    }

    @Override
    public void withdraw(EntityPlayerMP player, String instanceId, WithdrawReason reason) {
        if (reason == WithdrawReason.LEAVING) return;
        PlayerContext context = context(player);
        if (context != null) context.group.withdraw(instanceId);
    }

    @Override
    public void afterGroup(EntityPlayerMP player) {
        PlayerContext context = context(player);
        if (context == null) return;
        context.group.finish(false);
        if (!context.lifecycle) publish(context);
    }

    private void publish(PlayerContext context) {
        ManaProfileGeneration current = generation(context.player);
        context.group.bind(current);
        context.group.publish(current);
    }

    private ManaProfileGeneration generation(EntityPlayerMP player) {
        return provider.getGeneration(player).orElse(null);
    }

    @Override
    public PlayerStatus inspect(EntityPlayer entity) {
        PlayerContext context = context(entity);
        if (context == null) return new PlayerStatus("no reconciled player context", null);
        ManaProfileReadiness phase = ManaApi.getProfileReadiness(entity);
        ManaContribution applied = null;
        if (phase == ManaProfileReadiness.READY) {
            for (ManaContributionExplanation source : ManaApi.explainManaProfile(entity).getContributions()) {
                if (!SOURCE.equals(source.getSourceId())) continue;
                if (source.getDisposition() == ManaContributionDisposition.EMPTY) applied = ManaContribution.EMPTY;
                else if (source.hasAcceptedContribution()) applied = source.getAcceptedContribution();
                break;
            }
        }
        return context.group.inspect(phase, applied);
    }

    @Override
    public void serverStopped() {
        players.clear();
    }

    private PlayerContext context(EntityPlayer entity) {
        EntityPlayerMP player = serverPlayer(entity);
        if (player == null) return null;
        PlayerContext context = players.get(player.getUniqueID());
        return context != null && context.matches(player) && context.world == player.world ? context : null;
    }

    private static EntityPlayerMP serverPlayer(EntityPlayer entity) {
        if (!(entity instanceof EntityPlayerMP) || entity instanceof FakePlayer
                || !(entity.world instanceof WorldServer)
                || !((WorldServer) entity.world).isCallingFromMinecraftThread()) return null;
        return (EntityPlayerMP) entity;
    }

    private final class PlayerContext implements SetworksPlayerGroup.Slot {
        private final EntityPlayerMP player;
        private final NetHandlerPlayServer connection;
        private final SetworksPlayerGroup group;
        private World world;
        private boolean lifecycle;

        PlayerContext(EntityPlayerMP player) {
            this.player = player;
            this.connection = player.connection;
            group = new SetworksPlayerGroup(this, detail -> JawmsIntegrationsMod.LOGGER.warn(
                    "Setworks mana for {}: {}", player.getName(), detail));
        }

        boolean matches(EntityPlayerMP candidate) {
            return player == candidate && connection == candidate.connection;
        }

        @Override
        public ManaProfileReadiness readiness() {
            return ManaApi.getProfileReadiness(player);
        }

        @Override
        public ManaContributionPublicationResult complete(ManaProfileGeneration token, ManaContribution contribution) {
            return provider.completeInitial(player, token, contribution);
        }

        @Override
        public ManaContributionPublicationResult suspend(ManaProfileGeneration token, long revision) {
            return provider.suspend(player, token, revision);
        }

        @Override
        public ManaContributionPublicationResult update(ManaProfileGeneration token, long revision,
                                                        ManaContribution contribution) {
            return provider.update(player, token, revision, contribution);
        }
    }
}
