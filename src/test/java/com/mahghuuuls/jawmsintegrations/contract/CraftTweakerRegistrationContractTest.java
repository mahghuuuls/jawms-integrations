package com.mahghuuuls.jawmsintegrations.contract;

import crafttweaker.annotations.ZenRegister;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import stanhebben.zenscript.annotations.ZenClass;
import stanhebben.zenscript.annotations.ZenGetter;
import stanhebben.zenscript.annotations.ZenMethod;

import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CraftTweakerRegistrationContractTest {

    private static final String ROOT = "com/mahghuuuls/jawmsintegrations/";

    @Test
    void facadeAndImmutableTypesExposeExactStableZenSurface() throws Exception {
        Class<?> facade = com.mahghuuuls.jawmsintegrations.integration.crafttweaker.zen.Mana.class;
        Class<?> state = com.mahghuuuls.jawmsintegrations.integration.crafttweaker.zen.ManaState.class;
        Class<?> result =
                com.mahghuuuls.jawmsintegrations.integration.crafttweaker.zen.ManaMutationResult.class;

        assertZenClass(facade, "mods.jawmsintegrations.Mana");
        assertZenClass(state, "mods.jawmsintegrations.ManaState");
        assertZenClass(result, "mods.jawmsintegrations.ManaMutationResult");
        assertFalse(facade.isAnnotationPresent(ZenRegister.class));
        assertFalse(state.isAnnotationPresent(ZenRegister.class));
        assertFalse(result.isAnnotationPresent(ZenRegister.class));

        Set<String> facadeMethods = new HashSet<>();
        for (Method method : facade.getDeclaredMethods()) {
            if (method.isAnnotationPresent(ZenMethod.class)) {
                assertTrue(Modifier.isStatic(method.getModifiers()));
                facadeMethods.add(method.getName());
            }
        }
        assertEquals(new HashSet<>(Arrays.asList("getState", "setCurrentMana", "restoreMana",
                "consumeMana", "drainMana", "startRegenerationLockout")), facadeMethods);

        assertMethod(facade, "getState", state, crafttweaker.api.player.IPlayer.class);
        assertMethod(facade, "setCurrentMana", result,
                crafttweaker.api.player.IPlayer.class, int.class);
        assertMethod(facade, "restoreMana", result,
                crafttweaker.api.player.IPlayer.class, int.class);
        assertMethod(facade, "consumeMana", result,
                crafttweaker.api.player.IPlayer.class, int.class);
        assertMethod(facade, "drainMana", result,
                crafttweaker.api.player.IPlayer.class, int.class);
        assertMethod(facade, "startRegenerationLockout", long.class,
                crafttweaker.api.player.IPlayer.class);

        assertGetters(state, "currentMana", "maximumMana", "effectiveRegeneration",
                "effectiveLockoutTicks", "remainingLockoutTicks", "effectiveLockoutSeconds",
                "remainingLockoutSeconds", "canRegenerateDuringPostCastLockout",
                "canRegenerateDuringContinuousCasting", "continuousCastActive",
                "regenerationEligible");
        assertGetters(result, "successful", "failure", "requestedAmount", "actualDelta",
                "actualAmount", "oldState", "newState");
        assertImmutable(state);
        assertImmutable(result);
    }

    @Test
    void registrarMakesExactlyThreeProgrammaticRegistrations() throws Exception {
        int[] calls = {0};
        List<String> registered = new ArrayList<>();
        visit("com/mahghuuuls/jawmsintegrations/integration/crafttweaker/zen/"
                + "CraftTweakerRegistrar.class", new ClassVisitor(Opcodes.ASM5) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                if (!"register".equals(name) || !"()V".equals(descriptor)) return null;
                return new MethodVisitor(Opcodes.ASM5) {
                    private Type lastType;

                    @Override
                    public void visitLdcInsn(Object value) {
                        lastType = value instanceof Type ? (Type) value : null;
                    }

                    @Override
                    public void visitMethodInsn(int opcode, String owner, String name,
                                                String descriptor, boolean isInterface) {
                        if (opcode == Opcodes.INVOKESTATIC
                                && "crafttweaker/CraftTweakerAPI".equals(owner)
                                && "registerClass".equals(name)
                                && "(Ljava/lang/Class;)V".equals(descriptor)) {
                            calls[0]++;
                            registered.add(lastType == null ? "<missing>" : lastType.getClassName());
                        }
                    }
                };
            }
        });
        assertEquals(3, calls[0]);
        assertEquals(Arrays.asList(
                "com.mahghuuuls.jawmsintegrations.integration.crafttweaker.zen.ManaState",
                "com.mahghuuuls.jawmsintegrations.integration.crafttweaker.zen.ManaMutationResult",
                "com.mahghuuuls.jawmsintegrations.integration.crafttweaker.zen.Mana"), registered);
    }

    @Test
    void productionServerValidatorPinsSideThreadAndOnlineIdentityChecks() throws Exception {
        Map<String, Integer> calls = new LinkedHashMap<>();
        int[] playerTypeChecks = {0};
        int[] remoteReads = {0};
        visit("com/mahghuuuls/jawmsintegrations/integration/crafttweaker/"
                + "CraftTweakerManaService$ServerPlayerValidator.class",
                new ClassVisitor(Opcodes.ASM5) {
                    @Override
                    public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                     String signature, String[] exceptions) {
                        if (!"validate".equals(name)) return null;
                        return new MethodVisitor(Opcodes.ASM5) {
                            @Override
                            public void visitTypeInsn(int opcode, String type) {
                                if (opcode == Opcodes.INSTANCEOF
                                        && "net/minecraft/entity/player/EntityPlayerMP".equals(type)) {
                                    playerTypeChecks[0]++;
                                }
                            }

                            @Override
                            public void visitFieldInsn(int opcode, String owner, String name,
                                                       String descriptor) {
                                if (opcode == Opcodes.GETFIELD
                                        && "net/minecraft/world/World".equals(owner)
                                        && "isRemote".equals(name) && "Z".equals(descriptor)) {
                                    remoteReads[0]++;
                                }
                            }

                            @Override
                            public void visitMethodInsn(int opcode, String owner, String name,
                                                        String descriptor, boolean isInterface) {
                                String key = owner + "." + name + descriptor;
                                calls.put(key, calls.getOrDefault(key, 0) + 1);
                            }
                        };
                    }
                });
        assertEquals(1, playerTypeChecks[0]);
        assertEquals(1, remoteReads[0]);
        assertEquals(Integer.valueOf(1), calls.get("net/minecraft/entity/player/EntityPlayerMP."
                + "getServer()Lnet/minecraft/server/MinecraftServer;"));
        assertEquals(Integer.valueOf(1), calls.get("net/minecraft/server/MinecraftServer."
                + "isCallingFromMinecraftThread()Z"));
        assertEquals(Integer.valueOf(1), calls.get("net/minecraft/server/MinecraftServer."
                + "getPlayerList()Lnet/minecraft/server/management/PlayerList;"));
        assertEquals(Integer.valueOf(1), calls.get("net/minecraft/server/management/PlayerList."
                + "getPlayerByUUID(Ljava/util/UUID;)Lnet/minecraft/entity/player/EntityPlayerMP;"));
    }

    @Test
    void preInitOwnsOneReflectiveRegistrationBoundary() throws Exception {
        int[] calls = {0};
        visit("com/mahghuuuls/jawmsintegrations/JawmsIntegrationsMod.class",
                new ClassVisitor(Opcodes.ASM5) {
                    @Override
                    public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                     String signature, String[] exceptions) {
                        if (!"preInit".equals(name)) return null;
                        return new MethodVisitor(Opcodes.ASM5) {
                            @Override
                            public void visitMethodInsn(int opcode, String owner, String name,
                                                        String descriptor, boolean isInterface) {
                                if (ROOT.concat("JawmsIntegrationsMod").equals(owner)
                                        && "activateCraftTweaker".equals(name)) calls[0]++;
                            }
                        };
                    }
                });
        assertEquals(1, calls[0]);
    }

    @Test
    void dependencyFreeCraftTweakerCoreDoesNotLinkOptionalPackages() throws Exception {
        Path classes = Paths.get(System.getProperty("jawmsintegrations.mainClassesDir"));
        Path root = classes.resolve(ROOT).resolve("integration/crafttweaker");
        List<String> violations = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(path -> path.toString().endsWith(".class"))
                    .filter(path -> !path.toString().replace('\\', '/').contains("/zen/"))
                    .forEach(path -> inspectOptionalReferences(path, violations));
        }
        assertTrue(violations.isEmpty(), "Optional references escaped Zen boundary: " + violations);
    }

    @Test
    void compileOnlyZenScriptExercisesEveryPublishedMethodAndProperty() throws Exception {
        Path script = Paths.get("src/test/resources/validation/crafttweaker/"
                + "jawmsintegrations_api_contract.zs");
        String source = new String(Files.readAllBytes(script), StandardCharsets.UTF_8);

        assertTrue(source.contains("import crafttweaker.player.IPlayer;"));
        assertTrue(source.contains("import mods.jawmsintegrations.Mana;"));
        assertTrue(source.contains("function jawmsIntegrationsApiContract(player as IPlayer)"));
        for (String call : Arrays.asList("Mana.getState(player)",
                "Mana.setCurrentMana(player, 0)", "Mana.restoreMana(player, 0)",
                "Mana.consumeMana(player, 0)", "Mana.drainMana(player, 0)",
                "Mana.startRegenerationLockout(player)")) {
            assertTrue(source.contains(call), "Missing validation call " + call);
        }
        for (String property : Arrays.asList("currentMana", "maximumMana",
                "effectiveRegeneration", "effectiveLockoutTicks", "remainingLockoutTicks",
                "effectiveLockoutSeconds", "remainingLockoutSeconds",
                "canRegenerateDuringPostCastLockout",
                "canRegenerateDuringContinuousCasting", "continuousCastActive",
                "regenerationEligible", "successful", "failure", "requestedAmount",
                "actualDelta", "actualAmount", "oldState", "newState")) {
            assertTrue(source.contains("." + property),
                    "Missing validation property " + property);
        }

        String gradle = new String(Files.readAllBytes(Paths.get(
                "gradle/scripts/dependencies.gradle")), StandardCharsets.UTF_8);
        assertTrue(gradle.contains("prepareCraftTweakerDevelopmentValidationScript"));
        assertTrue(gradle.contains("run/scripts/jawmsintegrations-validation"));
        assertTrue(gradle.contains("prepareCraftTweakerPackagedValidationScript"));
        assertTrue(gradle.contains("run/obfuscated/scripts/jawmsintegrations-validation"));
    }

    private static void assertZenClass(Class<?> type, String expected) {
        ZenClass annotation = type.getAnnotation(ZenClass.class);
        assertTrue(annotation != null, "Missing @ZenClass on " + type.getName());
        assertEquals(expected, annotation.value());
    }

    private static void assertMethod(Class<?> owner, String name, Class<?> returnType,
                                     Class<?>... parameterTypes) throws Exception {
        Method method = owner.getDeclaredMethod(name, parameterTypes);
        assertEquals(returnType, method.getReturnType());
        assertTrue(Modifier.isStatic(method.getModifiers()));
        assertTrue(method.isAnnotationPresent(ZenMethod.class));
    }

    private static void assertGetters(Class<?> type, String... expected) {
        Set<String> actual = new HashSet<>();
        for (Method method : type.getDeclaredMethods()) {
            ZenGetter getter = method.getAnnotation(ZenGetter.class);
            if (getter != null) actual.add(getter.value());
        }
        assertEquals(new HashSet<>(Arrays.asList(expected)), actual);
    }

    private static void assertImmutable(Class<?> type) {
        for (Field field : type.getDeclaredFields()) {
            assertTrue(Modifier.isPrivate(field.getModifiers()));
            assertTrue(Modifier.isFinal(field.getModifiers()));
        }
        for (Constructor<?> constructor : type.getDeclaredConstructors()) {
            assertFalse(Modifier.isPublic(constructor.getModifiers()));
        }
    }

    private static void visit(String resource, ClassVisitor visitor) throws Exception {
        try (InputStream input = CraftTweakerRegistrationContractTest.class
                .getClassLoader().getResourceAsStream(resource)) {
            assertTrue(input != null, "Missing compiled class " + resource);
            new ClassReader(input).accept(visitor, 0);
        }
    }

    private static void inspectOptionalReferences(Path path, List<String> violations) {
        try {
            String constants = new String(Files.readAllBytes(path), StandardCharsets.ISO_8859_1);
            if (constants.contains("crafttweaker/CraftTweakerAPI")
                    || constants.contains("crafttweaker/api/")
                    || constants.contains("crafttweaker/annotations/")
                    || constants.contains("crafttweaker/runtime/")
                    || constants.contains("crafttweaker/mc1120/")
                    || constants.contains("stanhebben/zenscript/")) {
                violations.add(path.getFileName().toString());
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Could not inspect " + path, exception);
        }
    }
}
