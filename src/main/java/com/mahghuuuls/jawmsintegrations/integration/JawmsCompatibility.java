package com.mahghuuuls.jawmsintegrations.integration;

import java.lang.reflect.Field;

/** Verifies the required released JAWMS mod and API contracts before optional work begins. */
public final class JawmsCompatibility {

    public static final String REQUIRED_MOD_VERSION = "0.4.0";
    public static final String REQUIRED_API_VERSION = "1.4";

    private JawmsCompatibility() {
    }

    public static Status verifyInstalled(String detectedModVersion) {
        if (!REQUIRED_MOD_VERSION.equals(detectedModVersion)) {
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
            throw incompatible("the API 1.4 version contract is missing or could not be linked", exception);
        }
    }

    public static Status verify(String detectedModVersion, Class<?> apiVersionClass) {
        if (!REQUIRED_MOD_VERSION.equals(detectedModVersion)) {
            throw incompatible("detected mod version " + valueOrMissing(detectedModVersion));
        }
        try {
            Field current = apiVersionClass.getField("CURRENT");
            Object value = current.get(null);
            if (!(value instanceof String) || !REQUIRED_API_VERSION.equals(value)) {
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
        String message = "JAWMS Integrations requires released JAWMS " + REQUIRED_MOD_VERSION
                + " with public API " + REQUIRED_API_VERSION + "; " + finding + ".";
        return cause == null ? new IllegalStateException(message) : new IllegalStateException(message, cause);
    }

    private static String valueOrMissing(Object value) {
        return value == null ? "<missing>" : "'" + value + "'";
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
