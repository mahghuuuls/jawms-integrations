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
import java.util.concurrent.atomic.AtomicInteger;
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

    static void assertMethodInvocation(Path jarPath,
                                       String internalClassName,
                                       String methodName,
                                       String descriptor,
                                       String invokedOwner,
                                       String invokedName,
                                       String invokedDescriptor) throws IOException {
        String entryName = internalClassName + ".class";
        AtomicBoolean found = new AtomicBoolean(false);
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            JarEntry entry = jar.getJarEntry(entryName);
            if (entry == null) {
                throw new AssertionError("Missing class " + internalClassName + " in " + jarPath);
            }
            try (InputStream input = jar.getInputStream(entry)) {
                new ClassReader(input).accept(new ClassVisitor(Opcodes.ASM5) {
                    @Override
                    public MethodVisitor visitMethod(int access, String name, String methodDescriptor,
                                                     String signature, String[] exceptions) {
                        if (!methodName.equals(name) || !descriptor.equals(methodDescriptor)) {
                            return null;
                        }
                        return new MethodVisitor(Opcodes.ASM5) {
                            @Override
                            public void visitMethodInsn(int opcode, String owner, String name,
                                                        String descriptor, boolean isInterface) {
                                if (invokedOwner.equals(owner) && invokedName.equals(name)
                                        && invokedDescriptor.equals(descriptor)) {
                                    found.set(true);
                                }
                            }
                        };
                    }
                }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            }
        }
        if (!found.get()) {
            throw new AssertionError("Missing invocation " + invokedOwner + "." + invokedName
                    + invokedDescriptor + " in " + internalClassName + "." + methodName + descriptor);
        }
    }

    static void assertMethodInvocationCount(Path jarPath,
                                            String internalClassName,
                                            String methodName,
                                            String descriptor,
                                            String invokedOwner,
                                            String invokedName,
                                            String invokedDescriptor,
                                            int expectedCount) throws IOException {
        String entryName = internalClassName + ".class";
        AtomicInteger count = new AtomicInteger();
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            JarEntry entry = jar.getJarEntry(entryName);
            if (entry == null) {
                throw new AssertionError("Missing class " + internalClassName + " in " + jarPath);
            }
            try (InputStream input = jar.getInputStream(entry)) {
                new ClassReader(input).accept(new ClassVisitor(Opcodes.ASM5) {
                    @Override
                    public MethodVisitor visitMethod(int access, String name, String methodDescriptor,
                                                     String signature, String[] exceptions) {
                        if (!methodName.equals(name) || !descriptor.equals(methodDescriptor)) {
                            return null;
                        }
                        return new MethodVisitor(Opcodes.ASM5) {
                            @Override
                            public void visitMethodInsn(int opcode, String owner, String name,
                                                        String descriptor, boolean isInterface) {
                                if (invokedOwner.equals(owner) && invokedName.equals(name)
                                        && invokedDescriptor.equals(descriptor)) {
                                    count.incrementAndGet();
                                }
                            }
                        };
                    }
                }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            }
        }
        if (count.get() != expectedCount) {
            throw new AssertionError("Expected " + expectedCount + " invocation(s) of "
                    + invokedOwner + "." + invokedName + invokedDescriptor + " in "
                    + internalClassName + "." + methodName + descriptor + " but found " + count.get());
        }
    }

    static void assertFieldAccessCount(Path jarPath,
                                       String internalClassName,
                                       String methodName,
                                       String descriptor,
                                       String fieldOwner,
                                       String fieldName,
                                       String fieldDescriptor,
                                       int expectedCount) throws IOException {
        String entryName = internalClassName + ".class";
        AtomicInteger count = new AtomicInteger();
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            JarEntry entry = jar.getJarEntry(entryName);
            if (entry == null) {
                throw new AssertionError("Missing class " + internalClassName + " in " + jarPath);
            }
            try (InputStream input = jar.getInputStream(entry)) {
                new ClassReader(input).accept(new ClassVisitor(Opcodes.ASM5) {
                    @Override
                    public MethodVisitor visitMethod(int access, String name, String methodDescriptor,
                                                     String signature, String[] exceptions) {
                        if (!methodName.equals(name) || !descriptor.equals(methodDescriptor)) {
                            return null;
                        }
                        return new MethodVisitor(Opcodes.ASM5) {
                            @Override
                            public void visitFieldInsn(int opcode, String owner, String name,
                                                       String descriptor) {
                                if (fieldOwner.equals(owner) && fieldName.equals(name)
                                        && fieldDescriptor.equals(descriptor)) {
                                    count.incrementAndGet();
                                }
                            }
                        };
                    }
                }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            }
        }
        if (count.get() != expectedCount) {
            throw new AssertionError("Expected " + expectedCount + " access(es) of "
                    + fieldOwner + "." + fieldName + fieldDescriptor + " in "
                    + internalClassName + "." + methodName + descriptor + " but found " + count.get());
        }
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
