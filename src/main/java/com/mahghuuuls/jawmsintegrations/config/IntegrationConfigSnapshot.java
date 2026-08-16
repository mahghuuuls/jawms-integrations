package com.mahghuuuls.jawmsintegrations.config;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/** Restart-scoped, validated configuration consumed by runtime services. */
public final class IntegrationConfigSnapshot {

    private final QualityToolsConfig qualityTools;
    private final AncientSpellcraftConfig ancientSpellcraft;
    private final DiagnosticsConfig diagnostics;

    public IntegrationConfigSnapshot(QualityToolsConfig qualityTools,
                                     AncientSpellcraftConfig ancientSpellcraft,
                                     DiagnosticsConfig diagnostics) {
        if (qualityTools == null || ancientSpellcraft == null || diagnostics == null) {
            throw new IllegalArgumentException("Configuration sections must not be null");
        }
        this.qualityTools = qualityTools;
        this.ancientSpellcraft = ancientSpellcraft;
        this.diagnostics = diagnostics;
    }

    public static IntegrationConfigSnapshot defaults() {
        return new IntegrationConfigSnapshot(
                new QualityToolsConfig(true, true),
                new AncientSpellcraftConfig(true),
                new DiagnosticsConfig(false)
        );
    }

    public QualityToolsConfig getQualityTools() {
        return qualityTools;
    }

    public AncientSpellcraftConfig getAncientSpellcraft() {
        return ancientSpellcraft;
    }

    public DiagnosticsConfig getDiagnostics() {
        return diagnostics;
    }

    public static final class QualityToolsConfig {
        private final boolean integrationEnabled;
        private final boolean builtInQualitiesEnabled;
        private final Map<BuiltInQuality, BuiltInQualityConfig> builtInQualities;

        public QualityToolsConfig(boolean integrationEnabled, boolean builtInQualitiesEnabled) {
            this(integrationEnabled, builtInQualitiesEnabled, defaultBuiltInQualities());
        }

        public QualityToolsConfig(boolean integrationEnabled,
                                  boolean builtInQualitiesEnabled,
                                  Map<BuiltInQuality, BuiltInQualityConfig> builtInQualities) {
            if (builtInQualities == null || builtInQualities.size() != BuiltInQuality.values().length) {
                throw new IllegalArgumentException("Every built-in quality must have configuration");
            }
            for (BuiltInQuality quality : BuiltInQuality.values()) {
                if (builtInQualities.get(quality) == null) {
                    throw new IllegalArgumentException("Missing configuration for " + quality);
                }
            }
            this.integrationEnabled = integrationEnabled;
            this.builtInQualitiesEnabled = builtInQualitiesEnabled;
            this.builtInQualities = Collections.unmodifiableMap(new EnumMap<>(builtInQualities));
        }

        public boolean isIntegrationEnabled() {
            return integrationEnabled;
        }

        public boolean areBuiltInQualitiesEnabled() {
            return builtInQualitiesEnabled;
        }

        public Map<BuiltInQuality, BuiltInQualityConfig> getBuiltInQualities() {
            return builtInQualities;
        }

        public BuiltInQualityConfig getBuiltInQuality(BuiltInQuality quality) {
            BuiltInQualityConfig configured = builtInQualities.get(quality);
            if (configured == null) {
                throw new IllegalArgumentException("Unknown built-in quality " + quality);
            }
            return configured;
        }

        private static Map<BuiltInQuality, BuiltInQualityConfig> defaultBuiltInQualities() {
            EnumMap<BuiltInQuality, BuiltInQualityConfig> defaults =
                    new EnumMap<>(BuiltInQuality.class);
            for (BuiltInQuality quality : BuiltInQuality.values()) {
                defaults.put(quality, BuiltInQualityConfig.defaults(quality));
            }
            return defaults;
        }
    }

