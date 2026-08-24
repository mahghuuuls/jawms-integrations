package com.mahghuuuls.jawmsintegrations.integration.arsmagica;

import net.minecraft.entity.player.EntityPlayer;

import java.util.Collections;
import java.util.List;

/** Optional, read-only Ars state boundary used by the existing diagnostics command. */
public final class ArsPlayerStateInspectionService {

    public interface Reader {
        List<String> inspect(EntityPlayer player);
    }

    private static final Reader UNAVAILABLE = player -> Collections.singletonList(
            "Ars state: unavailable");

    private static volatile Reader installed = UNAVAILABLE;

    private ArsPlayerStateInspectionService() {
    }

    public static void install(Reader reader) {
        if (reader == null) {
            throw new IllegalArgumentException("Ars state reader is required");
        }
        installed = reader;
    }

    public static void installUnavailable() {
        installed = UNAVAILABLE;
    }

    public static List<String> inspect(EntityPlayer player) {
        if (player == null) {
            throw new IllegalArgumentException("Player is required for Ars state inspection");
        }
        return installed.inspect(player);
    }
}
