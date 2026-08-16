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

class ClientAncientPresentationCacheTest {

    @AfterEach
    void clearCache() {
        ClientAncientPresentationCache.clear();
    }

    @Test
    void cacheClaimsNothingBeforeSnapshotAndClearsAtConnectionEnd() {
        assertNull(ClientAncientPresentationCache.entry(
                AncientReplacement.LESSER_MANA_RING.getRegistryName()));
        assertFalse(ClientAncientPresentationCache.isActive(
                AncientReplacement.LESSER_MANA_RING.getRegistryName()));

        ClientAncientPresentationCache.install(IntegrationPresentationSnapshot.from(
                new AncientReplacementPolicy(true,
                        IntegrationConfigSnapshot.defaults().getAncientSpellcraft())));
        assertTrue(ClientAncientPresentationCache.isActive(
                AncientReplacement.LESSER_MANA_RING.getRegistryName()));

        ClientAncientPresentationCache.clear();
        assertNull(ClientAncientPresentationCache.entry(
                AncientReplacement.LESSER_MANA_RING.getRegistryName()));
    }

    @Test
    void serverSnapshotOverridesAClientLocalDisabledPolicyForInteractionGating() {
        AncientReplacementPolicy.install(AncientReplacementPolicy.disabled());
        ClientProxy proxy = new ClientProxy();
        assertFalse(proxy.isServerReplacementActive(
                AncientReplacement.EVERFULL_MANA_FLASK.getRegistryName()));

        ClientAncientPresentationCache.install(IntegrationPresentationSnapshot.from(
                new AncientReplacementPolicy(true,
                        IntegrationConfigSnapshot.defaults().getAncientSpellcraft())));

        assertFalse(AncientReplacementPolicy.active().isEverfullEnabled());
        assertTrue(proxy.isServerReplacementActive(
                AncientReplacement.EVERFULL_MANA_FLASK.getRegistryName()));
    }
}
