package com.mahghuuuls.jawmsintegrations.mixin;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mahghuuuls.jawmsintegrations.integration.IntegrationId;
import com.mahghuuuls.jawmsintegrations.integration.OptionalMixinGateRegistry;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Field;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/** Reads mod metadata without resolving optional classes during Mixin bootstrap. */
public final class EarlyModMetadataScanner {

    private EarlyModMetadataScanner() {
    }

    public static OptionalMixinGateRegistry.Evidence scan(ClassLoader classLoader,
                                                          IntegrationId integration) {
        return scan(classLoader, integration, findMinecraftHome(classLoader));
    }

    static OptionalMixinGateRegistry.Evidence scan(ClassLoader classLoader,
                                                   IntegrationId integration,
                                                   File minecraftHome) {
        if (classLoader == null) {
            return OptionalMixinGateRegistry.Evidence.error("No class loader was available to inspect mcmod.info");
        }
        Set<String> versions = new LinkedHashSet<>();
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
        scanModsDirectory(minecraftHome, integration.getModId(), versions);
        if (versions.isEmpty()) {
            return OptionalMixinGateRegistry.Evidence.absent();
        }
        if (versions.size() > 1) {
            return OptionalMixinGateRegistry.Evidence.error(
                    "Multiple metadata entries were found for " + integration.getModId() + ": " + versions
            );
        }
        String version = versions.iterator().next();
        return integration.supports(version)
                ? OptionalMixinGateRegistry.Evidence.supported(version)
                : OptionalMixinGateRegistry.Evidence.unsupported(version);
    }

    private static void readMatchingVersions(URL resource,
                                             String targetModId,
                                             Set<String> versions) {
        try (InputStream stream = resource.openStream()) {
            readMatchingVersions(stream, targetModId, versions);
        } catch (IOException | RuntimeException ignored) {
            // Unrelated malformed metadata must not disable a target whose own entry can still be identified.
        }
    }

    private static void readMatchingVersions(InputStream stream,
                                             String targetModId,
                                             Set<String> versions) {
        try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
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

    private static void scanModsDirectory(File minecraftHome,
                                          String targetModId,
                                          Set<String> versions) {
        if (minecraftHome == null) {
            return;
        }
        File modsDirectory = new File(minecraftHome, "mods");
        scanArchiveDirectory(modsDirectory, targetModId, versions);
        scanArchiveDirectory(new File(modsDirectory, "1.12.2"), targetModId, versions);
    }

    private static void scanArchiveDirectory(File directory,
                                             String targetModId,
                                             Set<String> versions) {
        File[] candidates = directory.listFiles(file -> file.isFile()
                && (file.getName().endsWith(".jar") || file.getName().endsWith(".zip")));
        if (candidates == null) {
            return;
        }
        Arrays.sort(candidates, (left, right) -> left.getName().compareTo(right.getName()));
        for (File candidate : candidates) {
            try (JarFile archive = new JarFile(candidate)) {
                JarEntry metadata = archive.getJarEntry("mcmod.info");
                if (metadata != null) {
                    readMatchingVersions(archive.getInputStream(metadata), targetModId, versions);
                }
            } catch (IOException | RuntimeException ignored) {
                // Forge may encounter unrelated non-mod or malformed archives in the same directory.
            }
        }
    }

    private static File findMinecraftHome(ClassLoader classLoader) {
        File home = findMinecraftHomeWith(classLoader);
        return home == null
                ? findMinecraftHomeWith(EarlyModMetadataScanner.class.getClassLoader())
                : home;
    }

    private static File findMinecraftHomeWith(ClassLoader classLoader) {
        if (classLoader == null) {
            return null;
        }
        try {
            Class<?> launch = Class.forName("net.minecraft.launchwrapper.Launch", false, classLoader);
            Field field = launch.getField("minecraftHome");
            Object value = field.get(null);
            return value instanceof File ? (File) value : null;
        } catch (ReflectiveOperationException | LinkageError | SecurityException ignored) {
            return null;
        }
    }
}
