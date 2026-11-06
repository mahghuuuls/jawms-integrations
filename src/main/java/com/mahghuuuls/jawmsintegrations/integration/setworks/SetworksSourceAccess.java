package com.mahghuuuls.jawmsintegrations.integration.setworks;

import com.mahghuuuls.jawms.api.ManaContribution;
import net.minecraft.entity.player.EntityPlayer;

/** Read-only Setworks status exposed without linking the optional readiness API. */
public interface SetworksSourceAccess {
    PlayerStatus inspect(EntityPlayer player);

    void serverStopped();

    final class PlayerStatus {
        private final String detail;
        private final ManaContribution applied;

        PlayerStatus(String detail, ManaContribution applied) {
            this.detail = detail;
            this.applied = applied;
        }

        public String getDetail() {
            return detail;
        }

        public ManaContribution getApplied() {
            return applied;
        }
    }
}
