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
import net.minecraft.util.text.ITextComponent;
import org.junit.jupiter.api.Test;
import com.mahghuuuls.jawmsintegrations.integration.IntegrationId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

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
        IntegrationStatusCommand command = new IntegrationStatusCommand(() -> diagnostics);

        assertEquals(2, command.getRequiredPermissionLevel());
        assertEquals("jawmsintegrations", command.getName());
        assertFalse(command.checkPermission(null, new PermissionSender(false)));
        assertTrue(command.checkPermission(null, new PermissionSender(true)));
    }

    @Test
    void dispatcherRefusesNonOperatorsBeforeReadingAnyStatus() {
        IntegrationStatusCommand command = new IntegrationStatusCommand(() -> {
            throw new AssertionError("Refused caller reached diagnostic state");
        });
        net.minecraft.command.CommandHandler dispatcher = new net.minecraft.command.CommandHandler() {
            @Override protected MinecraftServer getServer() { return null; }
        };
        dispatcher.registerCommand(command);
        PermissionSender sender = new PermissionSender(false);
        assertEquals(0, dispatcher.executeCommand(sender, "jawmsintegrations status"));
        assertTrue(sender.lines.stream().anyMatch(line -> line.contains("permission")));
    }

    @Test
    void statusReadsTheCurrentCoordinatorAfterARuntimeModuleFailure() throws Exception {
        IntegrationConfigSnapshot config = IntegrationConfigSnapshot.defaults();
        IntegrationCoordinator coordinator = IntegrationCoordinator.initialize(config,
                modId -> "1.0.0", integration -> OptionalIntegrationEvidenceRegistry.Evidence.supported("1.0.0"));
        AtomicReference<IntegrationDiagnosticsService> current = new AtomicReference<>(
                new IntegrationDiagnosticsService(JawmsCompatibility.verify("1.1.0", CompatibleApi.class),
                        config, coordinator));
        IntegrationStatusCommand command = new IntegrationStatusCommand(current::get);
        PermissionSender sender = new PermissionSender(true);
        command.execute(null, sender, new String[]{"status"});
        assertTrue(sender.lines.stream().anyMatch(line -> line.contains("Just Another Food System: READY")));
        current.set(new IntegrationDiagnosticsService(JawmsCompatibility.verify("1.1.0", CompatibleApi.class),
                config, coordinator.withFailure(IntegrationId.JAFS, "runtime linkage failure")));
        sender.lines.clear();
        command.execute(null, sender, new String[]{"status"});
        assertTrue(sender.lines.stream().anyMatch(line -> line.contains("Just Another Food System: FAILED")
                && line.contains("runtime linkage failure")));
    }

    public static final class CompatibleApi {
        public static final String CURRENT = "1.6";
    }

    private static final class PermissionSender implements ICommandSender {
        private final boolean permitted;
        private final List<String> lines = new ArrayList<>();

        @Override
        public void sendMessage(ITextComponent message) {
            lines.add(message.getUnformattedText());
        }

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
