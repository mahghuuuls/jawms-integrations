package com.mahghuuuls.jawmsintegrations.mixin.qualitytools;

import com.mahghuuuls.jawmsintegrations.integration.qualitytools.QualityCandidateAugmenter;
import com.tmtravlr.qualitytools.config.QualityEntry;
import com.tmtravlr.qualitytools.config.QualityType;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Redirects only native candidate choice; NBT writing and reforge behavior remain Quality Tools-owned. */
@Mixin(targets = "com.tmtravlr.qualitytools.config.QualityType", remap = false)
public abstract class MixinQualityToolsQualityType {

    @Redirect(
            method = "generateQualityTag(Lnet/minecraft/item/ItemStack;Z)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/tmtravlr/qualitytools/config/QualityType;chooseQualityEntry(Z)"
                            + "Lcom/tmtravlr/qualitytools/config/QualityEntry;",
                    remap = false
            ),
            remap = false
    )
    private QualityEntry jawmsIntegrations$chooseCandidate(QualityType selectedType,
                                                            boolean reforging,
                                                            ItemStack stack,
                                                            boolean ignoredReforging) {
        return QualityCandidateAugmenter.choose(selectedType, stack, reforging);
    }
}
