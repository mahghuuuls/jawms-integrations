package com.mahghuuuls.jawmsintegrations.config;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/** Restart-scoped, validated configuration consumed by runtime services. */
public final class IntegrationConfigSnapshot {

    private final QualityToolsConfig qualityTools;
    private final AncientSpellcraftConfig ancientSpellcraft;
    private final IntegrationToggleConfig craftTweaker;
    private final IntegrationToggleConfig arsMagica;
    private final DiagnosticsConfig diagnostics;

    public IntegrationConfigSnapshot(QualityToolsConfig qualityTools,
                                     AncientSpellcraftConfig ancientSpellcraft,
                                     IntegrationToggleConfig craftTweaker,
                                     IntegrationToggleConfig arsMagica,
                                     DiagnosticsConfig diagnostics) {
        if (qualityTools == null || ancientSpellcraft == null || craftTweaker == null
                || arsMagica == null || diagnostics == null) {
            throw new IllegalArgumentException("Configuration sections must not be null");
        }
        this.qualityTools = qualityTools;
        this.ancientSpellcraft = ancientSpellcraft;
        this.craftTweaker = craftTweaker;
        this.arsMagica = arsMagica;
        this.diagnostics = diagnostics;
    }

    public static IntegrationConfigSnapshot defaults() {
        return new IntegrationConfigSnapshot(
                new QualityToolsConfig(true, true),
                AncientSpellcraftConfig.defaults(),
                new IntegrationToggleConfig(true),
                new IntegrationToggleConfig(true),
                new DiagnosticsConfig(false)
        );
    }

    public QualityToolsConfig getQualityTools() {
        return qualityTools;
    }

    public AncientSpellcraftConfig getAncientSpellcraft() {
        return ancientSpellcraft;
    }

    public IntegrationToggleConfig getCraftTweaker() {
        return craftTweaker;
    }

    public IntegrationToggleConfig getArsMagica() {
        return arsMagica;
    }

    public DiagnosticsConfig getDiagnostics() {
        return diagnostics;
    }

    public static final class IntegrationToggleConfig {
        private final boolean enabled;

        public IntegrationToggleConfig(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isEnabled() {
            return enabled;
        }
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
        SWIFT_RECOVERY("swiftRecovery", "Swift Recovery", "jawmsintegrations.mana_regen_delay_reduction_percent", 10.0D, 2),
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
        private final ToggleIntConfig lesserManaRing;
        private final ToggleIntConfig greaterManaRing;
        private final ToggleDoubleConfig majesticManaCharm;
        private final ToggleDoubleConfig crystalRing;
        private final EverfullManaFlaskConfig everfullManaFlask;
        private final RingOfDagorimConfig ringOfDagorim;

        public AncientSpellcraftConfig(boolean integrationEnabled) {
            this(integrationEnabled,
                    new ToggleIntConfig(true, 8),
                    new ToggleIntConfig(true, 12),
                    new ToggleDoubleConfig(true, 15.0D),
                    new ToggleDoubleConfig(true, 25.0D),
                    new EverfullManaFlaskConfig(true, 1, 12, 10),
                    new RingOfDagorimConfig(true, 5, 20, 20.0D));
        }

        public AncientSpellcraftConfig(boolean integrationEnabled,
                                       ToggleIntConfig lesserManaRing,
                                       ToggleIntConfig greaterManaRing,
                                       ToggleDoubleConfig majesticManaCharm,
                                       ToggleDoubleConfig crystalRing) {
            this(integrationEnabled, lesserManaRing, greaterManaRing, majesticManaCharm,
                    crystalRing, new EverfullManaFlaskConfig(true, 1, 12, 10),
                    new RingOfDagorimConfig(true, 5, 20, 20.0D));
        }

        public AncientSpellcraftConfig(boolean integrationEnabled,
                                       ToggleIntConfig lesserManaRing,
                                       ToggleIntConfig greaterManaRing,
                                       ToggleDoubleConfig majesticManaCharm,
                                       ToggleDoubleConfig crystalRing,
                                       EverfullManaFlaskConfig everfullManaFlask) {
            this(integrationEnabled, lesserManaRing, greaterManaRing, majesticManaCharm,
                    crystalRing, everfullManaFlask,
                    new RingOfDagorimConfig(true, 5, 20, 20.0D));
        }

        public AncientSpellcraftConfig(boolean integrationEnabled,
                                       ToggleIntConfig lesserManaRing,
                                       ToggleIntConfig greaterManaRing,
                                       ToggleDoubleConfig majesticManaCharm,
                                       ToggleDoubleConfig crystalRing,
                                       EverfullManaFlaskConfig everfullManaFlask,
                                       RingOfDagorimConfig ringOfDagorim) {
            if (lesserManaRing == null || greaterManaRing == null
                    || majesticManaCharm == null || crystalRing == null
                    || everfullManaFlask == null || ringOfDagorim == null) {
                throw new IllegalArgumentException("Ancient replacement configuration must not be null");
            }
            this.integrationEnabled = integrationEnabled;
            this.lesserManaRing = lesserManaRing;
            this.greaterManaRing = greaterManaRing;
            this.majesticManaCharm = majesticManaCharm;
            this.crystalRing = crystalRing;
            this.everfullManaFlask = everfullManaFlask;
            this.ringOfDagorim = ringOfDagorim;
        }

        static AncientSpellcraftConfig defaults() {
            return new AncientSpellcraftConfig(true);
        }

        public boolean isIntegrationEnabled() {
            return integrationEnabled;
        }

        public ToggleIntConfig getLesserManaRing() {
            return lesserManaRing;
        }

        public ToggleIntConfig getGreaterManaRing() {
            return greaterManaRing;
        }

        public ToggleDoubleConfig getMajesticManaCharm() {
            return majesticManaCharm;
        }

        public ToggleDoubleConfig getCrystalRing() {
            return crystalRing;
        }

        public EverfullManaFlaskConfig getEverfullManaFlask() {
            return everfullManaFlask;
        }

        public RingOfDagorimConfig getRingOfDagorim() {
            return ringOfDagorim;
        }
    }

