package com.mahghuuuls.jawmsintegrations.client;

import com.mahghuuuls.jawmsintegrations.integration.ancientspellcraft.AncientReplacement;
import com.mahghuuuls.jawmsintegrations.network.IntegrationPresentationSnapshot;
import net.minecraft.util.ResourceLocation;

/** Connection-scoped client cache; empty state preserves native Ancient presentation. */
public final class ClientAncientPresentationCache {

    private static volatile IntegrationPresentationSnapshot snapshot;

    private ClientAncientPresentationCache() {
    }

    public static void install(IntegrationPresentationSnapshot value) {
        snapshot = value;
    }

    public static void clear() {
        snapshot = null;
    }

    public static IntegrationPresentationSnapshot.Entry entry(ResourceLocation registryName) {
        IntegrationPresentationSnapshot current = snapshot;
        AncientReplacement replacement = AncientReplacement.forRegistryName(registryName);
        return current == null || replacement == null ? null : current.get(replacement);
    }

    public static boolean isActive(ResourceLocation registryName) {
        IntegrationPresentationSnapshot.Entry entry = entry(registryName);
        return entry != null && entry.isEnabled();
    }
}
