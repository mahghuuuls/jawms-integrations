package com.mahghuuuls.jawmsintegrations;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
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

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        LOGGER.info("Initializing {}", Tags.MOD_NAME);
    }
}
