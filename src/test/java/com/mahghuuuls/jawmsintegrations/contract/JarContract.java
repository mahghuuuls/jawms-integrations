package com.mahghuuuls.jawmsintegrations.contract;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

final class JarContract {

    private JarContract() {
    }

    static void assertMethod(Path jarPath, String internalClassName,
                             String methodName, String descriptor) throws IOException {
        assertMember(jarPath, internalClassName, methodName, descriptor, true);
    }

    static void assertField(Path jarPath, String internalClassName,
                            String fieldName, String descriptor) throws IOException {
        assertMember(jarPath, internalClassName, fieldName, descriptor, false);
    }

    private static void assertMember(Path jarPath, String internalClassName,
                                     String memberName, String descriptor, boolean method) throws IOException {
        String entryName = internalClassName + ".class";
        AtomicBoolean found = new AtomicBoolean(false);

        try (JarFile jar = new JarFile(jarPath.toFile())) {
            JarEntry entry = jar.getJarEntry(entryName);
            if (entry == null) {
                throw new AssertionError("Missing class " + internalClassName + " in " + jarPath);
            }
            try (InputStream input = jar.getInputStream(entry)) {
                ClassReader reader = new ClassReader(input);
                reader.accept(new ClassVisitor(Opcodes.ASM5) {
                    @Override
                    public MethodVisitor visitMethod(int access, String name, String methodDescriptor,
                                                     String signature, String[] exceptions) {
                        if (method && memberName.equals(name) && descriptor.equals(methodDescriptor)) {
                            found.set(true);
                        }
                        return null;
                    }

                    @Override
                    public FieldVisitor visitField(int access, String name, String fieldDescriptor,
                                                   String signature, Object value) {
                        if (!method && memberName.equals(name) && descriptor.equals(fieldDescriptor)) {
                            found.set(true);
                        }
                        return null;
                    }
                }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            }
        }

        if (!found.get()) {
            throw new AssertionError("Missing " + (method ? "method " : "field ")
                    + internalClassName + "." + memberName
                    + descriptor + " in " + jarPath);
        }
    }
}
