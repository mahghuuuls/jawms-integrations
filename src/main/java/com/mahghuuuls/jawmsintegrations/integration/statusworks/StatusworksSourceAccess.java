package com.mahghuuuls.jawmsintegrations.integration.statusworks;

import com.mahghuuuls.jawms.api.ManaContribution;
import net.minecraft.entity.player.EntityPlayer;

/** Common diagnostics surface without native Statusworks types. */
public interface StatusworksSourceAccess {
    PlayerStatus inspect(EntityPlayer player);

    void serverStopped();

    final class PlayerStatus {
        public final String detail;
        public final ManaContribution expected;
        public final ManaContribution applied;

        PlayerStatus(String detail, ManaContribution expected, ManaContribution applied) {
            this.detail = detail;
            this.expected = expected;
            this.applied = applied;
        }
    }
}
