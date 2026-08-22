package com.mahghuuuls.jawmsintegrations.client;

import com.mahghuuuls.jawmsintegrations.config.IntegrationConfigSnapshot;
import com.mahghuuuls.jawmsintegrations.integration.ancientspellcraft.AncientReplacement;
import com.mahghuuuls.jawmsintegrations.integration.ancientspellcraft.AncientReplacementPolicy;
import com.mahghuuuls.jawmsintegrations.network.IntegrationPresentationSnapshot;
import com.mahghuuuls.jawmsintegrations.proxy.ClientProxy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientIntegrationPresentationCacheTest {

    @AfterEach
    void clearCache() {
        ClientIntegrationPresentationCache.clear();
    }

    @Test
    void cacheClaimsNothingBeforeSnapshotAndClearsAtConnectionEnd() {
        assertNull(ClientIntegrationPresentationCache.ancientEntry(
                AncientReplacement.LESSER_MANA_RING.getRegistryName()));
        assertFalse(ClientIntegrationPresentationCache.isAncientReplacementActive(
                AncientReplacement.LESSER_MANA_RING.getRegistryName()));

        ClientIntegrationPresentationCache.install(IntegrationPresentationSnapshot.from(true,
                new AncientReplacementPolicy(true,
                        IntegrationConfigSnapshot.defaults().getAncientSpellcraft())));
        assertTrue(ClientIntegrationPresentationCache.isAncientReplacementActive(
                AncientReplacement.LESSER_MANA_RING.getRegistryName()));

        ClientIntegrationPresentationCache.clear();
        assertNull(ClientIntegrationPresentationCache.ancientEntry(
                AncientReplacement.LESSER_MANA_RING.getRegistryName()));
    }

    @Test
    void serverSnapshotOverridesAClientLocalDisabledPolicyForInteractionGating() {
        AncientReplacementPolicy.install(AncientReplacementPolicy.disabled());
        ClientProxy proxy = new ClientProxy();
        assertFalse(proxy.isServerReplacementActive(
                AncientReplacement.EVERFULL_MANA_FLASK.getRegistryName()));

        ClientIntegrationPresentationCache.install(IntegrationPresentationSnapshot.from(true,
                new AncientReplacementPolicy(true,
                        IntegrationConfigSnapshot.defaults().getAncientSpellcraft())));

        assertFalse(AncientReplacementPolicy.active().isEverfullEnabled());
        assertTrue(proxy.isServerReplacementActive(
                AncientReplacement.EVERFULL_MANA_FLASK.getRegistryName()));
    }

    @Test
    void qualityToolsPresentationUsesOnlyTheAcceptedServerSnapshot() {
        assertFalse(ClientIntegrationPresentationCache.isQualityToolsActive());
        ClientIntegrationPresentationCache.install(IntegrationPresentationSnapshot.from(true,
                AncientReplacementPolicy.disabled()));
        assertTrue(ClientIntegrationPresentationCache.isQualityToolsActive());
        ClientIntegrationPresentationCache.install(IntegrationPresentationSnapshot.from(false,
                enabledPolicy()));
        assertFalse(ClientIntegrationPresentationCache.isQualityToolsActive());
    }

    private static AncientReplacementPolicy enabledPolicy() {
        return new AncientReplacementPolicy(true,
                IntegrationConfigSnapshot.defaults().getAncientSpellcraft());
    }
}
