package com.mahghuuuls.jawmsintegrations.network;

import com.mahghuuuls.jawmsintegrations.config.IntegrationConfigSnapshot;
import com.mahghuuuls.jawmsintegrations.integration.ancientspellcraft.AncientReplacement;
import com.mahghuuuls.jawmsintegrations.integration.ancientspellcraft.AncientReplacementPolicy;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PresentationSnapshotMessageTest {

    @Test
    void protocolVersionIncludesQualityToolsAuthorityFact() {
        assertEquals(5, IntegrationPresentationSnapshot.PROTOCOL_VERSION);
    }

    @Test
    void roundTripPreservesAuthoritativeFlagsAndValues() {
        IntegrationConfigSnapshot.AncientSpellcraftConfig config =
                new IntegrationConfigSnapshot.AncientSpellcraftConfig(
                        true,
                        new IntegrationConfigSnapshot.ToggleIntConfig(false, 9),
                        new IntegrationConfigSnapshot.ToggleIntConfig(true, 13),
                        new IntegrationConfigSnapshot.ToggleDoubleConfig(true, 19.5D),
                        new IntegrationConfigSnapshot.ToggleDoubleConfig(true, 27.5D));
        IntegrationPresentationSnapshot original = IntegrationPresentationSnapshot.from(true,
                new AncientReplacementPolicy(true, config));
        PresentationSnapshotMessage outgoing = new PresentationSnapshotMessage(original);
        ByteBuf buffer = Unpooled.buffer();
        try {
            outgoing.toBytes(buffer);
            PresentationSnapshotMessage incoming = new PresentationSnapshotMessage();
            incoming.fromBytes(buffer);

            assertTrue(incoming.isValid());
            assertTrue(incoming.getSnapshot().isQualityToolsActive());
            assertFalse(incoming.getSnapshot().get(
                    AncientReplacement.LESSER_MANA_RING).isEnabled());
            assertEquals(9.0D, incoming.getSnapshot().get(
                    AncientReplacement.LESSER_MANA_RING).getValue());
            assertEquals(13.0D, incoming.getSnapshot().get(
                    AncientReplacement.GREATER_MANA_RING).getValue());
            assertEquals(19.5D, incoming.getSnapshot().get(
                    AncientReplacement.MAJESTIC_MANA_CHARM).getValue());
            assertEquals(27.5D, incoming.getSnapshot().get(
                    AncientReplacement.CRYSTAL_RING).getValue());
            assertTrue(incoming.getSnapshot().get(
                    AncientReplacement.EVERFULL_MANA_FLASK).isEnabled());
            assertEquals(100.0D, incoming.getSnapshot().get(
                    AncientReplacement.EVERFULL_MANA_FLASK).getValue());
            IntegrationPresentationSnapshot.Entry dagorim = incoming.getSnapshot().get(
                    AncientReplacement.RING_OF_DAGORIM);
            assertTrue(dagorim.isEnabled());
            assertEquals(5.0D, dagorim.getValue());
            assertEquals(20.0D, dagorim.getSecondaryValue());
            assertEquals(20.0D, dagorim.getTertiaryValue());
        } finally {
            buffer.release();
        }
    }

    @Test
    void rejectsUnknownVersionTrailingBytesAndTruncatedPayload() {
        assertInvalid(buffer -> buffer.writeInt(
                IntegrationPresentationSnapshot.PROTOCOL_VERSION + 1));
        assertInvalid(buffer -> {
            new PresentationSnapshotMessage(defaultSnapshot()).toBytes(buffer);
            buffer.writeByte(1);
        });
        assertInvalid(buffer -> buffer.writeInt(
                IntegrationPresentationSnapshot.PROTOCOL_VERSION));
    }

    @Test
    void snapshotRejectsFractionalFlatManaBeforeClientCouldTruncateIt() {
        EnumMap<AncientReplacement, IntegrationPresentationSnapshot.Entry> entries =
                defaultEntries();
        entries.put(AncientReplacement.LESSER_MANA_RING,
                new IntegrationPresentationSnapshot.Entry(true, 8.5D));

        assertThrows(IllegalArgumentException.class,
                () -> new IntegrationPresentationSnapshot(true, entries));
    }

    private static void assertInvalid(BufferWriter writer) {
        ByteBuf buffer = Unpooled.buffer();
        try {
            writer.write(buffer);
            PresentationSnapshotMessage message = new PresentationSnapshotMessage();
            message.fromBytes(buffer);
            assertFalse(message.isValid());
        } finally {
            buffer.release();
        }
    }

    private static IntegrationPresentationSnapshot defaultSnapshot() {
        return new IntegrationPresentationSnapshot(false, defaultEntries());
    }

    private static EnumMap<AncientReplacement, IntegrationPresentationSnapshot.Entry>
    defaultEntries() {
        EnumMap<AncientReplacement, IntegrationPresentationSnapshot.Entry> entries =
                new EnumMap<>(AncientReplacement.class);
        entries.put(AncientReplacement.LESSER_MANA_RING,
                new IntegrationPresentationSnapshot.Entry(true, 8.0D));
        entries.put(AncientReplacement.GREATER_MANA_RING,
                new IntegrationPresentationSnapshot.Entry(true, 12.0D));
        entries.put(AncientReplacement.MAJESTIC_MANA_CHARM,
                new IntegrationPresentationSnapshot.Entry(true, 18.0D));
        entries.put(AncientReplacement.CRYSTAL_RING,
                new IntegrationPresentationSnapshot.Entry(true, 25.0D));
        entries.put(AncientReplacement.EVERFULL_MANA_FLASK,
                new IntegrationPresentationSnapshot.Entry(true, 100.0D));
        entries.put(AncientReplacement.RING_OF_DAGORIM,
                new IntegrationPresentationSnapshot.Entry(true, 5.0D, 20.0D, 20.0D));
        return entries;
    }

    private interface BufferWriter {
        void write(ByteBuf buffer);
    }
}
