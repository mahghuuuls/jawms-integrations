package com.mahghuuuls.jawmsintegrations.config;

import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

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
                "Offers the built-in Manawoven and Meditative armor qualities. User-defined JAWMS attributes remain available when this is false. Requires restart.",
                warnings
        );
        boolean ancientSpellcraftEnabled = readBoolean(
                configuration,
                ANCIENT_SPELLCRAFT,
                "enabled",
                true,
                "Enables all JAWMS replacement behavior owned by the Ancient Spellcraft integration. Requires restart.",
                warnings
        );
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
                        builtInQualitiesEnabled
                ),
                new IntegrationConfigSnapshot.AncientSpellcraftConfig(ancientSpellcraftEnabled),
                new IntegrationConfigSnapshot.DiagnosticsConfig(diagnosticsEnabled)
        );
        return new LoadResult(snapshot, warnings);
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
        return validateBoolean(
                category,
                key,
                raw == null ? property.getString() : raw,
                defaultValue,
                warnings
        );
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
