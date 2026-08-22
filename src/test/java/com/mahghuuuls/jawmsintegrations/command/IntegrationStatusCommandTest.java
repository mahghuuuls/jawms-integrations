package com.mahghuuuls.jawmsintegrations.command;

import com.mahghuuuls.jawmsintegrations.config.IntegrationConfigSnapshot;
import com.mahghuuuls.jawmsintegrations.diagnostic.IntegrationDiagnosticsService;
import com.mahghuuuls.jawmsintegrations.integration.IntegrationCoordinator;
import com.mahghuuuls.jawmsintegrations.integration.JawmsCompatibility;
import com.mahghuuuls.jawmsintegrations.integration.OptionalIntegrationEvidenceRegistry;
import net.minecraft.command.CommandResultStats;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntegrationStatusCommandTest {

    @Test
    void commandUsesForgeOperatorPermissionBoundary() {
        IntegrationConfigSnapshot config = IntegrationConfigSnapshot.defaults();
        IntegrationCoordinator coordinator = IntegrationCoordinator.initialize(
                config,
                modId -> null,
                integration -> OptionalIntegrationEvidenceRegistry.Evidence.absent()
        );
        IntegrationDiagnosticsService diagnostics = new IntegrationDiagnosticsService(
                JawmsCompatibility.verify("1.1.0", CompatibleApi.class),
                config,
                coordinator
        );
        IntegrationStatusCommand command = new IntegrationStatusCommand(diagnostics);

        assertEquals(2, command.getRequiredPermissionLevel());
        assertEquals("jawmsintegrations", command.getName());
        assertFalse(command.checkPermission(null, new PermissionSender(false)));
        assertTrue(command.checkPermission(null, new PermissionSender(true)));
    }

    public static final class CompatibleApi {
        public static final String CURRENT = "1.6";
    }

    private static final class PermissionSender implements ICommandSender {
        private final boolean permitted;

        private PermissionSender(boolean permitted) {
            this.permitted = permitted;
        }

        @Override
        public String getName() {
            return "test";
        }

        @Override
        public boolean canUseCommand(int permissionLevel, String commandName) {
            return permitted && permissionLevel == 2 && "jawmsintegrations".equals(commandName);
        }

        @Override
        public World getEntityWorld() {
            return null;
        }

        @Override
        public void setCommandStat(CommandResultStats.Type type, int amount) {
        }

        @Override
        public MinecraftServer getServer() {
            return null;
        }
    }
}
