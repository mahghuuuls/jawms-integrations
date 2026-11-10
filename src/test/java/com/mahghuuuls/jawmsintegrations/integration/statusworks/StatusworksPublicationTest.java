package com.mahghuuuls.jawmsintegrations.integration.statusworks;

import com.mahghuuuls.jawms.api.ManaContribution;
import com.mahghuuuls.jawms.api.ManaContributionPublicationResult;
import com.mahghuuuls.jawms.api.ManaContributionPublicationStatus;
import com.mahghuuuls.jawms.api.ManaProfileGeneration;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.util.OptionalLong;

import static com.mahghuuuls.jawms.api.ManaContributionPublicationStatus.BUSY;
import static com.mahghuuuls.jawms.api.ManaContributionPublicationStatus.INITIAL_RECORDED;
import static com.mahghuuuls.jawms.api.ManaContributionPublicationStatus.REVISION_MISMATCH;
import static com.mahghuuuls.jawms.api.ManaContributionPublicationStatus.SUSPENDED;
import static com.mahghuuuls.jawms.api.ManaContributionPublicationStatus.UPDATE_RECORDED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatusworksPublicationTest {
    @Test
    void verifierDeclinesFailedPendingAndChangedGroupsEvenWithoutSuspension() throws Exception {
        StatusworksPublication publication = new StatusworksPublication();
        ManaProfileGeneration generation = generation();
        publication.bind(generation);
        ManaContribution negative = ManaContribution.builder().flatMaximumMana(-20).build();
        assertTrue(publication.acknowledge(receipt(INITIAL_RECORDED, 1), INITIAL_RECORDED, negative));
        assertFalse(publication.verifies(generation, 1, negative,
                () -> StatusworksProjection.Read.unverified("FAILED")));
        assertFalse(publication.verifies(generation, 1, negative,
                () -> StatusworksProjection.Read.unverified("PENDING")));
        assertFalse(publication.verifies(generation, 1, negative, StatusworksProjectionTest::empty));
        assertTrue(publication.acknowledge(receipt(SUSPENDED, 2), SUSPENDED, null));
        assertFalse(publication.verifies(generation, 2, ManaContribution.EMPTY, StatusworksPublicationTest::unexpectedRead));
        assertTrue(publication.acknowledge(receipt(UPDATE_RECORDED, 3), UPDATE_RECORDED, ManaContribution.EMPTY));
        assertTrue(publication.verifies(generation, 3, ManaContribution.EMPTY, StatusworksProjectionTest::empty));
        ManaContribution differentDefault = ManaContribution.builder().regenerateDuringPostCastLockout(true).build();
        assertFalse(publication.verifies(generation, 3, differentDefault, StatusworksPublicationTest::unexpectedRead));
    }

    @Test
    void distinguishesRecordedEmptyTransientRejectionAndUnknownRevision() throws Exception {
        StatusworksPublication publication = new StatusworksPublication();
        ManaProfileGeneration generation = generation();
        publication.bind(generation);
        assertTrue(publication.acknowledge(receipt(INITIAL_RECORDED, 1), INITIAL_RECORDED, ManaContribution.EMPTY));
        assertFalse(publication.acknowledge(rejected(BUSY), SUSPENDED, null));
        assertFalse(publication.uncertain);
        assertEquals(1, publication.revision);
        assertFalse(publication.acknowledge(rejected(REVISION_MISMATCH), SUSPENDED, null));
        assertTrue(publication.uncertain);
        assertFalse(publication.verifies(generation, 1, ManaContribution.EMPTY, StatusworksPublicationTest::unexpectedRead));
        publication.bind(generation);
        assertTrue(publication.uncertain);
        publication.bind(generation());
        assertFalse(publication.uncertain);
        assertEquals(0, publication.revision);
        assertNull(publication.candidate);
        assertFalse(publication.verifies(generation, 1, ManaContribution.EMPTY, StatusworksPublicationTest::unexpectedRead));
    }

    @Test
    void acknowledgesTheProviderRevisionAndRejectsAReusedUpdateRevision() throws Exception {
        StatusworksPublication publication = new StatusworksPublication();
        publication.bind(generation());
        assertTrue(publication.acknowledge(receipt(INITIAL_RECORDED, 3), INITIAL_RECORDED, ManaContribution.EMPTY));
        assertFalse(publication.acknowledge(receipt(UPDATE_RECORDED, 3), UPDATE_RECORDED,
                ManaContribution.builder().flatMaximumMana(20).build()));
        assertTrue(publication.uncertain);
        assertEquals(3, publication.revision);
        assertTrue(publication.candidate.isEmpty());
    }

    private static StatusworksProjection.Read unexpectedRead() {
        throw new AssertionError("Verifier read for an unconfirmed candidate");
    }

    private static ManaContributionPublicationResult receipt(ManaContributionPublicationStatus status, long revision) {
        return new ManaContributionPublicationResult(status, OptionalLong.of(revision));
    }

    private static ManaContributionPublicationResult rejected(ManaContributionPublicationStatus status) {
        return new ManaContributionPublicationResult(status, OptionalLong.empty());
    }

    private static ManaProfileGeneration generation() throws Exception {
        Constructor<ManaProfileGeneration> constructor = ManaProfileGeneration.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }
}
