package com.mahghuuuls.jawmsintegrations.proxy;

import com.mahghuuuls.jawmsintegrations.client.QualityToolsTooltipAdapter;
import net.minecraftforge.common.MinecraftForge;

public final class ClientProxy extends CommonProxy {

    private boolean qualityToolsClientActive;

    @Override
    public void activateQualityToolsClient() {
        if (!qualityToolsClientActive) {
            MinecraftForge.EVENT_BUS.register(new QualityToolsTooltipAdapter());
            qualityToolsClientActive = true;
        }
    }
}
