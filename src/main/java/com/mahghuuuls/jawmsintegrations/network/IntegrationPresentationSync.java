package com.mahghuuuls.jawmsintegrations.network;

import com.mahghuuuls.jawmsintegrations.integration.ancientspellcraft.AncientReplacementPolicy;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;

/** Sends restart-scoped authoritative integration presentation facts when a player joins. */
public final class IntegrationPresentationSync {

    private final boolean qualityToolsActive;

    public IntegrationPresentationSync(boolean qualityToolsActive) {
        this.qualityToolsActive = qualityToolsActive;
    }

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.player instanceof EntityPlayerMP) {
            IntegrationNetwork.send((EntityPlayerMP) event.player,
                    IntegrationPresentationSnapshot.from(
                            qualityToolsActive, AncientReplacementPolicy.active()));
        }
    }
}
