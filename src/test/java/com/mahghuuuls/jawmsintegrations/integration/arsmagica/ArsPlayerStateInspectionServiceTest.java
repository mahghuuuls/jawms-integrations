package com.mahghuuuls.jawmsintegrations.integration.arsmagica;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ArsPlayerStateInspectionServiceTest {

    @AfterEach
    void reset() {
        ArsPlayerStateInspectionService.installUnavailable();
    }

    @Test
    void unavailableReaderIsSafeAndExplicit() throws Exception {
        ArsPlayerStateInspectionService.installUnavailable();

        assertEquals(Arrays.asList("Ars state: unavailable"),
                ArsPlayerStateInspectionService.inspect(uninitializedServerPlayer()));
    }

    @Test
    void installedReaderOwnsOnlyReadOnlyFormattingBoundary() throws Exception {
        EntityPlayer player = uninitializedServerPlayer();
        ArsPlayerStateInspectionService.install(value -> {
            assertEquals(player, value);
            return Arrays.asList("state", "affinity");
        });

        assertEquals(Arrays.asList("state", "affinity"),
                ArsPlayerStateInspectionService.inspect(player));
        assertThrows(IllegalArgumentException.class,
                () -> ArsPlayerStateInspectionService.install(null));
        assertThrows(IllegalArgumentException.class,
                () -> ArsPlayerStateInspectionService.inspect(null));
    }

    private static EntityPlayer uninitializedServerPlayer() throws Exception {
        Class<?> unsafeType = Class.forName("sun.misc.Unsafe");
        java.lang.reflect.Field field = unsafeType.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        Object unsafe = field.get(null);
        return (EntityPlayer) unsafeType.getMethod("allocateInstance", Class.class)
                .invoke(unsafe, EntityPlayerMP.class);
    }
}
