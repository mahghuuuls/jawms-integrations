package com.mahghuuuls.jawmsintegrations.network;

import com.mahghuuuls.jawmsintegrations.JawmsIntegrationsMod;
import com.mahghuuuls.jawmsintegrations.integration.ancientspellcraft.AncientReplacement;
import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.EnumMap;
import java.util.concurrent.atomic.AtomicBoolean;

public final class PresentationSnapshotMessage implements IMessage {

    private static final AtomicBoolean MALFORMED_WARNING_EMITTED = new AtomicBoolean();
    private IntegrationPresentationSnapshot snapshot;
    private boolean valid;

    public PresentationSnapshotMessage() {
    }

    public PresentationSnapshotMessage(IntegrationPresentationSnapshot snapshot) {
        if (snapshot == null) {
            throw new NullPointerException("snapshot");
        }
        this.snapshot = snapshot;
        this.valid = true;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        valid = false;
        try {
            if (buffer.readInt() != IntegrationPresentationSnapshot.PROTOCOL_VERSION) {
                return;
            }
            EnumMap<AncientReplacement, IntegrationPresentationSnapshot.Entry> entries =
                    new EnumMap<>(AncientReplacement.class);
            for (AncientReplacement replacement
                    : IntegrationPresentationSnapshot.staticReplacements()) {
                boolean enabled = buffer.readBoolean();
                double value = buffer.readDouble();
                double secondary = buffer.readDouble();
                double tertiary = buffer.readDouble();
                entries.put(replacement, new IntegrationPresentationSnapshot.Entry(
                        enabled, value, secondary, tertiary));
            }
            if (buffer.isReadable()) {
                return;
            }
            snapshot = new IntegrationPresentationSnapshot(entries);
            valid = true;
        } catch (RuntimeException ignored) {
            snapshot = null;
        }
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        if (!valid || snapshot == null) {
            throw new IllegalStateException("Cannot encode an invalid presentation snapshot");
        }
        buffer.writeInt(IntegrationPresentationSnapshot.PROTOCOL_VERSION);
        for (AncientReplacement replacement : IntegrationPresentationSnapshot.staticReplacements()) {
            IntegrationPresentationSnapshot.Entry entry = snapshot.get(replacement);
            buffer.writeBoolean(entry.isEnabled());
            buffer.writeDouble(entry.getValue());
            buffer.writeDouble(entry.getSecondaryValue());
            buffer.writeDouble(entry.getTertiaryValue());
        }
    }

    public boolean isValid() {
        return valid;
    }

    public IntegrationPresentationSnapshot getSnapshot() {
        return snapshot;
    }

    public static final class Handler
            implements IMessageHandler<PresentationSnapshotMessage, IMessage> {
        @Override
        public IMessage onMessage(PresentationSnapshotMessage message, MessageContext context) {
            if (message != null && message.isValid() && message.getSnapshot() != null) {
                JawmsIntegrationsMod.PROXY.acceptPresentationSnapshot(message.getSnapshot());
            } else if (MALFORMED_WARNING_EMITTED.compareAndSet(false, true)) {
                JawmsIntegrationsMod.LOGGER.warn(
                        "Rejected malformed or unsupported integration presentation snapshot");
            }
            return null;
        }
    }
}
