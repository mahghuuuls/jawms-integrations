package com.mahghuuuls.jawmsintegrations.integration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JawmsCompatibilityTest {

    @Test
    void acceptsReleasedModAndApiVersions() {
        JawmsCompatibility.Status status = JawmsCompatibility.verify("0.4.0", CompatibleApi.class);

        assertEquals("0.4.0", status.getModVersion());
        assertEquals("1.4", status.getApiVersion());
    }

    @Test
    void rejectsMissingOrIncompatibleContractsWithRequiredVersionsInMessage() {
        IllegalStateException missing = assertThrows(IllegalStateException.class,
                () -> JawmsCompatibility.verify(null, CompatibleApi.class));
        IllegalStateException incompatible = assertThrows(IllegalStateException.class,
                () -> JawmsCompatibility.verify("0.4.0", IncompatibleApi.class));

        assertTrue(missing.getMessage().contains("JAWMS 0.4.0"));
        assertTrue(missing.getMessage().contains("API 1.4"));
        assertTrue(incompatible.getMessage().contains("'1.3'"));
    }

    public static final class CompatibleApi {
        public static final String CURRENT = "1.4";
    }

    public static final class IncompatibleApi {
        public static final String CURRENT = "1.3";
    }
}
