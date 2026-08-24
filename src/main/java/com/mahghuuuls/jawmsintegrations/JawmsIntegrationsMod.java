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
import com.mahghuuuls.jawmsintegrations.integration.EarlyModMetadataScanner;
import com.mahghuuuls.jawmsintegrations.integration.OptionalIntegrationEvidenceRegistry;
import com.mahghuuuls.jawmsintegrations.integration.qualitytools.QualityToolsIntegration;
import com.mahghuuuls.jawmsintegrations.integration.ancientspellcraft.AncientSpellcraftIntegration;
import com.mahghuuuls.jawmsintegrations.integration.ancientspellcraft.AncientReplacementPolicy;
import com.mahghuuuls.jawmsintegrations.integration.ancientspellcraft.EverfullManaService;
import com.mahghuuuls.jawmsintegrations.integration.ancientspellcraft.DagorimFlaskService;
import com.mahghuuuls.jawmsintegrations.integration.crafttweaker.CraftTweakerManaService;
import com.mahghuuuls.jawmsintegrations.integration.crafttweaker.CraftTweakerRegistrationBootstrap;
import com.mahghuuuls.jawmsintegrations.network.IntegrationPresentationSync;
import com.mahghuuuls.jawmsintegrations.network.IntegrationNetwork;
import com.mahghuuuls.jawmsintegrations.proxy.CommonProxy;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(
        modid = Tags.MOD_ID,
        name = Tags.MOD_NAME,
        version = Tags.VERSION,
        acceptedMinecraftVersions = "[1.12.2]",
        dependencies = "required-after:jawms@[1.1.0,);before:crafttweaker"
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
    private JawmsCompatibility.Status jawms;

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
        jawms = JawmsCompatibility.verifyInstalled(jawmsVersion);

        ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
        if (contextLoader == null) {
            contextLoader = JawmsIntegrationsMod.class.getClassLoader();
        }
        OptionalIntegrationEvidenceRegistry.record(
                IntegrationId.CRAFTTWEAKER,
                EarlyModMetadataScanner.scan(contextLoader, IntegrationId.CRAFTTWEAKER)
        );

        IntegrationNetwork.initialize();

        coordinator = IntegrationCoordinator.initialize(config);
        diagnostics = new IntegrationDiagnosticsService(jawms, config, coordinator);
        activateCraftTweaker(contextLoader);
        if (coordinator.getStatus(IntegrationId.QUALITY_TOOLS).getState() == IntegrationState.READY) {
            try {
                QualityToolsIntegration.activate(config.getQualityTools(), this::emitQualityToolsReloadSummary);
                coordinator = coordinator.withActive(IntegrationId.QUALITY_TOOLS,
                        "Quality Tools integration activated");
            } catch (RuntimeException exception) {
                coordinator = coordinator.withFailure(IntegrationId.QUALITY_TOOLS,
                        "Activation failed: " + exception.getMessage());
                LOGGER.error("Quality Tools integration activation failed", exception);
            }
        }
        IntegrationState qualityToolsState = coordinator.getStatus(
                IntegrationId.QUALITY_TOOLS).getState();
        if (qualityToolsState == IntegrationState.ACTIVE
                || qualityToolsState == IntegrationState.DISABLED) {
            PROXY.activateQualityToolsClient();
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
    public void init(FMLInitializationEvent event) {
        IntegrationState ancientState = coordinator.getStatus(
                IntegrationId.ANCIENT_SPELLCRAFT).getState();
        if (ancientState == IntegrationState.READY) {
            try {
                AncientSpellcraftIntegration.activate(config.getAncientSpellcraft());
                coordinator = coordinator.withActive(IntegrationId.ANCIENT_SPELLCRAFT,
                        "Ancient Spellcraft integration activated");
            } catch (RuntimeException exception) {
                AncientReplacementPolicy.install(AncientReplacementPolicy.disabled());
                EverfullManaService.install(EverfullManaService.disabled());
                DagorimFlaskService.install(DagorimFlaskService.disabled());
                coordinator = coordinator.withFailure(IntegrationId.ANCIENT_SPELLCRAFT,
                        "Activation failed: " + exception.getMessage());
                LOGGER.error("Ancient Spellcraft integration activation failed", exception);
                LOGGER.warn("Ancient Spellcraft integration FAILED: {}",
                        coordinator.getStatus(IntegrationId.ANCIENT_SPELLCRAFT).getDetail());
            }
        }
        ancientState = coordinator.getStatus(IntegrationId.ANCIENT_SPELLCRAFT).getState();
        if (ancientState == IntegrationState.ACTIVE || ancientState == IntegrationState.DISABLED) {
            PROXY.activateAncientSpellcraftClient();
        }
        FMLCommonHandler.instance().bus().register(new IntegrationPresentationSync(
                coordinator.getStatus(IntegrationId.QUALITY_TOOLS).getState()
                        == IntegrationState.ACTIVE));
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

    private void activateCraftTweaker(ClassLoader loader) {
        IntegrationState state = coordinator.getStatus(IntegrationId.CRAFTTWEAKER).getState();
        if (state != IntegrationState.READY && state != IntegrationState.DISABLED) {
            CraftTweakerManaService.installInactive(
                    coordinator.getStatus(IntegrationId.CRAFTTWEAKER).getDetail());
            return;
        }
        try {
            CraftTweakerRegistrationBootstrap.register(loader);
            if (state == IntegrationState.READY) {
                CraftTweakerManaService.installActive();
                coordinator = coordinator.withActive(IntegrationId.CRAFTTWEAKER,
                        "CraftTweaker mana API registered and activated");
            } else {
                CraftTweakerManaService.installInactive("Disabled by configuration");
            }
        } catch (RuntimeException | LinkageError exception) {
            CraftTweakerManaService.installInactive("Registration failed: " + exception.getMessage());
            coordinator = coordinator.withFailure(IntegrationId.CRAFTTWEAKER,
                    "Registration failed: " + exception.getMessage());
            LOGGER.error("CraftTweaker integration registration failed", exception);
        }
    }

}
