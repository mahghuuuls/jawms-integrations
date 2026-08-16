package com.mahghuuuls.jawmsintegrations.integration.qualitytools;

import com.google.common.collect.HashMultimap;
import com.mahghuuuls.jawmsintegrations.config.IntegrationConfigSnapshot;
import com.tmtravlr.qualitytools.config.QualityEntry;
import com.tmtravlr.qualitytools.config.QualityType;
import electroblob.wizardry.constants.Element;
import electroblob.wizardry.item.ItemWizardArmour;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextFormatting;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QualityCandidateAugmenterTest {

    @Test
    void defaultPoolAppendsTwelveUniqueCandidatesWithoutMutatingNativeArray() {
        QualityEntry normal = nativeEntry("normal", 10);
        QualityEntry nativePositive = nativeEntry("Healthy", 5);
        QualityEntry[] original = {normal, nativePositive};

        QualityEntry[] augmented = QualityCandidateAugmenter.augmented(
                original, IntegrationConfigSnapshot.defaults().getQualityTools());

        assertEquals(2, original.length);
        assertSame(normal, original[0]);
        assertSame(nativePositive, original[1]);
        assertEquals(14, augmented.length);
        assertSame(normal, augmented[0]);
        assertSame(nativePositive, augmented[1]);

        Set<String> names = new HashSet<>();
        for (QualityEntry entry : augmented) {
            assertTrue(names.add(entry.name.toLowerCase()));
        }
        for (IntegrationConfigSnapshot.BuiltInQuality quality
                : IntegrationConfigSnapshot.BuiltInQuality.values()) {
            assertBuiltIn(augmented[2 + quality.ordinal()], quality);
        }
    }

    @Test
    void independentlyDisabledEntryIsOmittedAndConfiguredSiblingIsUsed() {
        EnumMap<IntegrationConfigSnapshot.BuiltInQuality,
                IntegrationConfigSnapshot.BuiltInQualityConfig> values = defaults();
        values.put(IntegrationConfigSnapshot.BuiltInQuality.FIRE_FOCUS,
                new IntegrationConfigSnapshot.BuiltInQualityConfig(false, "Fire Focus", 8.0D, 1));
        values.put(IntegrationConfigSnapshot.BuiltInQuality.EFFICIENT_CASTING,
                new IntegrationConfigSnapshot.BuiltInQualityConfig(true, "Economical", 7.5D, 3));
        IntegrationConfigSnapshot.QualityToolsConfig config =
                new IntegrationConfigSnapshot.QualityToolsConfig(true, true, values);

        QualityEntry[] augmented = QualityCandidateAugmenter.augmented(
                new QualityEntry[] {nativeEntry("normal", 10)}, config);

        assertEquals(12, augmented.length);
        assertFalse(contains(augmented, "Fire Focus"));
        QualityEntry economical = find(augmented, "Economical");
        assertEquals(3, economical.weight);
        AttributeModifier modifier = economical.attributeMap
                .get(IntegrationConfigSnapshot.BuiltInQuality.EFFICIENT_CASTING.getAttributeName())
                .iterator().next();
        assertEquals(7.5D, modifier.getAmount());
        assertEquals(0, modifier.getOperation());
    }

    @Test
    void configuredNameCollisionPreservesNativeEntryWithoutAddingDuplicate() {
        EnumMap<IntegrationConfigSnapshot.BuiltInQuality,
                IntegrationConfigSnapshot.BuiltInQualityConfig> values = defaults();
        values.put(IntegrationConfigSnapshot.BuiltInQuality.MANAWOVEN,
                new IntegrationConfigSnapshot.BuiltInQualityConfig(true, "Healthy", 5.0D, 5));

        QualityEntry nativeHealthy = nativeEntry("Healthy", 17);
        QualityEntry[] augmented = QualityCandidateAugmenter.augmented(
                new QualityEntry[] {nativeHealthy},
                new IntegrationConfigSnapshot.QualityToolsConfig(true, true, values));

        assertEquals(12, augmented.length);
        assertSame(nativeHealthy, find(augmented, "Healthy"));
        assertEquals(17, find(augmented, "Healthy").weight);
    }

    @Test
    void eligibilityRequiresWizardArmourClassAndElectroblobNamespace() {
        IntegrationConfigSnapshot.QualityToolsConfig enabled =
                IntegrationConfigSnapshot.defaults().getQualityTools();
        assertTrue(QualityCandidateAugmenter.shouldAugment(
                enabled, true, new ResourceLocation("ebwizardry", "wizard_hat")));
        assertTrue(QualityCandidateAugmenter.shouldAugment(
                enabled, true, new ResourceLocation("ebwizardry", "sage_robe_fire")));
        assertTrue(QualityCandidateAugmenter.shouldAugment(
                enabled, true, new ResourceLocation("ebwizardry", "battlemage_leggings_ice")));
        assertTrue(QualityCandidateAugmenter.shouldAugment(
                enabled, true, new ResourceLocation("ebwizardry", "warlock_boots_healing")));
        assertFalse(QualityCandidateAugmenter.shouldAugment(
                enabled, false, new ResourceLocation("ebwizardry", "spectral_helmet")));
        assertFalse(QualityCandidateAugmenter.shouldAugment(
                enabled, true, new ResourceLocation("ancientspellcraft", "addon_robes")));
        assertFalse(QualityCandidateAugmenter.shouldAugment(
                enabled, true, new ResourceLocation("minecraft", "diamond_helmet")));
        assertFalse(QualityCandidateAugmenter.shouldAugment(enabled, true, null));
        assertFalse(QualityCandidateAugmenter.shouldAugment(
                new IntegrationConfigSnapshot.QualityToolsConfig(true, false),
                true, new ResourceLocation("ebwizardry", "wizard_hat")));
        assertFalse(QualityCandidateAugmenter.shouldAugment(
                null, true, new ResourceLocation("ebwizardry", "wizard_hat")));
    }

    @Test
    void ineligibleSelectionDelegatesToSelectedNativeTypeAndForwardsReforgeFlag() {
        QualityEntry nativeEntry = nativeEntry("Native", 1);
        TrackingQualityType selectedType = new TrackingQualityType(nativeEntry);

        QualityEntry selected = QualityCandidateAugmenter.choose(
                selectedType, IntegrationConfigSnapshot.defaults().getQualityTools(),
                false, true);

        assertSame(nativeEntry, selected);
        assertEquals(1, selectedType.calls);
        assertTrue(selectedType.reforging);
    }

    @Test
    void eligibleSelectionUsesTemporaryNativeTypeAndForwardsReforgeFlag() {
        QualityType selectedType = new QualityType();
        selectedType.qualities = new QualityEntry[] {nativeEntry("normal", 100)};

        QualityEntry selected = QualityCandidateAugmenter.choose(
                selectedType, IntegrationConfigSnapshot.defaults().getQualityTools(),
                true, true);

        assertTrue(selected != null);
        assertFalse("normal".equalsIgnoreCase(selected.name));
        assertEquals(1, selectedType.qualities.length);
        assertEquals("normal", selectedType.qualities[0].name);
    }

    @Test
    void productionItemStackAdapterRequiresRealWizardArmourAndPreservesExistingNbt() {
        Bootstrap.register();
        ItemWizardArmour official = new ItemWizardArmour(
                ItemArmor.ArmorMaterial.LEATHER, 0, EntityEquipmentSlot.HEAD, Element.MAGIC);
        official.setRegistryName("ebwizardry", "wizard_hat_test");
        ItemStack stack = new ItemStack(official);
        NBTTagCompound quality = new NBTTagCompound();
        quality.setString("Name", "Existing Quality");
        stack.setTagInfo("Quality", quality);
        NBTTagCompound before = stack.serializeNBT().copy();

        assertTrue(QualityCandidateAugmenter.isEligible(stack));
        assertFalse(QualityCandidateAugmenter.isEligible(new ItemStack(Items.DIAMOND_HELMET)));
        QualityType selectedType = new QualityType();
        selectedType.qualities = new QualityEntry[] {nativeEntry("normal", 100)};
        QualityEntry selected = QualityCandidateAugmenter.choose(
                selectedType, IntegrationConfigSnapshot.defaults().getQualityTools(),
                QualityCandidateAugmenter.isEligible(stack), true);

        assertTrue(selected != null);
        assertFalse("normal".equalsIgnoreCase(selected.name));
        assertEquals(before, stack.serializeNBT());
    }

    private static void assertBuiltIn(QualityEntry entry,
                                      IntegrationConfigSnapshot.BuiltInQuality quality) {
        assertEquals(quality.getDefaultDisplayName(), entry.name);
        assertEquals(TextFormatting.AQUA, entry.color);
        assertEquals(quality.getDefaultWeight(), entry.weight);
        assertEquals(1, entry.attributeMap.get(quality.getAttributeName()).size());
        AttributeModifier modifier = entry.attributeMap.get(quality.getAttributeName())
                .iterator().next();
        assertEquals(quality.getDefaultAmount(), modifier.getAmount());
        assertEquals(0, modifier.getOperation());
    }

    private static EnumMap<IntegrationConfigSnapshot.BuiltInQuality,
            IntegrationConfigSnapshot.BuiltInQualityConfig> defaults() {
        return new EnumMap<>(IntegrationConfigSnapshot.defaults().getQualityTools()
                .getBuiltInQualities());
    }

    private static QualityEntry nativeEntry(String name, int weight) {
        QualityEntry entry = new QualityEntry();
        entry.name = name;
        entry.color = TextFormatting.WHITE;
        entry.weight = weight;
        entry.attributeMap = HashMultimap.create();
        return entry;
    }

    private static boolean contains(QualityEntry[] entries, String name) {
        for (QualityEntry entry : entries) {
            if (name.equals(entry.name)) {
                return true;
            }
        }
        return false;
    }

    private static QualityEntry find(QualityEntry[] entries, String name) {
        for (QualityEntry entry : entries) {
            if (name.equals(entry.name)) {
                return entry;
            }
        }
        throw new AssertionError("Missing quality " + name);
    }

    private static final class TrackingQualityType extends QualityType {
        private final QualityEntry result;
        private int calls;
        private boolean reforging;

        private TrackingQualityType(QualityEntry result) {
            this.result = result;
        }

        @Override
        public QualityEntry chooseQualityEntry(boolean reforging) {
            calls++;
            this.reforging = reforging;
            return result;
        }
    }
}
