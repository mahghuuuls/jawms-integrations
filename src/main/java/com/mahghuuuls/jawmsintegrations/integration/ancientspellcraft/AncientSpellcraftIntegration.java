package com.mahghuuuls.jawmsintegrations.integration.ancientspellcraft;

import com.mahghuuuls.jawms.api.ManaApi;
import com.mahghuuuls.jawmsintegrations.Tags;
import com.mahghuuuls.jawmsintegrations.config.IntegrationConfigSnapshot;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import java.util.EnumMap;
import java.util.Map;

/** Activates the four static item providers against the exact registered dependency items. */
public final class AncientSpellcraftIntegration {

    private AncientSpellcraftIntegration() {
    }

    public static AncientReplacementPolicy activate(
            IntegrationConfigSnapshot.AncientSpellcraftConfig config) {
        AncientReplacementPolicy policy = new AncientReplacementPolicy(true, config);
        Map<AncientReplacement, Item> items = resolveStaticItems();
        try {
            for (Map.Entry<AncientReplacement, Item> entry : items.entrySet()) {
                register(entry.getKey(), entry.getValue());
            }
            AncientReplacementPolicy.install(policy);
            EverfullManaService.install(new EverfullManaService(
                    true, config.getEverfullManaFlask()));
            DagorimFlaskService.install(new DagorimFlaskService(
                    true, config.getRingOfDagorim()));
            return policy;
        } catch (RuntimeException exception) {
            AncientReplacementPolicy.install(AncientReplacementPolicy.disabled());
            EverfullManaService.install(EverfullManaService.disabled());
            DagorimFlaskService.install(DagorimFlaskService.disabled());
            throw exception;
        }
    }

    private static Map<AncientReplacement, Item> resolveStaticItems() {
        Map<AncientReplacement, Item> items = new EnumMap<>(AncientReplacement.class);
        for (AncientReplacement replacement : AncientReplacementPolicy.staticReplacements()) {
            Item item = ForgeRegistries.ITEMS.getValue(replacement.getRegistryName());
            if (item == null) {
                throw new IllegalStateException("Missing supported Ancient Spellcraft item "
                        + replacement.getRegistryName());
            }
            items.put(replacement, item);
        }
        return items;
    }

    private static void register(AncientReplacement replacement, Item item) {
        ResourceLocation providerId = new ResourceLocation(Tags.MOD_ID,
                "ancient_" + replacement.getRegistryName().getPath());
        ManaApi.registerItemProvider(providerId, item,
                (player, context) -> AncientReplacementPolicy.active().contribution(context));
    }
}
