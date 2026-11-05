package com.mahghuuuls.jawmsintegrations.network;

import com.mahghuuuls.jawmsintegrations.Tags;
import com.mahghuuuls.jawmsintegrations.integration.JawmsCompatibility;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.handshake.NetworkDispatcher;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

import java.util.function.Consumer;

public final class IntegrationNetwork {

    private static final SimpleNetworkWrapper CHANNEL =
            NetworkRegistry.INSTANCE.newSimpleChannel(Tags.MOD_ID);
    static final String FIRST_BOW_OWNERSHIP_RELEASE = "1.3.0";
    private static boolean initialized;

    private IntegrationNetwork() {
    }

    public static synchronized void initialize() {
        if (!initialized) {
            CHANNEL.registerMessage(PresentationSnapshotMessage.Handler.class,
                    PresentationSnapshotMessage.class, 0, Side.CLIENT);
            CHANNEL.registerMessage(BowOwnershipMessage.Handler.class,
                    BowOwnershipMessage.class, 1, Side.CLIENT);
            initialized = true;
        }
    }

    public static void send(EntityPlayerMP player, IntegrationPresentationSnapshot snapshot) {
        CHANNEL.sendTo(new PresentationSnapshotMessage(snapshot), player);
    }

    public static void sendBowOwnership(EntityPlayerMP player, boolean enabled) {
        NetworkDispatcher peer = NetworkDispatcher.get(player.connection.netManager);
        String version = peer == null ? null : peer.getModList().get(Tags.MOD_ID);
        sendBowOwnership(version, enabled, message -> CHANNEL.sendTo(message, player));
    }

    static void sendBowOwnership(String version, boolean enabled, Consumer<BowOwnershipMessage> send) {
        if (version != null && version.matches("[0-9]+(?:\\.[0-9]+){2,}(?:\\+[0-9A-Za-z.-]+)?")
                && JawmsCompatibility.meetsMinimum(version, FIRST_BOW_OWNERSHIP_RELEASE)) {
            send.accept(new BowOwnershipMessage(enabled));
        }
    }
}
