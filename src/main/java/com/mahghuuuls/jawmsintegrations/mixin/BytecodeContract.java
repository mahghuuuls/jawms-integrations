package com.mahghuuuls.jawmsintegrations.mixin;

import com.mahghuuuls.jawmsintegrations.integration.EarlyModMetadataScanner;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;

/** Generic no-load bytecode assertions used by integration-owned Mixin contracts. */
public final class BytecodeContract {
    private BytecodeContract() {
    }

    public static ClassNode read(ClassLoader loader, String internalName)
            throws IOException, Violation {
        byte[] bytes = EarlyModMetadataScanner.readEarlyClassBytes(loader, internalName);
        if (bytes == null) throw new Violation("missing class " + internalName.replace('/', '.'));
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return node;
    }

    public static MethodNode requireMethod(ClassNode owner, String name, String descriptor)
            throws Violation {
        return requireMethod(owner, new String[]{name}, descriptor);
    }

    public static MethodNode requireMethod(ClassNode owner, String[] names, String descriptor)
            throws Violation {
        for (MethodNode method : owner.methods) {
            if (matches(method.name, names) && descriptor.equals(method.desc)) return method;
        }
        throw new Violation("missing method " + owner.name.replace('/', '.')
                + "." + join(names) + descriptor);
    }

    public static void requireInvocationCount(MethodNode method, String owner, String[] names,
                                              String descriptor, int opcode, int expected)
            throws Violation {
        int count = 0;
        for (AbstractInsnNode instruction = method.instructions.getFirst();
             instruction != null; instruction = instruction.getNext()) {
            if (instruction instanceof MethodInsnNode) {
                MethodInsnNode invocation = (MethodInsnNode) instruction;
                if (opcode == invocation.getOpcode() && owner.equals(invocation.owner)
                        && matches(invocation.name, names) && descriptor.equals(invocation.desc)) count++;
            }
        }
        if (count != expected) {
            throw new Violation("expected " + expected + " invocation(s) of "
                    + owner.replace('/', '.') + "." + join(names) + descriptor
                    + " in " + method.name + method.desc + " but found " + count);
        }
    }

    public static void requireFieldCount(MethodNode method, String owner, String name,
                                         String descriptor, int opcode, int expected)
            throws Violation {
        int count = 0;
        for (AbstractInsnNode instruction = method.instructions.getFirst();
             instruction != null; instruction = instruction.getNext()) {
            if (instruction instanceof FieldInsnNode) {
                FieldInsnNode access = (FieldInsnNode) instruction;
                if (opcode == access.getOpcode() && owner.equals(access.owner)
                        && name.equals(access.name) && descriptor.equals(access.desc)) count++;
            }
        }
        if (count != expected) {
            throw new Violation("expected " + expected + " access(es) to "
                    + owner.replace('/', '.') + "." + name + ":" + descriptor
                    + " in " + method.name + method.desc + " but found " + count);
        }
    }

    private static boolean matches(String value, String[] accepted) {
        for (String candidate : accepted) if (candidate.equals(value)) return true;
        return false;
    }

    private static String join(String[] values) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) result.append('/');
            result.append(values[i]);
        }
        return result.toString();
    }

    public static final class Violation extends Exception {
        public Violation(String message) {
            super(message);
        }
    }
}
