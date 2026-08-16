package com.mahghuuuls.jawmsintegrations.integration.ancientspellcraft;

import com.mahghuuuls.jawms.api.ManaContribution;
import com.mahghuuuls.jawms.api.ManaItemContext;
import com.mahghuuuls.jawms.api.ManaSlotCategory;
import com.mahghuuuls.jawmsintegrations.config.IntegrationConfigSnapshot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

/** Restart-scoped authority for Ancient item classification, contribution, and suppression. */
public final class AncientReplacementPolicy {

    private static volatile AncientReplacementPolicy active = disabled();

    private final boolean integrationActive;
    private final IntegrationConfigSnapshot.AncientSpellcraftConfig config;

    public AncientReplacementPolicy(boolean integrationActive,
                                    IntegrationConfigSnapshot.AncientSpellcraftConfig config) {
        if (config == null) {
            throw new NullPointerException("config");
        }
        this.integrationActive = integrationActive;
        this.config = config;
    }

    public static AncientReplacementPolicy disabled() {
        return new AncientReplacementPolicy(false, new IntegrationConfigSnapshot.AncientSpellcraftConfig(false));
    }

    public static void install(AncientReplacementPolicy policy) {
        if (policy == null) {
            throw new NullPointerException("policy");
        }
        active = policy;
    }

    public static AncientReplacementPolicy active() {
        return active;
    }

    public ManaContribution contribution(ManaItemContext context) {
        if (!integrationActive || context == null
                || context.getSlotCategory() != ManaSlotCategory.BAUBLES) {
            return ManaContribution.EMPTY;
        }
        AncientReplacement replacement = replacementFor(context.getStack());
        if (!isStaticEnabled(replacement)) {
            return ManaContribution.EMPTY;
        }
        switch (replacement) {
            case LESSER_MANA_RING:
                return ManaContribution.builder().flatMaximumMana(
                        config.getLesserManaRing().getValue()).build();
            case GREATER_MANA_RING:
                return ManaContribution.builder().flatMaximumMana(
                        config.getGreaterManaRing().getValue()).build();
            case MAJESTIC_MANA_CHARM:
                return ManaContribution.builder().flatMaximumMana(
                        config.getMajesticManaCharm().getValue()).build();
            case CRYSTAL_RING:
                return ManaContribution.builder().globalSpellEfficiency(
                        config.getCrystalRing().getValue()).build();
            default:
                return ManaContribution.EMPTY;
        }
    }

    public boolean shouldSuppressLegacy(ItemStack stack, AncientReplacement expected) {
        return expected != null && replacementFor(stack) == expected && isStaticEnabled(expected);
    }

    public boolean isStaticEnabled(AncientReplacement replacement) {
        if (!integrationActive || replacement == null) {
            return false;
        }
        switch (replacement) {
            case LESSER_MANA_RING:
                return config.getLesserManaRing().isEnabled();
            case GREATER_MANA_RING:
                return config.getGreaterManaRing().isEnabled();
            case MAJESTIC_MANA_CHARM:
                return config.getMajesticManaCharm().isEnabled();
            case CRYSTAL_RING:
                return config.getCrystalRing().isEnabled();
            default:
                return false;
        }
    }

    public boolean isStorageReplacement(ItemStack stack) {
        AncientReplacement replacement = replacementFor(stack);
        return isStorage(replacement) && isStaticEnabled(replacement);
    }

    public boolean isEverfullEnabled() {
        return integrationActive && config.getEverfullManaFlask().isEnabled();
    }

    public boolean shouldReplaceEverfull(ItemStack stack) {
        return isEverfullEnabled()
                && replacementFor(stack) == AncientReplacement.EVERFULL_MANA_FLASK;
    }

    public boolean isDagorimEnabled() {
        return integrationActive && config.getRingOfDagorim().isEnabled();
    }

    public boolean shouldReplaceDagorim(ItemStack stack) {
        return isDagorimEnabled()
                && replacementFor(stack) == AncientReplacement.RING_OF_DAGORIM;
    }

    public static boolean isStorage(AncientReplacement replacement) {
        return replacement == AncientReplacement.LESSER_MANA_RING
                || replacement == AncientReplacement.GREATER_MANA_RING
                || replacement == AncientReplacement.MAJESTIC_MANA_CHARM;
    }

    public static AncientReplacement[] staticReplacements() {
        return new AncientReplacement[] {
                AncientReplacement.LESSER_MANA_RING,
                AncientReplacement.GREATER_MANA_RING,
                AncientReplacement.MAJESTIC_MANA_CHARM,
                AncientReplacement.CRYSTAL_RING
        };
    }

    public AncientReplacement replacementFor(ItemStack stack) {
        if (stack == null || stack.isEmpty() || stack.getItem().getRegistryName() == null) {
            return null;
        }
        return AncientReplacement.forRegistryName(stack.getItem().getRegistryName());
    }

    public double presentationValue(AncientReplacement replacement) {
        switch (replacement) {
            case LESSER_MANA_RING:
                return config.getLesserManaRing().getValue();
            case GREATER_MANA_RING:
                return config.getGreaterManaRing().getValue();
            case MAJESTIC_MANA_CHARM:
                return config.getMajesticManaCharm().getValue();
            case CRYSTAL_RING:
                return config.getCrystalRing().getValue();
            case EVERFULL_MANA_FLASK:
                return EverfullManaState.CAPACITY;
            case RING_OF_DAGORIM:
                return config.getRingOfDagorim().getIntervalSeconds();
            default:
                return 0.0D;
        }
    }

    public boolean isIntegrationActive() {
        return integrationActive;
    }

    public boolean isPresentationEnabled(AncientReplacement replacement) {
        if (replacement == AncientReplacement.EVERFULL_MANA_FLASK) return isEverfullEnabled();
        if (replacement == AncientReplacement.RING_OF_DAGORIM) return isDagorimEnabled();
        return isStaticEnabled(replacement);
    }

    public IntegrationConfigSnapshot.EverfullManaFlaskConfig getEverfullConfig() {
        return config.getEverfullManaFlask();
    }

    public IntegrationConfigSnapshot.RingOfDagorimConfig getDagorimConfig() {
        return config.getRingOfDagorim();
    }
}
