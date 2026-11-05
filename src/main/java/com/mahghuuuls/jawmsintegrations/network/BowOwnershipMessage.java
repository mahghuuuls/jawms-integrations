package com.mahghuuuls.jawmsintegrations.network;

import com.mahghuuuls.jawmsintegrations.JawmsIntegrationsMod;
import com.mahghuuuls.jawmsintegrations.Tags;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.NetworkManager;
import net.minecraftforge.fml.common.network.FMLIndexedMessageToMessageCodec;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.internal.FMLProxyPacket;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;

import java.lang.ref.WeakReference;

/** Restart-scoped ownership policy, independent of the legacy presentation protocol. */
public final class BowOwnershipMessage implements IMessage {
    private static final int PROTOCOL_VERSION = 1;

    private boolean enabled;

    public BowOwnershipMessage() {
    }

    public BowOwnershipMessage(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        enabled = buffer.readableBytes() == 2
                && buffer.readUnsignedByte() == PROTOCOL_VERSION
                && buffer.readUnsignedByte() == 1;
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeByte(PROTOCOL_VERSION);
        buffer.writeBoolean(enabled);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public static final class Handler implements IMessageHandler<BowOwnershipMessage, IMessage> {
        @Override
        public IMessage onMessage(BowOwnershipMessage message, MessageContext context) {
            // MessageContext uses the embedded channel's shared NET_HANDLER. The codec
            // records the actual incoming packet on this thread before dispatch.
            ThreadLocal<WeakReference<FMLProxyPacket>> inbound = NetworkRegistry.INSTANCE
                    .getChannel(Tags.MOD_ID, Side.CLIENT)
                    .attr(FMLIndexedMessageToMessageCodec.INBOUNDPACKETTRACKER).get();
            WeakReference<FMLProxyPacket> reference = inbound == null ? null : inbound.get();
            FMLProxyPacket packet = reference == null ? null : reference.get();
            NetworkManager origin = packet == null ? null : packet.getOrigin();
            if (origin != null) {
                JawmsIntegrationsMod.PROXY.acceptBowOwnership(origin, message.isEnabled());
            }
            return null;
        }
    }
}
