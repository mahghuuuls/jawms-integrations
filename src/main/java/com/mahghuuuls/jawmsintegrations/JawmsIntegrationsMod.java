package com.mahghuuuls.jawmsintegrations;

import com.mahghuuuls.jawmsintegrations.command.IntegrationStatusCommand;
import com.mahghuuuls.jawmsintegrations.config.IntegrationConfigLoader;
import com.mahghuuuls.jawmsintegrations.config.IntegrationConfigSnapshot;
import com.mahghuuuls.jawmsintegrations.diagnostic.IntegrationDiagnosticsService;
import com.mahghuuuls.jawmsintegrations.integration.IntegrationCoordinator;
import com.mahghuuuls.jawmsintegrations.integration.IntegrationState;
import com.mahghuuuls.jawmsintegrations.integration.IntegrationStatusView;
import com.mahghuuuls.jawmsintegrations.integration.JawmsCompatibility;
import com.mahghuuuls.jawmsintegrations.integration.IntegrationId;
import com.mahghuuuls.jawmsintegrations.integration.qualitytools.QualityToolsIntegration;
import com.mahghuuuls.jawmsintegrations.proxy.CommonProxy;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(
        modid = Tags.MOD_ID,
        name = Tags.MOD_NAME,
        version = Tags.VERSION,
        acceptedMinecraftVersions = "[1.12.2]",
        dependencies = "required-after:jawms@[0.4.0]"
)
public final class JawmsIntegrationsMod {

    public static final String CONFIG_FILENAME = "jawmsintegrations.cfg";
    public static final Logger LOGGER = LogManager.getLogger(Tags.MOD_NAME);

    @SidedProxy(
            clientSide = "com.mahghuuuls.jawmsintegrations.proxy.ClientProxy",
            serverSide = "com.mahghuuuls.jawmsintegrations.proxy.CommonProxy"
    )
    public static CommonProxy PROXY;

    private IntegrationConfigSnapshot config;
    private IntegrationCoordinator coordinator;
    private IntegrationDiagnosticsService diagnostics;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        IntegrationConfigLoader.LoadResult loaded = IntegrationConfigLoader.load(
                event.getSuggestedConfigurationFile()
        );
        config = loaded.getSnapshot();
        for (String warning : loaded.getWarnings()) {
            LOGGER.warn(warning);
        }

        ModContainer jawmsContainer = Loader.instance().getIndexedModList().get("jawms");
        String jawmsVersion = jawmsContainer == null ? null : jawmsContainer.getVersion();
        JawmsCompatibility.Status jawms = JawmsCompatibility.verifyInstalled(jawmsVersion);

        coordinator = IntegrationCoordinator.initialize(config);
        diagnostics = new IntegrationDiagnosticsService(jawms, config, coordinator);
        if (coordinator.getStatus(IntegrationId.QUALITY_TOOLS).getState() == IntegrationState.ACTIVE) {
            try {
                QualityToolsIntegration.activate(config.getQualityTools(), this::emitQualityToolsReloadSummary);
                PROXY.activateQualityToolsClient();
            } catch (RuntimeException exception) {
                coordinator = coordinator.withFailure(IntegrationId.QUALITY_TOOLS,
                        "Activation failed: " + exception.getMessage());
                LOGGER.error("Quality Tools integration activation failed", exception);
            }
        }
        for (IntegrationStatusView status : coordinator.getStatuses()) {
            if (status.getState() == IntegrationState.UNSUPPORTED
                    || status.getState() == IntegrationState.FAILED) {
                LOGGER.warn("{} integration {}: {}", status.getIntegration().getDisplayName(),
                        status.getState(), status.getDetail());
            }
        }
        diagnostics = new IntegrationDiagnosticsService(jawms, config, coordinator);
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        if (config.getDiagnostics().isEnabled()) {
            LOGGER.info(diagnostics.startupSummary());
        }
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new IntegrationStatusCommand(diagnostics));
    }

    private void emitQualityToolsReloadSummary() {
        if (config.getDiagnostics().isEnabled()) {
            LOGGER.info(diagnostics.qualityToolsReloadSummary());
        }
    }
}
