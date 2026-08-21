package com.mahghuuuls.jawmsintegrations.integration;

import java.lang.reflect.Field;

/** Verifies the required released JAWMS mod and API contracts before optional work begins. */
public final class JawmsCompatibility {

    public static final String REQUIRED_MOD_VERSION = "1.0.0";
    public static final String REQUIRED_API_VERSION = "1.5";

    private JawmsCompatibility() {
    }

    public static Status verifyInstalled(String detectedModVersion) {
        if (!meetsMinimum(detectedModVersion, REQUIRED_MOD_VERSION)) {
            throw incompatible("detected mod version " + valueOrMissing(detectedModVersion));
        }
        try {
            Class<?> versionClass = Class.forName(
                    "com.mahghuuuls.jawms.api.ManaApiVersion",
                    false,
                    JawmsCompatibility.class.getClassLoader()
            );
            return verify(detectedModVersion, versionClass);
        } catch (ClassNotFoundException | LinkageError exception) {
            throw incompatible("the API 1.5 version contract is missing or could not be linked", exception);
        }
    }

    public static Status verify(String detectedModVersion, Class<?> apiVersionClass) {
        if (!meetsMinimum(detectedModVersion, REQUIRED_MOD_VERSION)) {
            throw incompatible("detected mod version " + valueOrMissing(detectedModVersion));
        }
        try {
            Field current = apiVersionClass.getField("CURRENT");
            Object value = current.get(null);
            if (!(value instanceof String) || !meetsMinimum((String) value, REQUIRED_API_VERSION)) {
                throw incompatible("detected API version " + valueOrMissing(value));
            }
            return new Status(detectedModVersion, (String) value);
        } catch (ReflectiveOperationException | LinkageError exception) {
            throw incompatible("the API version contract could not be read", exception);
        }
    }

    private static IllegalStateException incompatible(String finding) {
        return incompatible(finding, null);
    }

    private static IllegalStateException incompatible(String finding, Throwable cause) {
        String message = "JAWMS Integrations requires JAWMS " + REQUIRED_MOD_VERSION
                + " or newer with public API " + REQUIRED_API_VERSION + " or newer; " + finding + ".";
        return cause == null ? new IllegalStateException(message) : new IllegalStateException(message, cause);
    }

    private static String valueOrMissing(Object value) {
        return value == null ? "<missing>" : "'" + value + "'";
    }

    private static boolean meetsMinimum(String detected, String minimum) {
        int[] detectedParts = parseNumericVersion(detected);
        int[] minimumParts = parseNumericVersion(minimum);
        if (detectedParts == null || minimumParts == null) return false;
        int length = Math.max(detectedParts.length, minimumParts.length);
        for (int i = 0; i < length; i++) {
            int detectedPart = i < detectedParts.length ? detectedParts[i] : 0;
            int minimumPart = i < minimumParts.length ? minimumParts[i] : 0;
            if (detectedPart != minimumPart) return detectedPart > minimumPart;
        }
        return true;
    }

    private static int[] parseNumericVersion(String version) {
        if (version == null) return null;
        String trimmed = version.trim();
        int end = 0;
        while (end < trimmed.length()) {
            char character = trimmed.charAt(end);
            if (!Character.isDigit(character) && character != '.') break;
            end++;
        }
        if (end == 0 || trimmed.charAt(end - 1) == '.') return null;
        String[] components = trimmed.substring(0, end).split("\\.");
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

    public static final class Status {
        private final String modVersion;
        private final String apiVersion;

        private Status(String modVersion, String apiVersion) {
            this.modVersion = modVersion;
            this.apiVersion = apiVersion;
        }

        public String getModVersion() {
            return modVersion;
        }

        public String getApiVersion() {
            return apiVersion;
        }
    }
}
