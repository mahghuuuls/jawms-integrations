package com.mahghuuuls.jawmsintegrations.client;

import com.mahghuuuls.jawmsintegrations.Tags;
import io.netty.channel.Channel;
import io.netty.util.AttributeKey;

import java.util.function.Consumer;
import java.util.function.Supplier;

/** Each Netty channel is a new connection generation with no inherited policy. */
public final class ClientBowOwnership {
    private static final AttributeKey<Boolean> ENABLED =
            AttributeKey.valueOf(Tags.MOD_ID + ".bowOwnership");

    private ClientBowOwnership() {
    }

    public static void receive(Channel origin, boolean enabled, Supplier<Channel> current,
                               Consumer<Runnable> schedule) {
        if (origin == null) return;
        schedule.accept(() -> {
            if (origin == current.get() && origin.isOpen()) {
                origin.attr(ENABLED).set(enabled);
            }
        });
    }

    public static boolean isEnabled(Channel connection) {
        return connection != null && connection.isOpen()
                && Boolean.TRUE.equals(connection.attr(ENABLED).get());
    }
}
