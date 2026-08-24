package com.mahghuuuls.jawmsintegrations.contract;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArsMixinConfigurationTest {

    @Test
    void configContainsOnlyTheContractScopedPaymentMixin() throws IOException {
        String resource = "mixins.jawmsintegrations.arsmagica.json";
        String json;
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertTrue(input != null, "Missing " + resource);
            json = new String(readAll(input), StandardCharsets.UTF_8);
        }
        assertEquals(1, occurrences(json, "MixinEBWizardryCompatHandler"));
        assertEquals(1, occurrences(json, "\"Mixin"));
    }

    private static int occurrences(String text, String needle) {
        int count = 0;
        int offset = 0;
        while ((offset = text.indexOf(needle, offset)) >= 0) {
            count++;
            offset += needle.length();
        }
        return count;
    }

    private static byte[] readAll(InputStream input) throws IOException {
        byte[] buffer = new byte[1024];
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        int read;
        while ((read = input.read(buffer)) >= 0) output.write(buffer, 0, read);
        return output.toByteArray();
    }
}
