package com.mahghuuuls.jawmsintegrations.integration.jafs;

import com.mahghuuuls.jawmsintegrations.integration.stat.ManaStatProjection;

import net.minecraft.entity.player.EntityPlayer;

/** Optional-module access without linking the native food API from common code. */
public interface FoodSourceAccess {
    /** Reads current native totals without accepting a mana profile or changing module state. */
    ManaStatProjection.Result inspect(EntityPlayer player);

    /** Ends only addon warning attribution; food data remains owned by its provider. */
    void clearWarnings();
}
