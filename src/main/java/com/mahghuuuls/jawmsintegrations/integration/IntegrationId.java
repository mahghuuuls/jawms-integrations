package com.mahghuuuls.jawmsintegrations.integration;

/** Minimum optional dependency releases supported without an upper version bound. */
public enum IntegrationId {
    QUALITY_TOOLS("Quality Tools", "qualitytools", "1.0.7", "1.0.7_for_1.12.2"),
    ANCIENT_SPELLCRAFT(
            "Ancient Spellcraft",
            "ancientspellcraft",
            "1.8.3",
            "1.12.2-1.8.3",
            "1.12.2-INDEV"
    ),
    CRAFTTWEAKER(
            "CraftTweaker",
            "crafttweaker",
            "1.12-4.1.20.715",
            "1.12-4.1.20.715",
            "4.1.20"
    ),
    ARS_MAGICA("Ars Magica 2: Rekindled", "arsmagica2", "1.6.2", "1.6.2");

    private final String displayName;
    private final String modId;
    private final String minimumVersion;
    private final String minimumMetadataVersion;
    private final String[] loaderVersionAliases;

    IntegrationId(String displayName,
                  String modId,
                  String supportedVersion,
                  String supportedMetadataVersion,
                  String... loaderVersionAliases) {
        this.displayName = displayName;
        this.modId = modId;
        this.minimumVersion = supportedVersion;
        this.minimumMetadataVersion = supportedMetadataVersion;
        this.loaderVersionAliases = loaderVersionAliases.clone();
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getModId() {
        return modId;
    }

    public String getMinimumVersion() {
        return minimumVersion;
    }

    public String getMinimumMetadataVersion() {
        return minimumMetadataVersion;
    }

    public boolean meetsMinimumMetadataVersion(String version) {
        int[] detected = normalizedVersion(version);
        int[] minimum = normalizedVersion(minimumMetadataVersion);
        return detected != null && minimum != null && compare(detected, minimum) >= 0;
    }

    /** Loader aliases are trusted only after the early gate confirms minimum release metadata. */
    public boolean acceptsLoaderVersion(String version) {
        if (meetsMinimumMetadataVersion(version)) {
            return true;
        }
        for (String alias : loaderVersionAliases) {
            if (alias.equals(version)) {
                return true;
            }
        }
        return false;
    }

    private int[] normalizedVersion(String version) {
        if (version == null) {
            return null;
        }
        String normalized = version.trim();
        switch (this) {
            case QUALITY_TOOLS:
                int qualitySuffix = normalized.indexOf('_');
                if (qualitySuffix >= 0) normalized = normalized.substring(0, qualitySuffix);
                break;
            case ANCIENT_SPELLCRAFT:
                if (normalized.startsWith("1.12.2-")) normalized = normalized.substring(7);
                break;
            case CRAFTTWEAKER:
                if (normalized.startsWith("1.12-")) normalized = normalized.substring(5);
                break;
            case ARS_MAGICA:
                break;
            default:
                throw new IllegalStateException("Unhandled integration " + this);
        }
        normalized = numericPrefix(normalized);
        if (normalized == null) return null;
        String[] components = normalized.split("\\.");
        if (components.length == 0) return null;
        int[] parsed = new int[components.length];
        for (int i = 0; i < components.length; i++) {
            try {
                parsed[i] = Integer.parseInt(components[i]);
            } catch (NumberFormatException exception) {
                return null;
            }
        }
        return parsed;
    }

    private static String numericPrefix(String version) {
        int end = 0;
        while (end < version.length()) {
            char character = version.charAt(end);
            if (!Character.isDigit(character) && character != '.') break;
            end++;
        }
        if (end == 0 || version.charAt(end - 1) == '.') return null;
        return version.substring(0, end);
    }

    private static int compare(int[] left, int[] right) {
        int length = Math.max(left.length, right.length);
        for (int i = 0; i < length; i++) {
            int leftPart = i < left.length ? left[i] : 0;
            int rightPart = i < right.length ? right[i] : 0;
            if (leftPart != rightPart) return Integer.compare(leftPart, rightPart);
        }
        return 0;
    }
}