    public enum BuiltInQuality {
        MANAWOVEN("manawoven", "Manawoven", "jawmsintegrations.max_mana_percent", 5.0D, 5),
        MEDITATIVE("meditative", "Meditative", "jawmsintegrations.mana_regen_percent", 5.0D, 5),
        EFFICIENT_CASTING("efficientCasting", "Efficient Casting", "jawmsintegrations.spell_efficiency", 5.0D, 2),
        SWIFT_RECOVERY("swiftRecovery", "Swift Recovery", "jawmsintegrations.mana_regen_delay_reduction_percent", 5.0D, 2),
        MAGIC_FOCUS("magicFocus", "Magic Focus", "jawmsintegrations.spell_efficiency_magic", 8.0D, 1),
        FIRE_FOCUS("fireFocus", "Fire Focus", "jawmsintegrations.spell_efficiency_fire", 8.0D, 1),
        ICE_FOCUS("iceFocus", "Ice Focus", "jawmsintegrations.spell_efficiency_ice", 8.0D, 1),
        LIGHTNING_FOCUS("lightningFocus", "Lightning Focus", "jawmsintegrations.spell_efficiency_lightning", 8.0D, 1),
        NECROMANCY_FOCUS("necromancyFocus", "Necromancy Focus", "jawmsintegrations.spell_efficiency_necromancy", 8.0D, 1),
        EARTH_FOCUS("earthFocus", "Earth Focus", "jawmsintegrations.spell_efficiency_earth", 8.0D, 1),
        SORCERY_FOCUS("sorceryFocus", "Sorcery Focus", "jawmsintegrations.spell_efficiency_sorcery", 8.0D, 1),
        HEALING_FOCUS("healingFocus", "Healing Focus", "jawmsintegrations.spell_efficiency_healing", 8.0D, 1);

        private final String configKey;
        private final String defaultDisplayName;
        private final String attributeName;
        private final double defaultAmount;
        private final int defaultWeight;

        BuiltInQuality(String configKey,
                       String defaultDisplayName,
                       String attributeName,
                       double defaultAmount,
                       int defaultWeight) {
            this.configKey = configKey;
            this.defaultDisplayName = defaultDisplayName;
            this.attributeName = attributeName;
            this.defaultAmount = defaultAmount;
            this.defaultWeight = defaultWeight;
        }

        public String getConfigKey() {
            return configKey;
        }

        public String getDefaultDisplayName() {
            return defaultDisplayName;
        }

        public String getAttributeName() {
            return attributeName;
        }

        public double getDefaultAmount() {
            return defaultAmount;
        }

        public int getDefaultWeight() {
            return defaultWeight;
        }
    }

    public static final class BuiltInQualityConfig {
        private final boolean enabled;
        private final String displayName;
        private final double amount;
        private final int weight;

        public BuiltInQualityConfig(boolean enabled, String displayName, double amount, int weight) {
            if (displayName == null || displayName.trim().isEmpty()) {
                throw new IllegalArgumentException("Built-in quality display name must not be blank");
            }
            if (!Double.isFinite(amount) || amount <= 0.0D) {
                throw new IllegalArgumentException("Built-in quality amount must be finite and positive");
            }
            if (weight <= 0 || weight > 10000) {
                throw new IllegalArgumentException("Built-in quality weight must be from 1 through 10000");
            }
            this.enabled = enabled;
            this.displayName = displayName.trim();
            this.amount = amount;
            this.weight = weight;
        }

        static BuiltInQualityConfig defaults(BuiltInQuality quality) {
            return new BuiltInQualityConfig(true, quality.getDefaultDisplayName(),
                    quality.getDefaultAmount(), quality.getDefaultWeight());
        }

        public boolean isEnabled() {
            return enabled;
        }

        public String getDisplayName() {
            return displayName;
        }

        public double getAmount() {
            return amount;
        }

        public int getWeight() {
            return weight;
        }
    }

    public static final class AncientSpellcraftConfig {
        private final boolean integrationEnabled;

        public AncientSpellcraftConfig(boolean integrationEnabled) {
            this.integrationEnabled = integrationEnabled;
        }

        public boolean isIntegrationEnabled() {
            return integrationEnabled;
        }
    }

    public static final class DiagnosticsConfig {
        private final boolean enabled;

        public DiagnosticsConfig(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isEnabled() {
            return enabled;
        }
    }
}