    public static final class RingOfDagorimConfig {
        private final boolean enabled;
        private final int intervalSeconds;
        private final int manaThreshold;
        private final double activationChancePercent;

        public RingOfDagorimConfig(boolean enabled, int intervalSeconds,
                                  int manaThreshold, double activationChancePercent) {
            if (intervalSeconds <= 0 || intervalSeconds > 10000) {
                throw new IllegalArgumentException("Dagorim interval must be from 1 through 10000");
            }
            if (manaThreshold < 0 || manaThreshold > 10000) {
                throw new IllegalArgumentException("Dagorim mana threshold must be from 0 through 10000");
            }
            if (!Double.isFinite(activationChancePercent)
                    || activationChancePercent < 0.0D || activationChancePercent > 100.0D) {
                throw new IllegalArgumentException("Dagorim activation chance must be from 0 through 100");
            }
            this.enabled = enabled;
            this.intervalSeconds = intervalSeconds;
            this.manaThreshold = manaThreshold;
            this.activationChancePercent = activationChancePercent;
        }

        public boolean isEnabled() { return enabled; }
        public int getIntervalSeconds() { return intervalSeconds; }
        public int getManaThreshold() { return manaThreshold; }
        public double getActivationChancePercent() { return activationChancePercent; }
    }

    public static final class EverfullManaFlaskConfig {
        private final boolean enabled;
        private final int regenerationAmount;
        private final int regenerationIntervalSeconds;
        private final int transferAmount;

        public EverfullManaFlaskConfig(boolean enabled,
                                       int regenerationAmount,
                                       int regenerationIntervalSeconds,
                                       int transferAmount) {
            requirePositive(regenerationAmount, "Everfull regeneration amount");
            requirePositive(regenerationIntervalSeconds, "Everfull regeneration interval");
            requirePositive(transferAmount, "Everfull transfer amount");
            this.enabled = enabled;
            this.regenerationAmount = regenerationAmount;
            this.regenerationIntervalSeconds = regenerationIntervalSeconds;
            this.transferAmount = transferAmount;
        }

        private static void requirePositive(int value, String name) {
            if (value <= 0 || value > 10000) {
                throw new IllegalArgumentException(name + " must be from 1 through 10000");
            }
        }

        public boolean isEnabled() {
            return enabled;
        }

        public int getRegenerationAmount() {
            return regenerationAmount;
        }

        public int getRegenerationIntervalSeconds() {
            return regenerationIntervalSeconds;
        }

        public int getTransferAmount() {
            return transferAmount;
        }
    }

    public static final class ToggleIntConfig {
        private final boolean enabled;
        private final int value;

        public ToggleIntConfig(boolean enabled, int value) {
            if (value <= 0 || value > 10000) {
                throw new IllegalArgumentException(
                        "Replacement value must be from 1 through 10000");
            }
            this.enabled = enabled;
            this.value = value;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public int getValue() {
            return value;
        }
    }

    public static final class ToggleDoubleConfig {
        private final boolean enabled;
        private final double value;

        public ToggleDoubleConfig(boolean enabled, double value) {
            if (!Double.isFinite(value) || value <= 0.0D) {
                throw new IllegalArgumentException("Replacement value must be finite and positive");
            }
            this.enabled = enabled;
            this.value = value;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public double getValue() {
            return value;
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
