package com.mahghuuuls.jawmsintegrations.mixin;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mahghuuuls.jawmsintegrations.integration.IntegrationId;
import com.mahghuuuls.jawmsintegrations.integration.OptionalMixinGateRegistry;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/** Reads mod metadata without resolving optional classes during Mixin bootstrap. */
public final class EarlyModMetadataScanner {

    private EarlyModMetadataScanner() {
    }

    public static OptionalMixinGateRegistry.Evidence scan(ClassLoader classLoader,
                                                          IntegrationId integration) {
        if (classLoader == null) {
            return OptionalMixinGateRegistry.Evidence.error("No class loader was available to inspect mcmod.info");
        }
        List<String> versions = new ArrayList<>();
        try {
            Enumeration<URL> resources = classLoader.getResources("mcmod.info");
            while (resources.hasMoreElements()) {
                readMatchingVersions(resources.nextElement(), integration.getModId(), versions);
            }
        } catch (IOException exception) {
            return OptionalMixinGateRegistry.Evidence.error(
                    "Could not inspect early mod metadata: " + exception.getClass().getSimpleName()
            );
        }
        if (versions.isEmpty()) {
            return OptionalMixinGateRegistry.Evidence.absent();
        }
        if (versions.size() > 1) {
            return OptionalMixinGateRegistry.Evidence.error(
                    "Multiple metadata entries were found for " + integration.getModId() + ": " + versions
            );
        }
        String version = versions.get(0);
        return integration.supports(version)
                ? OptionalMixinGateRegistry.Evidence.supported(version)
                : OptionalMixinGateRegistry.Evidence.unsupported(version);
    }

    private static void readMatchingVersions(URL resource,
                                             String targetModId,
                                             List<String> versions) {
        try (InputStream stream = resource.openStream();
             Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            JsonElement root = new JsonParser().parse(reader);
            if (!root.isJsonArray()) {
                return;
            }
            for (JsonElement element : root.getAsJsonArray()) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject object = element.getAsJsonObject();
                if (!object.has("modid") || !object.has("version")) {
                    continue;
                }
                if (targetModId.equals(object.get("modid").getAsString())) {
                    versions.add(object.get("version").getAsString());
                }
            }
        } catch (IOException | RuntimeException ignored) {
            // Unrelated malformed metadata must not disable a target whose own entry can still be identified.
        }
    }
}
