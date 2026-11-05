package com.mahghuuuls.jawmsintegrations.network;

import com.mahghuuuls.jawmsintegrations.JawmsIntegrationsMod;
import com.mahghuuuls.jawmsintegrations.Tags;
import com.mahghuuuls.jawmsintegrations.client.ClientBowOwnership;
import com.mahghuuuls.jawmsintegrations.proxy.CommonProxy;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.EnumPacketDirection;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.handshake.NetworkDispatcher;
import net.minecraftforge.fml.common.network.internal.FMLProxyPacket;
import net.minecraftforge.fml.relauncher.Side;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BowOwnershipMessageTest {

    @Test
    void onlyReleasedPacketCapablePeersReceiveOwnership() {
        assertEquals("1.3.0", IntegrationNetwork.FIRST_BOW_OWNERSHIP_RELEASE);
        List<BowOwnershipMessage> sent = new ArrayList<>();
        for (String version : new String[] {null, "1.2.0", "1.3.0-dev", "99.0.0unknown"}) {
            IntegrationNetwork.sendBowOwnership(version, true, sent::add);
        }
        assertTrue(sent.isEmpty());
        for (String version : new String[] {"1.3.0", Tags.VERSION, "1.4.0+build.2", "2.0.0"}) {
            IntegrationNetwork.sendBowOwnership(version, true, sent::add);
        }
        IntegrationNetwork.sendBowOwnership("1.3.0", false, sent::add);
        assertEquals(5, sent.size());
        assertTrue(sent.get(0).isEnabled());
        assertFalse(sent.get(4).isEnabled());
    }

    @Test
    void productionDecoderAcceptsOnlyTheSupportedEnabledPayload() {
        ByteBuf buffer = Unpooled.buffer();
        try {
            for (boolean enabled : new boolean[] {true, false}) {
                new BowOwnershipMessage(enabled).toBytes(buffer.clear());
                assertEquals(2, buffer.readableBytes());
                assertEquals(1, buffer.getUnsignedByte(0));
                assertEquals(enabled ? 1 : 0, buffer.getUnsignedByte(1));
                assertEquals(enabled, decode(buffer));
            }
            assertFalse(decode(buffer.clear()));
            assertFalse(decode(buffer.clear().writeByte(1)));
            assertFalse(decode(buffer.clear().writeByte(2).writeByte(1)));
            assertFalse(decode(buffer.clear().writeByte(1).writeByte(2)));
            assertFalse(decode(buffer.clear().writeByte(1).writeByte(1).writeByte(0)));
        } finally {
            buffer.release();
        }
    }

    @Test
    void connectionPolicyCannotLeakThroughReconnectOrLatePackets() {
        EmbeddedChannel first = new EmbeddedChannel();
        EmbeddedChannel second = new EmbeddedChannel();
        EmbeddedChannel third = new EmbeddedChannel();
        ByteBuf buffer = Unpooled.buffer();
        AtomicReference<EmbeddedChannel> current = new AtomicReference<>(first);
        Queue<Runnable> clientThread = new ArrayDeque<>();
        try {
            assertFalse(ClientBowOwnership.isEnabled(null));
            assertFalse(ClientBowOwnership.isEnabled(first));
            ClientBowOwnership.receive(first, true, current::get, clientThread::add);
            assertFalse(ClientBowOwnership.isEnabled(first));
            clientThread.remove().run();
            assertTrue(ClientBowOwnership.isEnabled(first));
            ClientBowOwnership.receive(first, false, current::get, clientThread::add);
            current.set(second);
            assertFalse(ClientBowOwnership.isEnabled(second));
            clientThread.remove().run();
            assertTrue(ClientBowOwnership.isEnabled(first));
            assertFalse(ClientBowOwnership.isEnabled(second));
            ClientBowOwnership.receive(first, false, current::get, Runnable::run);
            assertTrue(ClientBowOwnership.isEnabled(first));
            first.close();
            assertFalse(ClientBowOwnership.isEnabled(first));
            current.set(third);
            ClientBowOwnership.receive(third, true, current::get, Runnable::run);
            assertTrue(ClientBowOwnership.isEnabled(third));
            ClientBowOwnership.receive(third, decode(buffer.writeByte(2).writeByte(1)),
                    current::get, Runnable::run);
            assertFalse(ClientBowOwnership.isEnabled(third));
        } finally {
            first.finishAndReleaseAll();
            second.finishAndReleaseAll();
            third.finishAndReleaseAll();
            buffer.release();
        }
    }

    @Test
    void forgeHandlerUsesTheInboundPacketsOrigin() {
        IntegrationNetwork.initialize();
        NetworkManager origin = new NetworkManager(EnumPacketDirection.CLIENTBOUND);
        EmbeddedChannel connection = new EmbeddedChannel(origin);
        AtomicReference<NetworkManager> received = new AtomicReference<>();
        CommonProxy originalProxy = JawmsIntegrationsMod.PROXY;
        JawmsIntegrationsMod.PROXY = new CommonProxy() {
            @Override
            public void acceptBowOwnership(NetworkManager manager, boolean enabled) {
                assertTrue(enabled);
                received.set(manager);
            }
        };
        try {
            PacketBuffer bytes = new PacketBuffer(Unpooled.buffer());
            bytes.writeByte(1);
            new BowOwnershipMessage(true).toBytes(bytes);
            FMLProxyPacket packet = new FMLProxyPacket(bytes, Tags.MOD_ID);
            packet.setDispatcher(new NetworkDispatcher(origin));
            packet.setTarget(Side.CLIENT);
            NetworkRegistry.INSTANCE.getChannel(Tags.MOD_ID, Side.CLIENT).writeInbound(packet);
            assertSame(origin, received.get());
        } finally {
            JawmsIntegrationsMod.PROXY = originalProxy;
            connection.finishAndReleaseAll();
        }
    }

    private static boolean decode(ByteBuf buffer) {
        BowOwnershipMessage message = new BowOwnershipMessage(true);
        message.fromBytes(buffer);
        return message.isEnabled();
    }
}
