package com.mahghuuuls.jawmsintegrations.integration.jass;

import net.minecraft.entity.player.EntityPlayer;

/** Read-only status and server-run diagnostic lifecycle, without optional API types. */
public interface SpellStaminaAccess {
    String inspect(EntityPlayer player);

    void serverStopped();
}
