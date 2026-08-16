package com.mahghuuuls.jawmsintegrations.network;

import com.mahghuuuls.jawmsintegrations.integration.ancientspellcraft.AncientReplacementPolicy;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;

/** Sends restart-scoped authoritative presentation facts when a player joins. */
public final class AncientPresentationSync {

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.player instanceof EntityPlayerMP) {
            IntegrationNetwork.send((EntityPlayerMP) event.player,
                    IntegrationPresentationSnapshot.from(AncientReplacementPolicy.active()));
        }
    }
}
