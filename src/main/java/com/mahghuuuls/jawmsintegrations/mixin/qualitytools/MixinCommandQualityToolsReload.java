package com.mahghuuuls.jawmsintegrations.mixin.qualitytools;

import com.mahghuuuls.jawmsintegrations.integration.qualitytools.QualityToolsIntegration;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Publishes one integration-owned summary after Quality Tools reloads its files. */
@Mixin(targets = "com.tmtravlr.qualitytools.config.CommandQualityToolsReload", remap = false)
public abstract class MixinCommandQualityToolsReload {

    @Inject(
            method = "execute(Lnet/minecraft/server/MinecraftServer;"
                    + "Lnet/minecraft/command/ICommandSender;[Ljava/lang/String;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/tmtravlr/qualitytools/config/ConfigLoader;reloadConfigs()V",
                    shift = At.Shift.AFTER,
                    remap = false
            ),
            remap = true
    )
    private void jawmsIntegrations$afterReload(MinecraftServer server,
                                                ICommandSender sender,
                                                String[] arguments,
                                                CallbackInfo callbackInfo) throws CommandException {
        QualityToolsIntegration.afterConfigReload();
    }
}
