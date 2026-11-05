package com.mahghuuuls.jawmsintegrations.proxy;

import com.mahghuuuls.jawmsintegrations.client.QualityToolsTooltipAdapter;
import com.mahghuuuls.jawmsintegrations.client.AncientSpellcraftTooltipAdapter;
import com.mahghuuuls.jawmsintegrations.client.ClientIntegrationPresentationCache;
import com.mahghuuuls.jawmsintegrations.client.ClientBowOwnership;
import com.mahghuuuls.jawmsintegrations.network.IntegrationPresentationSnapshot;
import io.netty.channel.Channel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.NetworkManager;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;

public final class ClientProxy extends CommonProxy {

    private boolean qualityToolsClientActive;
    private boolean ancientSpellcraftClientActive;

    @Override
    public void activateQualityToolsClient() {
        if (!qualityToolsClientActive) {
            MinecraftForge.EVENT_BUS.register(new QualityToolsTooltipAdapter());
            qualityToolsClientActive = true;
        }
    }

    @Override
    public void activateAncientSpellcraftClient() {
        if (!ancientSpellcraftClientActive) {
            MinecraftForge.EVENT_BUS.register(new AncientSpellcraftTooltipAdapter());
            FMLCommonHandler.instance().bus().register(this);
            ancientSpellcraftClientActive = true;
        }
    }

    @Override
    public void acceptPresentationSnapshot(IntegrationPresentationSnapshot snapshot) {
        ClientIntegrationPresentationCache.install(snapshot);
    }

    @Override
    public void acceptBowOwnership(NetworkManager origin, boolean enabled) {
        Minecraft minecraft = Minecraft.getMinecraft();
        ClientBowOwnership.receive(origin.channel(), enabled, () -> currentChannel(minecraft),
                minecraft::addScheduledTask);
    }

    @Override
    public boolean isClientBowOwnershipEnabled() {
        Minecraft minecraft = Minecraft.getMinecraft();
        return minecraft.isCallingFromMinecraftThread()
                && ClientBowOwnership.isEnabled(currentChannel(minecraft));
    }

    private static Channel currentChannel(Minecraft minecraft) {
        NetHandlerPlayClient connection = minecraft.getConnection();
        return connection == null ? null : connection.getNetworkManager().channel();
    }

    @Override
    public boolean isServerReplacementActive(ResourceLocation registryName) {
        return ClientIntegrationPresentationCache.isAncientReplacementActive(registryName);
    }

    @SubscribeEvent
    public void onDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        ClientIntegrationPresentationCache.clear();
    }
}
