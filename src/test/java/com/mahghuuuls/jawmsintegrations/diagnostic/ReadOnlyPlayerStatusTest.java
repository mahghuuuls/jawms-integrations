package com.mahghuuuls.jawmsintegrations.diagnostic;

import com.mahghuuuls.jawms.internal.mana.ManaState;
import com.mahghuuuls.jawmsintegrations.config.IntegrationConfigSnapshot;
import com.mahghuuuls.jawmsintegrations.integration.IntegrationCoordinator;
import com.mahghuuuls.jawmsintegrations.integration.JawmsCompatibility;
import com.mahghuuuls.jawmsintegrations.integration.OptionalIntegrationEvidenceRegistry;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.capabilities.Capability;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Runs the production status through the released JAWMS facade with actual pending state. */
final class ReadOnlyPlayerStatusTest {
    @Test
    void inspectingPendingLoadedManaDoesNotAcceptOrClampIt() throws Exception {
        TestPlayer player = allocate(TestPlayer.class);
        player.world = allocate(TestWorld.class);
        player.pending = new ManaState();
        NBTTagCompound saved = new NBTTagCompound();
        saved.setInteger("currentMana", 110);
        player.pending.deserializeIntrinsicState(saved);
        assertThrows(IllegalStateException.class, player.pending::explainAcceptedProfile);
        IntegrationConfigSnapshot config = IntegrationConfigSnapshot.defaults();
        IntegrationCoordinator coordinator = IntegrationCoordinator.initialize(config, ignored -> null,
                ignored -> OptionalIntegrationEvidenceRegistry.Evidence.absent());
        IntegrationDiagnosticsService status = new IntegrationDiagnosticsService(
                JawmsCompatibility.verify("1.5.0", CompatibleApi.class), config, coordinator);

        List<String> first = status.playerStatus(player);
        List<String> second = status.playerStatus(player);

        assertEquals(first, second);
        assertTrue(first.stream().anyMatch(line -> line.contains("last accepted profile")
                && line.contains("unavailable")));
        assertThrows(IllegalStateException.class, player.pending::explainAcceptedProfile);
        assertEquals(110, player.pending.serializeIntrinsicState().getInteger("currentMana"));
        assertEquals(2, player.capabilityReads, "Only the two read-only explanation calls may read state");
    }

    public static final class CompatibleApi {
        public static final String CURRENT = "1.6";
    }

    private static final class TestWorld extends WorldServer {
        private TestWorld() { super(null, null, null, 0, null); }
        @Override public boolean isCallingFromMinecraftThread() { return true; }
        @Override public long getTotalWorldTime() { return 0; }
    }

    private static final class TestPlayer extends EntityPlayerMP {
        private ManaState pending;
        private int capabilityReads;
        private TestPlayer() { super(null, null, null, null); }
        @Override public String getName() { return "PendingPlayer"; }
        @Override public UUID getUniqueID() { return new UUID(0, 18); }
        @Override @SuppressWarnings("unchecked")
        public <T> T getCapability(Capability<T> capability, EnumFacing facing) {
            capabilityReads++;
            return (T) pending;
        }
    }

    private static <T> T allocate(Class<T> type) throws Exception {
        Class<?> unsafeType = Class.forName("sun.misc.Unsafe");
        Field field = unsafeType.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return type.cast(unsafeType.getMethod("allocateInstance", Class.class).invoke(field.get(null), type));
    }
}
