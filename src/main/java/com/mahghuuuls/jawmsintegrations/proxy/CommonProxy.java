package com.mahghuuuls.jawmsintegrations.proxy;

import com.mahghuuuls.jawmsintegrations.network.IntegrationPresentationSnapshot;
import net.minecraft.util.ResourceLocation;

/** Dedicated-server-safe boundary for optional client presentation. */
public class CommonProxy {

    public void activateQualityToolsClient() {
        // Client-only tooltip presentation has no server implementation.
    }

    public void activateAncientSpellcraftClient() {
        // Client-only Ancient presentation has no server implementation.
    }

    public void acceptPresentationSnapshot(IntegrationPresentationSnapshot snapshot) {
        // S2C messages are handled only by the physical client proxy.
    }

    public boolean isServerReplacementActive(ResourceLocation registryName) {
        return false;
    }
}
