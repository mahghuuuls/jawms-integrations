package com.mahghuuuls.jawmsintegrations.integration.qualitytools;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QualityAttributeTranslationsTest {

    @Test
    void everyPublicAttributeHasAReadableTranslation() throws IOException {
        Properties translations = new Properties();
        try (InputStream input = getClass().getResourceAsStream(
                "/assets/jawmsintegrations/lang/en_us.lang")) {
            assertNotNull(input);
            translations.load(input);
        }

        for (QualityAttributeProjection.Attribute attribute : QualityAttributeProjection.Attribute.values()) {
            String key = "attribute.name." + attribute.getPublicName();
            assertTrue(translations.containsKey(key), "Missing translation for " + key);
            assertTrue(!translations.getProperty(key).contains("jawmsintegrations."));
        }
        assertEquals(15, translations.stringPropertyNames().stream()
                .filter(key -> key.startsWith("attribute.name.")).count());
        assertEquals("Maximum Mana",
                translations.getProperty("attribute.name.jawmsintegrations.max_mana_percent"));
    }
}
