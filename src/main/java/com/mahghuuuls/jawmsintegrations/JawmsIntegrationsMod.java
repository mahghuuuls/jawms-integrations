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
import com.mahghuuuls.jawmsintegrations.integration.jafs.JafsRegistrationBootstrap;
import com.mahghuuuls.jawmsintegrations.integration.jafs.FoodSourceAccess;
import com.mahghuuuls.jawmsintegrations.integration.jass.SpellStaminaAccess;
import com.mahghuuuls.jawmsintegrations.integration.jass.SpellStaminaBootstrap;
import com.mahghuuuls.jawmsintegrations.integration.EarlyModMetadataScanner;
import com.mahghuuuls.jawmsintegrations.integration.OptionalIntegrationEvidenceRegistry;
import com.mahghuuuls.jawmsintegrations.integration.qualitytools.QualityToolsIntegration;
import com.mahghuuuls.jawmsintegrations.integration.setworks.SetworksBootstrap;
import com.mahghuuuls.jawmsintegrations.integration.setworks.SetworksSourceAccess;
import com.mahghuuuls.jawmsintegrations.integration.statusworks.StatusworksBootstrap;
import com.mahghuuuls.jawmsintegrations.integration.statusworks.StatusworksSourceAccess;
import com.mahghuuuls.jawmsintegrations.integration.ancientspellcraft.AncientSpellcraftIntegration;
import com.mahghuuuls.jawmsintegrations.integration.ancientspellcraft.AncientReplacementPolicy;
import com.mahghuuuls.jawmsintegrations.integration.ancientspellcraft.EverfullManaService;
import com.mahghuuuls.jawmsintegrations.integration.ancientspellcraft.DagorimFlaskService;
import com.mahghuuuls.jawmsintegrations.integration.crafttweaker.CraftTweakerManaService;
import com.mahghuuuls.jawmsintegrations.integration.crafttweaker.CraftTweakerRegistrationBootstrap;
import com.mahghuuuls.jawmsintegrations.network.IntegrationPresentationSync;
import com.mahghuuuls.jawmsintegrations.network.IntegrationNetwork;
import com.mahghuuuls.jawmsintegrations.proxy.CommonProxy;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppedEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(
        modid = Tags.MOD_ID,
        name = Tags.MOD_NAME,
        version = Tags.VERSION,
        acceptedMinecraftVersions = "[1.12.2]",
        dependencies = "required:jawms@[" + JawmsCompatibility.REQUIRED_MOD_VERSION + ",)"
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
    private volatile IntegrationCoordinator coordinator;
    private IntegrationDiagnosticsService diagnostics;
    private JawmsCompatibility.Status jawms;
    private FoodSourceAccess foodSource;
    private SpellStaminaAccess spellStamina;
    private SetworksSourceAccess setworks;
    private StatusworksSourceAccess statusworks;

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
        OptionalIntegrationEvidenceRegistry.record(
                IntegrationId.JAFS,
                EarlyModMetadataScanner.scan(contextLoader, IntegrationId.JAFS)
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
        try {
            ClassLoader loader = Thread.currentThread().getContextClassLoader();
            JafsRegistrationBootstrap.Result initialized = JafsRegistrationBootstrap.initialize(coordinator,
                    loader == null ? getClass().getClassLoader() : loader,
                    detail -> {
                        coordinator = coordinator.withFailure(IntegrationId.JAFS, detail);
                        diagnostics = new IntegrationDiagnosticsService(jawms, config, coordinator, foodSource,
                                spellStamina, setworks, statusworks);
                        LOGGER.warn("JAFS integration FAILED: {}", detail);
                    });
            foodSource = initialized.getFoodSource();
            coordinator = initialized.getCoordinator();
        } catch (RuntimeException | LinkageError exception) {
            coordinator = coordinator.withFailure(IntegrationId.JAFS,
                    "Initialization failed: " + exception.getClass().getSimpleName()
                            + ": " + exception.getMessage());
            LOGGER.error("JAFS integration initialization failed; no food bonus enabled", exception);
        }
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
        SpellStaminaBootstrap.Result initialized = SpellStaminaBootstrap.initialize(
                coordinator, config, jawms, this::onSpellStaminaContractLoss);
        coordinator = initialized.getCoordinator();
        spellStamina = initialized.getAccess();
        SetworksBootstrap.Result setworksResult = SetworksBootstrap.initialize(coordinator, jawms);
        coordinator = setworksResult.getCoordinator();
        setworks = setworksResult.getSource();
        StatusworksBootstrap.Result statusworksResult = StatusworksBootstrap.initialize(
                coordinator, jawms, config.getDiagnostics().isEnabled());
        coordinator = statusworksResult.coordinator;
        statusworks = statusworksResult.source;
        FMLCommonHandler.instance().bus().register(new IntegrationPresentationSync(
                coordinator.getStatus(IntegrationId.QUALITY_TOOLS).getState() == IntegrationState.ACTIVE,
                () -> coordinator.getStatus(IntegrationId.SPELL_STAMINA).getState() == IntegrationState.ACTIVE));
        diagnostics = new IntegrationDiagnosticsService(jawms, config, coordinator, foodSource, spellStamina, setworks,
                statusworks);
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        if (config.getDiagnostics().isEnabled()) {
            LOGGER.info(diagnostics.startupSummary());
        }
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new IntegrationStatusCommand(() -> diagnostics));
    }

    @Mod.EventHandler
    public void serverStopped(FMLServerStoppedEvent event) {
        if (foodSource != null) foodSource.clearWarnings();
        if (spellStamina != null) spellStamina.serverStopped();
        if (setworks != null) setworks.serverStopped();
        if (statusworks != null) statusworks.serverStopped();
    }

    private void onSpellStaminaContractLoss(LinkageError cause) {
        String detail = "Spell stamina provider contract lost: " + cause;
        LOGGER.warn(detail, cause);
        coordinator = coordinator.withFailure(IntegrationId.SPELL_STAMINA, detail);
        diagnostics = new IntegrationDiagnosticsService(jawms, config, coordinator, foodSource, spellStamina, setworks,
                statusworks);
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server != null) {
            for (EntityPlayerMP player : server.getPlayerList().getPlayers()) {
                IntegrationNetwork.sendBowOwnership(player, false);
            }
        }
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
