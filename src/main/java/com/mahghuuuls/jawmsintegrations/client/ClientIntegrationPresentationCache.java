package com.mahghuuuls.jawmsintegrations.client;

import com.mahghuuuls.jawmsintegrations.integration.ancientspellcraft.AncientReplacement;
import com.mahghuuuls.jawmsintegrations.network.IntegrationPresentationSnapshot;
import net.minecraft.util.ResourceLocation;

/** Connection-scoped accepted server presentation facts; empty state changes no tooltip. */
public final class ClientIntegrationPresentationCache {

    private static volatile IntegrationPresentationSnapshot snapshot;

    private ClientIntegrationPresentationCache() {
    }

    public static void install(IntegrationPresentationSnapshot value) {
        snapshot = value;
    }

    public static void clear() {
        snapshot = null;
    }

    public static boolean isQualityToolsActive() {
        IntegrationPresentationSnapshot current = snapshot;
        return current != null && current.isQualityToolsActive();
    }

    public static IntegrationPresentationSnapshot.Entry ancientEntry(ResourceLocation registryName) {
        IntegrationPresentationSnapshot current = snapshot;
        AncientReplacement replacement = AncientReplacement.forRegistryName(registryName);
        return current == null || replacement == null ? null : current.get(replacement);
    }

    public static boolean isAncientReplacementActive(ResourceLocation registryName) {
        IntegrationPresentationSnapshot.Entry entry = ancientEntry(registryName);
        return entry != null && entry.isEnabled();
    }
}
