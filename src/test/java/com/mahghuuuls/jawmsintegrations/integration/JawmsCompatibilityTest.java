package com.mahghuuuls.jawmsintegrations.integration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JawmsCompatibilityTest {

    @Test
    void acceptsReleasedModAndApiVersions() {
        JawmsCompatibility.Status status = JawmsCompatibility.verify("1.1.0", CompatibleApi.class);

        assertEquals("1.1.0", status.getModVersion());
        assertEquals("1.6", status.getApiVersion());
    }

    @Test
    void acceptsNewerModAndApiVersionsWithoutAnUpperBound() {
        JawmsCompatibility.Status status = JawmsCompatibility.verify("2.0.0-beta", NewerApi.class);

        assertEquals("2.0.0-beta", status.getModVersion());
        assertEquals("2.0", status.getApiVersion());
    }

    @Test
    void rejectsMissingOrIncompatibleContractsWithRequiredVersionsInMessage() {
        IllegalStateException missing = assertThrows(IllegalStateException.class,
                () -> JawmsCompatibility.verify(null, CompatibleApi.class));
        IllegalStateException oldMod = assertThrows(IllegalStateException.class,
                () -> JawmsCompatibility.verify("1.0.0", CompatibleApi.class));
        IllegalStateException oldApi = assertThrows(IllegalStateException.class,
                () -> JawmsCompatibility.verify("1.1.0", IncompatibleApi.class));

        assertTrue(missing.getMessage().contains("JAWMS 1.1.0"));
        assertTrue(missing.getMessage().contains("API 1.6"));
        assertTrue(oldMod.getMessage().contains("'1.0.0'"));
        assertTrue(oldApi.getMessage().contains("'1.5'"));
    }

    public static final class CompatibleApi {
        public static final String CURRENT = "1.6";
    }

    public static final class IncompatibleApi {
        public static final String CURRENT = "1.5";
    }

    public static final class NewerApi {
        public static final String CURRENT = "2.0";
    }
}
