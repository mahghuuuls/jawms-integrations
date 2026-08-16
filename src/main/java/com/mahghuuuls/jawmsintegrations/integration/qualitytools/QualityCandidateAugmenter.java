package com.mahghuuuls.jawmsintegrations.integration.qualitytools;

import com.google.common.collect.HashMultimap;
import com.mahghuuuls.jawmsintegrations.config.IntegrationConfigSnapshot;
import com.tmtravlr.qualitytools.config.QualityEntry;
import com.tmtravlr.qualitytools.config.QualityType;
import electroblob.wizardry.item.ItemWizardArmour;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextFormatting;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Builds a temporary native Quality Tools candidate view without mutating loaded configuration. */
public final class QualityCandidateAugmenter {

    private static final String WIZARDRY_NAMESPACE = "ebwizardry";

    private static volatile IntegrationConfigSnapshot.QualityToolsConfig config;

    private QualityCandidateAugmenter() {
    }

    static void configure(IntegrationConfigSnapshot.QualityToolsConfig configured) {
        if (configured == null) {
            throw new IllegalArgumentException("Quality Tools configuration must not be null");
        }
        config = configured;
    }

    /** Called only from the exact-version optional Quality Tools Mixin. */
    public static QualityEntry choose(QualityType selectedType,
                                      ItemStack stack,
                                      boolean reforging) {
        if (selectedType == null) {
            throw new IllegalArgumentException("Selected Quality Tools type must not be null");
        }
        IntegrationConfigSnapshot.QualityToolsConfig configured = config;
        return choose(selectedType, configured, isEligible(stack), reforging);
    }

    static QualityEntry choose(QualityType selectedType,
                               IntegrationConfigSnapshot.QualityToolsConfig configured,
                               boolean eligible,
                               boolean reforging) {
        if (configured == null
                || !configured.areBuiltInQualitiesEnabled()
                || !eligible) {
            return selectedType.chooseQualityEntry(reforging);
        }

        QualityType temporary = new QualityType();
        temporary.qualities = augmented(selectedType.qualities, configured);
        return temporary.chooseQualityEntry(reforging);
    }

    static QualityEntry[] augmented(QualityEntry[] original,
                                    IntegrationConfigSnapshot.QualityToolsConfig configured) {
        List<QualityEntry> candidates = new ArrayList<>();
        Set<String> names = new HashSet<>();
        if (original != null) {
            for (QualityEntry entry : original) {
                if (entry != null) {
                    candidates.add(entry);
                    if (entry.name != null) {
                        names.add(normalize(entry.name));
                    }
                }
            }
        }

        for (IntegrationConfigSnapshot.BuiltInQuality quality
                : IntegrationConfigSnapshot.BuiltInQuality.values()) {
            IntegrationConfigSnapshot.BuiltInQualityConfig value =
                    configured.getBuiltInQuality(quality);
            if (value.isEnabled() && names.add(normalize(value.getDisplayName()))) {
                candidates.add(entry(quality, value));
            }
        }
        return candidates.toArray(new QualityEntry[0]);
    }

    static boolean isEligible(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        Item item = stack.getItem();
        return isEligible(item instanceof ItemWizardArmour, item.getRegistryName());
    }

    static boolean shouldAugment(IntegrationConfigSnapshot.QualityToolsConfig configured,
                                 boolean wizardArmour,
                                 ResourceLocation registryName) {
        return configured != null
                && configured.areBuiltInQualitiesEnabled()
                && isEligible(wizardArmour, registryName);
    }

    static boolean isEligible(boolean wizardArmour, ResourceLocation registryName) {
        return wizardArmour
                && registryName != null
                && WIZARDRY_NAMESPACE.equals(registryName.getNamespace());
    }

    private static QualityEntry entry(IntegrationConfigSnapshot.BuiltInQuality quality,
                                      IntegrationConfigSnapshot.BuiltInQualityConfig value) {
        QualityEntry entry = new QualityEntry();
        entry.name = value.getDisplayName();
        entry.color = TextFormatting.AQUA;
        entry.weight = value.getWeight();
        entry.attributeMap = HashMultimap.create();
        entry.attributeMap.put(quality.getAttributeName(), new AttributeModifier(
                "jawmsintegrations:" + quality.getConfigKey(), value.getAmount(), 0));
        return entry;
    }

    private static String normalize(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }
}
