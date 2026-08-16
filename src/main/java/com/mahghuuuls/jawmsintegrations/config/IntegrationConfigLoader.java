package com.mahghuuuls.jawmsintegrations.config;

import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Owns Forge configuration I/O, validation, defaults, and warning text. */
public final class IntegrationConfigLoader {

    private static final String QUALITY_TOOLS = "quality_tools";
    private static final String ANCIENT_SPELLCRAFT = "ancient_spellcraft";
    private static final String DIAGNOSTICS = "diagnostics";

    private IntegrationConfigLoader() {
    }

    public static LoadResult load(File file) {
        Configuration configuration = new Configuration(file);
        List<String> warnings = new ArrayList<>();

        boolean qualityToolsEnabled = readBoolean(
                configuration,
                QUALITY_TOOLS,
                "enabled",
                true,
                "Enables all JAWMS behavior owned by the Quality Tools integration. Requires restart.",
                warnings
        );
        boolean builtInQualitiesEnabled = readBoolean(
                configuration,
                QUALITY_TOOLS,
                "builtInQualitiesEnabled",
                true,
                "Offers JAWMS built-in qualities on official Electroblob Wizardry mage armor. User-defined JAWMS attributes remain available when this is false. Requires restart.",
                warnings
        );
        Map<IntegrationConfigSnapshot.BuiltInQuality, IntegrationConfigSnapshot.BuiltInQualityConfig>
                builtInQualities = readBuiltInQualities(configuration, warnings);
        boolean ancientSpellcraftEnabled = readBoolean(
                configuration,
                ANCIENT_SPELLCRAFT,
                "enabled",
                true,
                "Enables all JAWMS replacement behavior owned by the Ancient Spellcraft integration. Requires restart.",
                warnings
        );
        IntegrationConfigSnapshot.ToggleIntConfig lesserManaRing = readToggleInt(
                configuration, "lesser_mana_ring", "flatMaximumMana", 8, warnings);
        IntegrationConfigSnapshot.ToggleIntConfig greaterManaRing = readToggleInt(
                configuration, "greater_mana_ring", "flatMaximumMana", 12, warnings);
        IntegrationConfigSnapshot.ToggleIntConfig majesticManaCharm = readToggleInt(
                configuration, "majestic_mana_charm", "flatMaximumMana", 18, warnings);
        IntegrationConfigSnapshot.ToggleDoubleConfig crystalRing = readToggleDouble(
                configuration, "crystal_ring", "spellEfficiency", 25.0D, warnings);
        String everfullCategory = ANCIENT_SPELLCRAFT + ".replacements.everfull_mana_flask";
        IntegrationConfigSnapshot.EverfullManaFlaskConfig everfullManaFlask =
                new IntegrationConfigSnapshot.EverfullManaFlaskConfig(
                        readBoolean(configuration, everfullCategory, "enabled", true,
                                "Enables the JAWMS Everfull Mana Flask replacement. Requires restart.",
                                warnings),
                        readPositiveInt(configuration, everfullCategory, "regenerationAmount", 1,
                                "Stored mana regenerated at each interval. Requires restart.", warnings),
                        readPositiveInt(configuration, everfullCategory,
                                "regenerationIntervalSeconds", 12,
                                "Seconds between stored-mana regeneration. Requires restart.", warnings),
                        readPositiveInt(configuration, everfullCategory, "transferAmount", 10,
                                "Maximum JAWMS mana restored per valid offhand use. Requires restart.",
                                warnings));
        String dagorimCategory = ANCIENT_SPELLCRAFT + ".replacements.ring_of_dagorim";
        IntegrationConfigSnapshot.RingOfDagorimConfig ringOfDagorim =
                new IntegrationConfigSnapshot.RingOfDagorimConfig(
                        readBoolean(configuration, dagorimCategory, "enabled", true,
                                "Enables the JAWMS Ring of Dagorim replacement. Requires restart.", warnings),
                        readPositiveInt(configuration, dagorimCategory, "intervalSeconds", 5,
                                "Seconds between equipped-ring checks. Requires restart.", warnings),
                        readBoundedInt(configuration, dagorimCategory, "manaThreshold", 20, 0, 10000,
                                "Checks only while current JAWMS mana is below this value. Requires restart.", warnings),
                        readBoundedDouble(configuration, dagorimCategory, "activationChancePercent",
                                20.0D, 0.0D, 100.0D,
                                "Chance per eligible check to consume one ordinary mana flask. Requires restart.", warnings));
        boolean diagnosticsEnabled = readBoolean(
                configuration,
                DIAGNOSTICS,
                "enabled",
                false,
                "Writes one detailed integration summary during startup. Requires restart.",
                warnings
        );

        if (configuration.hasChanged()) {
            configuration.save();
        }

        IntegrationConfigSnapshot snapshot = new IntegrationConfigSnapshot(
                new IntegrationConfigSnapshot.QualityToolsConfig(
                        qualityToolsEnabled,
                        builtInQualitiesEnabled,
                        builtInQualities
                ),
                new IntegrationConfigSnapshot.AncientSpellcraftConfig(
                        ancientSpellcraftEnabled,
                        lesserManaRing,
                        greaterManaRing,
                        majesticManaCharm,
                        crystalRing,
                        everfullManaFlask,
                        ringOfDagorim),
                new IntegrationConfigSnapshot.DiagnosticsConfig(diagnosticsEnabled)
        );
        return new LoadResult(snapshot, warnings);
    }

