package com.mahghuuuls.jawmsintegrations.network;

import com.mahghuuuls.jawmsintegrations.Tags;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

public final class IntegrationNetwork {

    private static final SimpleNetworkWrapper CHANNEL =
            NetworkRegistry.INSTANCE.newSimpleChannel(Tags.MOD_ID);
    private static boolean initialized;

    private IntegrationNetwork() {
    }

    public static synchronized void initialize() {
        if (!initialized) {
            CHANNEL.registerMessage(PresentationSnapshotMessage.Handler.class,
                    PresentationSnapshotMessage.class, 0, Side.CLIENT);
            initialized = true;
        }
    }

    public static void send(EntityPlayerMP player, IntegrationPresentationSnapshot snapshot) {
        CHANNEL.sendTo(new PresentationSnapshotMessage(snapshot), player);
    }
}
