package com.mahghuuuls.jawmsintegrations.integration.statusworks;

import com.mahghuuuls.jawmsintegrations.integration.stat.ManaStatProjection.Stat;
import com.mahghuuuls.statusworks.api.RegistrationResult;
import com.mahghuuuls.statusworks.api.StatusworksApi;
import com.mahghuuuls.statusworks.api.contribution.ContributionType;
import net.minecraft.entity.EntityLivingBase;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** Native definition targets using the base API shared by supported authoring releases. */
final class StatusworksAuthoring {
    private StatusworksAuthoring() {
    }

    static void register() {
        registerTypes(InertType::new);
    }

    static void registerTypes(Function<String, ContributionType> factory) {
        for (String id : typeIds()) {
            RegistrationResult result = StatusworksApi.registerContributionType(factory.apply(id));
            if (!result.isAccepted()) {
                throw new IllegalStateException(id + ": " + result.error());
            }
        }
    }

    static List<String> typeIds() {
        List<String> ids = new ArrayList<>();
        for (Stat stat : Stat.values()) {
            ids.add(id(stat, false));
            if (stat.supportsPercent()) ids.add(id(stat, true));
        }
        return ids;
    }

    static String id(Stat stat, boolean percent) {
        return stat.getId() + (stat.supportsPercent() ? (percent ? "_percent" : "_flat") : "");
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