    private static IntegrationConfigSnapshot.ToggleIntConfig readToggleInt(
            Configuration configuration,
            String replacement,
            String valueKey,
            int defaultValue,
            List<String> warnings) {
        String category = ANCIENT_SPELLCRAFT + ".replacements." + replacement;
        boolean enabled = readBoolean(configuration, category, "enabled", true,
                "Enables this Ancient Spellcraft replacement. Requires restart.", warnings);
        int value = readPositiveInt(configuration, category, valueKey, defaultValue,
                "Positive JAWMS replacement value. Requires restart.", warnings);
        return new IntegrationConfigSnapshot.ToggleIntConfig(enabled, value);
    }

    private static IntegrationConfigSnapshot.ToggleDoubleConfig readToggleDouble(
            Configuration configuration,
            String replacement,
            String valueKey,
            double defaultValue,
            List<String> warnings) {
        String category = ANCIENT_SPELLCRAFT + ".replacements." + replacement;
        boolean enabled = readBoolean(configuration, category, "enabled", true,
                "Enables this Ancient Spellcraft replacement. Requires restart.", warnings);
        double value = readPositiveDouble(configuration, category, valueKey, defaultValue,
                "Positive JAWMS replacement value. Requires restart.", warnings);
        return new IntegrationConfigSnapshot.ToggleDoubleConfig(enabled, value);
    }

    private static Map<IntegrationConfigSnapshot.BuiltInQuality,
            IntegrationConfigSnapshot.BuiltInQualityConfig> readBuiltInQualities(
            Configuration configuration,
            List<String> warnings) {
        EnumMap<IntegrationConfigSnapshot.BuiltInQuality,
                IntegrationConfigSnapshot.BuiltInQualityConfig> result =
                new EnumMap<>(IntegrationConfigSnapshot.BuiltInQuality.class);
        for (IntegrationConfigSnapshot.BuiltInQuality quality
                : IntegrationConfigSnapshot.BuiltInQuality.values()) {
            String category = QUALITY_TOOLS + ".built_in_qualities." + quality.getConfigKey();
            boolean enabled = readBoolean(configuration, category, "enabled", true,
                    "Offers this built-in quality on new or reforged eligible mage armor. Requires restart.",
                    warnings);
            String displayName = readNonBlankString(configuration, category, "displayName",
                    quality.getDefaultDisplayName(),
                    "Display name stored on newly rolled items. Requires restart.", warnings);
            double amount = readPositiveDouble(configuration, category, "amount",
                    quality.getDefaultAmount(),
                    "Positive JAWMS attribute amount stored on newly rolled items. Requires restart.", warnings);
            int weight = readPositiveInt(configuration, category, "weight",
                    quality.getDefaultWeight(),
                    "Positive relative Quality Tools selection weight. Requires restart.", warnings);
            result.put(quality, new IntegrationConfigSnapshot.BuiltInQualityConfig(
                    enabled, displayName, amount, weight));
        }
        return result;
    }

    private static boolean readBoolean(Configuration configuration,
                                       String category,
                                       String key,
                                       boolean defaultValue,
                                       String comment,
                                       List<String> warnings) {
        Property existing = configuration.hasCategory(category)
                ? configuration.getCategory(category).get(key)
                : null;
        String raw = existing == null ? null : existing.getString();
        Property property = configuration.get(category, key, defaultValue, comment);
        int warningCount = warnings.size();
        boolean validated = validateBoolean(
                category,
                key,
                raw == null ? property.getString() : raw,
                defaultValue,
                warnings
        );
        if (warnings.size() != warningCount) {
            property.set(validated);
        }
        return validated;
    }

    static boolean validateBoolean(String category,
                                   String key,
                                   String raw,
                                   boolean defaultValue,
                                   List<String> warnings) {
        String rawValue = raw.trim().toLowerCase(Locale.ROOT);
        if ("true".equals(rawValue)) {
            return true;
        }
        if ("false".equals(rawValue)) {
            return false;
        }
        warnings.add("Invalid configuration value for '" + category + "." + key
                + "': expected true or false but found '" + raw
                + "'; using default " + defaultValue
                + ". Forge will write that fallback to the file.");
        return defaultValue;
    }

