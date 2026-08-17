package com.mahghuuuls.jawmsintegrations.client;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;

class AncientTooltipTranslationsTest {

    @Test
    void everfullPresentationUsesSeamlessManaTerminology() throws IOException {
        Properties translations = new Properties();
        try (InputStream input = getClass().getResourceAsStream(
                "/assets/jawmsintegrations/lang/en_us.lang")) {
            assertNotNull(input);
            translations.load(input);
        }

        String charge = translations.getProperty("tooltip.jawmsintegrations.everfull.charge");
        String syncing = translations.getProperty("tooltip.jawmsintegrations.everfull.syncing");
        assertEquals("Stored Mana: %s / %s", charge);
        assertEquals("Stored Mana: awaiting server state", syncing);
        assertFalse(charge.contains("JAWMS"));
        assertFalse(syncing.contains("JAWMS"));
    }
}
