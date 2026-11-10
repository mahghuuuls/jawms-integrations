package com.mahghuuuls.jawmsintegrations.integration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JawmsCompatibilityTest {

    @Test
    void acceptsMinimumModAndCoreApiVersions() {
        JawmsCompatibility.Status status = JawmsCompatibility.verify("1.5.0", CompatibleApi.class);

        assertEquals("1.5.0", status.getModVersion());
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
                () -> JawmsCompatibility.verifyInstalled(null));
        IllegalStateException oldMod = assertThrows(IllegalStateException.class,
                () -> JawmsCompatibility.verifyInstalled("1.4.1"));
        IllegalStateException oldApi = assertThrows(IllegalStateException.class,
                () -> JawmsCompatibility.verify("1.5.0", IncompatibleApi.class));

        assertTrue(missing.getMessage().contains("JAWMS 1.5.0"));
        assertTrue(missing.getMessage().contains("API 1.6"));
        assertTrue(oldMod.getMessage().contains("'1.4.1'"));
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
