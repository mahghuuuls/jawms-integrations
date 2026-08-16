package com.mahghuuuls.jawmsintegrations.integration;

/** Exact optional dependencies supported by the first release. */
public enum IntegrationId {
    QUALITY_TOOLS("Quality Tools", "qualitytools", "1.0.7", "1.0.7_for_1.12.2"),
    ANCIENT_SPELLCRAFT(
            "Ancient Spellcraft",
            "ancientspellcraft",
            "1.8.3",
            "1.12.2-1.8.3",
            "1.12.2-INDEV"
    );

    private final String displayName;
    private final String modId;
    private final String supportedVersion;
    private final String supportedMetadataVersion;
    private final String[] loaderVersionAliases;

    IntegrationId(String displayName,
                  String modId,
                  String supportedVersion,
                  String supportedMetadataVersion,
                  String... loaderVersionAliases) {
        this.displayName = displayName;
        this.modId = modId;
        this.supportedVersion = supportedVersion;
        this.supportedMetadataVersion = supportedMetadataVersion;
        this.loaderVersionAliases = loaderVersionAliases.clone();
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getModId() {
        return modId;
    }

    public String getSupportedVersion() {
        return supportedVersion;
    }

    public String getSupportedMetadataVersion() {
        return supportedMetadataVersion;
    }

    public boolean supports(String version) {
        return supportedMetadataVersion.equals(version);
    }

    /** Loader aliases are trusted only after the early gate confirms exact release metadata. */
    public boolean acceptsLoaderVersion(String version) {
        if (supports(version)) {
            return true;
        }
        for (String alias : loaderVersionAliases) {
            if (alias.equals(version)) {
                return true;
            }
        }
        return false;
    }
}
