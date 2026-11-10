package com.mahghuuuls.jawmsintegrations.integration.statusworks;

import com.mahghuuuls.jawmsintegrations.integration.stat.ManaStatProjection.Stat;
import com.mahghuuuls.statusworks.api.RegistrationResult;
import com.mahghuuuls.statusworks.api.StatusworksApi;
import com.mahghuuuls.statusworks.api.contribution.ContributionType;
import net.minecraft.entity.EntityLivingBase;

import java.util.ArrayList;
import java.util.List;

/** Native definition targets using the base API shared by supported authoring releases. */
final class StatusworksAuthoring {
    private StatusworksAuthoring() {
    }

    static void register() {
        for (String id : typeIds()) {
            RegistrationResult result = StatusworksApi.registerContributionType(new InertType(id));
            if (!result.isAccepted()) {
                throw new IllegalStateException(id + ": " + result.error());
            }
        }
    }

    static List<String> typeIds() {
        List<String> ids = new ArrayList<>();
        for (Stat stat : Stat.values()) {
            String id = stat.getId();
            if (stat.supportsPercent()) {
                ids.add(id + "_flat");
                ids.add(id + "_percent");
            } else {
                ids.add(id);
            }
        }
        return ids;
    }

    private static final class InertType implements ContributionType {
        private final String id;

        private InertType(String id) {
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public boolean appliesTo(EntityLivingBase entity) {
            return false;
        }

        @Override
        public void onValue(EntityLivingBase entity, String key, double value) {
        }

        @Override
        public void onEnd(EntityLivingBase entity, String key) {
        }
    }
}
