package com.mahghuuuls.jawmsintegrations.network;

import com.mahghuuuls.jawmsintegrations.integration.ancientspellcraft.AncientReplacementPolicy;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;

import java.util.function.BooleanSupplier;

/** Sends restart-scoped authoritative integration presentation facts when a player joins. */
public final class IntegrationPresentationSync {

    private final boolean qualityToolsActive;
    private final BooleanSupplier spellStaminaActive;

    public IntegrationPresentationSync(boolean qualityToolsActive, BooleanSupplier spellStaminaActive) {
        this.qualityToolsActive = qualityToolsActive;
        this.spellStaminaActive = spellStaminaActive;
    }

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.player instanceof EntityPlayerMP) {
            EntityPlayerMP player = (EntityPlayerMP) event.player;
            IntegrationNetwork.send(player,
                    IntegrationPresentationSnapshot.from(
                            qualityToolsActive, AncientReplacementPolicy.active()));
            IntegrationNetwork.sendBowOwnership(player, spellStaminaActive.getAsBoolean());
        }
    }
}
