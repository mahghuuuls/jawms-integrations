package com.mahghuuuls.jawmsintegrations.proxy;

import com.mahghuuuls.jawmsintegrations.client.QualityToolsTooltipAdapter;
import com.mahghuuuls.jawmsintegrations.client.AncientSpellcraftTooltipAdapter;
import com.mahghuuuls.jawmsintegrations.client.ClientIntegrationPresentationCache;
import com.mahghuuuls.jawmsintegrations.network.IntegrationPresentationSnapshot;
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
    public boolean isServerReplacementActive(ResourceLocation registryName) {
        return ClientIntegrationPresentationCache.isAncientReplacementActive(registryName);
    }

    @SubscribeEvent
    public void onDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        ClientIntegrationPresentationCache.clear();
    }
}
