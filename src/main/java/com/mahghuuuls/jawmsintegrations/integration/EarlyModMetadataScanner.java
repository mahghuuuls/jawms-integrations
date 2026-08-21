package com.mahghuuuls.jawmsintegrations.integration;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.ByteArrayOutputStream;
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
import java.util.Locale;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/** Acquires optional-mod metadata without resolving optional implementation classes. */
public final class EarlyModMetadataScanner {

    private EarlyModMetadataScanner() {
    }

    public static OptionalIntegrationEvidenceRegistry.Evidence scan(
            ClassLoader classLoader, IntegrationId integration) {
        return scan(classLoader, integration, findMinecraftHome(classLoader));
    }

    /** Reads class bytes from the early classpath or Forge mods directories without defining it. */
    public static byte[] readEarlyClassBytes(ClassLoader classLoader, String internalName)
            throws IOException {
        if (classLoader == null || internalName == null || internalName.trim().isEmpty()) {
            return null;
        }
        String resourceName = internalName + ".class";
        try (InputStream stream = classLoader.getResourceAsStream(resourceName)) {
            if (stream != null) return readAll(stream);
        }
        File minecraftHome = findMinecraftHome(classLoader);
        if (minecraftHome == null) return null;
        File modsDirectory = new File(minecraftHome, "mods");
        byte[] bytes = readClassFromArchiveDirectory(modsDirectory, resourceName);
        return bytes == null
                ? readClassFromArchiveDirectory(new File(modsDirectory, "1.12.2"), resourceName)
                : bytes;
    }

    public static OptionalIntegrationEvidenceRegistry.Evidence scan(
            ClassLoader classLoader, IntegrationId integration, File minecraftHome) {
        if (classLoader == null || integration == null) {
            return OptionalIntegrationEvidenceRegistry.Evidence.error(
                    "A class loader and integration are required to inspect mcmod.info");
        }
        Set<String> versions = new LinkedHashSet<>();
        try {
            Enumeration<URL> resources = classLoader.getResources("mcmod.info");
            while (resources.hasMoreElements()) {
                readMatchingVersions(resources.nextElement(), integration.getModId(), versions);
            }
        } catch (IOException exception) {
            return OptionalIntegrationEvidenceRegistry.Evidence.error(
                    "Could not inspect early mod metadata: " + exception.getClass().getSimpleName());
        }
        scanModsDirectory(minecraftHome, integration.getModId(), versions);
        if (versions.isEmpty()) {
            return OptionalIntegrationEvidenceRegistry.Evidence.absent();
        }
        if (versions.size() > 1) {
            return OptionalIntegrationEvidenceRegistry.Evidence.error(
                    "Multiple metadata entries were found for " + integration.getModId() + ": " + versions);
        }
        String version = versions.iterator().next();
        return integration.meetsMinimumMetadataVersion(version)
                ? OptionalIntegrationEvidenceRegistry.Evidence.supported(version)
                : OptionalIntegrationEvidenceRegistry.Evidence.unsupported(version);
    }

    private static void readMatchingVersions(URL resource, String targetModId, Set<String> versions) {
        try (InputStream stream = resource.openStream()) {
            readMatchingVersions(stream, targetModId, versions);
        } catch (IOException | RuntimeException ignored) {
            // Unrelated malformed metadata must not hide a valid target entry.
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
                if (object.has("modid") && object.has("version")
                        && targetModId.equals(object.get("modid").getAsString())) {
                    versions.add(object.get("version").getAsString());
                }
            }
        } catch (IOException | RuntimeException ignored) {
            // Unrelated malformed metadata must not hide a valid target entry.
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
        File[] candidates = directory.listFiles(file -> {
            String name = file.getName().toLowerCase(Locale.ROOT);
            return file.isFile() && (name.endsWith(".jar") || name.endsWith(".zip"));
        });
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
                // Forge may encounter unrelated malformed archives in the same directory.
            }
        }
    }

    private static byte[] readClassFromArchiveDirectory(File directory, String resourceName) {
        File[] candidates = archiveCandidates(directory);
        if (candidates == null) return null;
        for (File candidate : candidates) {
            try (JarFile archive = new JarFile(candidate)) {
                JarEntry entry = archive.getJarEntry(resourceName);
                if (entry != null) {
                    try (InputStream stream = archive.getInputStream(entry)) {
                        return readAll(stream);
                    }
                }
            } catch (IOException | RuntimeException ignored) {
                // An unrelated malformed archive must not prevent another candidate from matching.
            }
        }
        return null;
    }

    private static File[] archiveCandidates(File directory) {
        File[] candidates = directory.listFiles(file -> {
            String name = file.getName().toLowerCase(Locale.ROOT);
            return file.isFile() && (name.endsWith(".jar") || name.endsWith(".zip"));
        });
        if (candidates != null) {
            Arrays.sort(candidates, (left, right) -> left.getName().compareTo(right.getName()));
        }
        return candidates;
    }

    private static byte[] readAll(InputStream stream) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = stream.read(buffer)) >= 0) output.write(buffer, 0, read);
        return output.toByteArray();
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