    private static String readNonBlankString(Configuration configuration,
                                             String category,
                                             String key,
                                             String defaultValue,
                                             String comment,
                                             List<String> warnings) {
        Property property = configuration.get(category, key, defaultValue, comment);
        String value = property.getString();
        int warningCount = warnings.size();
        String validated = validateNonBlankString(category, key, value, defaultValue, warnings);
        if (warnings.size() != warningCount) {
            property.set(validated);
        }
        return validated;
    }

    private static double readPositiveDouble(Configuration configuration,
                                             String category,
                                             String key,
                                             double defaultValue,
                                             String comment,
                                             List<String> warnings) {
        Property property = configuration.get(category, key, defaultValue, comment);
        String raw = property.getString();
        int warningCount = warnings.size();
        double validated = validatePositiveDouble(category, key, raw, defaultValue, warnings);
        if (warnings.size() != warningCount) {
            property.set(validated);
        }
        return validated;
    }

    static String validateNonBlankString(String category,
                                         String key,
                                         String raw,
                                         String defaultValue,
                                         List<String> warnings) {
        if (raw != null && !raw.trim().isEmpty()) {
            return raw.trim();
        }
        warnings.add(invalid(category, key, raw, "a non-blank string", defaultValue));
        return defaultValue;
    }

    static double validatePositiveDouble(String category,
                                         String key,
                                         String raw,
                                         double defaultValue,
                                         List<String> warnings) {
        if (raw != null) {
            try {
                double value = Double.parseDouble(raw);
                if (Double.isFinite(value) && value > 0.0D) {
                    return value;
                }
            } catch (NumberFormatException ignored) {
                // Report one bounded field warning below.
            }
        }
        warnings.add(invalid(category, key, raw, "a finite number greater than zero", defaultValue));
        return defaultValue;
    }

    private static int readPositiveInt(Configuration configuration,
                                       String category,
                                       String key,
                                       int defaultValue,
                                       String comment,
                                       List<String> warnings) {
        Property property = configuration.get(category, key, defaultValue, comment);
        String raw = property.getString();
        int warningCount = warnings.size();
        int validated = validatePositiveInt(category, key, raw, defaultValue, warnings);
        if (warnings.size() != warningCount) {
            property.set(validated);
        }
        return validated;
    }

    private static int readBoundedInt(Configuration configuration, String category, String key,
                                      int defaultValue, int minimum, int maximum,
                                      String comment, List<String> warnings) {
        Property property = configuration.get(category, key, defaultValue, comment);
        String raw = property.getString();
        try {
            int value = Integer.parseInt(raw);
            if (value >= minimum && value <= maximum) return value;
        } catch (NumberFormatException ignored) { }
        warnings.add(invalid(category, key, raw,
                "an integer from " + minimum + " through " + maximum, defaultValue));
        property.set(defaultValue);
        return defaultValue;
    }

    private static double readBoundedDouble(Configuration configuration, String category, String key,
                                            double defaultValue, double minimum, double maximum,
                                            String comment, List<String> warnings) {
        Property property = configuration.get(category, key, defaultValue, comment);
        String raw = property.getString();
        try {
            double value = Double.parseDouble(raw);
            if (Double.isFinite(value) && value >= minimum && value <= maximum) return value;
        } catch (NumberFormatException ignored) { }
        warnings.add(invalid(category, key, raw,
                "a finite number from " + minimum + " through " + maximum, defaultValue));
        property.set(defaultValue);
        return defaultValue;
    }

    static int validatePositiveInt(String category,
                                   String key,
                                   String raw,
                                   int defaultValue,
                                   List<String> warnings) {
        if (raw != null) {
            try {
                int value = Integer.parseInt(raw);
                if (value > 0 && value <= 10000) {
                    return value;
                }
            } catch (NumberFormatException ignored) {
                // Report one bounded field warning below.
            }
        }
        warnings.add(invalid(category, key, raw, "an integer from 1 through 10000", defaultValue));
        return defaultValue;
    }

    private static String invalid(String category,
                                  String key,
                                  Object raw,
                                  String expected,
                                  Object defaultValue) {
        return "Invalid configuration value for '" + category + "." + key
                + "': expected " + expected + " but found '" + raw
                + "'; using default " + defaultValue
                + ". Forge will write that fallback to the file.";
    }

    public static final class LoadResult {
        private final IntegrationConfigSnapshot snapshot;
        private final List<String> warnings;

        private LoadResult(IntegrationConfigSnapshot snapshot, List<String> warnings) {
            this.snapshot = snapshot;
            this.warnings = Collections.unmodifiableList(new ArrayList<>(warnings));
        }

        public IntegrationConfigSnapshot getSnapshot() {
            return snapshot;
        }

        public List<String> getWarnings() {
            return warnings;
        }
    }
}
