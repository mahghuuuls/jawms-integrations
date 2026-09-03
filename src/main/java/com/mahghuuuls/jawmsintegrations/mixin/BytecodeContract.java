package com.mahghuuuls.jawmsintegrations.mixin;

import com.mahghuuuls.jawmsintegrations.integration.EarlyModMetadataScanner;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

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

    public static void requireNoMethod(ClassNode owner, String[] names, String descriptor)
            throws Violation {
        for (MethodNode method : owner.methods) {
            if (matches(method.name, names) && descriptor.equals(method.desc)) {
                throw new Violation("unexpected method " + owner.name.replace('/', '.')
                        + "." + method.name + descriptor);
            }
        }
    }

    public static void requireConstructionAfterString(MethodNode method,
                                                      String constant,
                                                      String constructedOwner)
            throws Violation {
        int constants = 0;
        int matches = 0;
        for (AbstractInsnNode instruction = method.instructions.getFirst();
             instruction != null; instruction = instruction.getNext()) {
            if (!(instruction instanceof LdcInsnNode)
                    || !constant.equals(((LdcInsnNode) instruction).cst)) {
                continue;
            }
            constants++;
            int remaining = 16;
            for (AbstractInsnNode candidate = instruction.getNext();
                 candidate != null && remaining-- > 0;
                 candidate = candidate.getNext()) {
                if (candidate instanceof TypeInsnNode
                        && candidate.getOpcode() == Opcodes.NEW) {
                    if (constructedOwner.equals(((TypeInsnNode) candidate).desc)) {
                        matches++;
                    }
                    break;
                }
                if (candidate instanceof MethodInsnNode
                        && candidate.getOpcode() == Opcodes.INVOKESTATIC) {
                    break;
                }
            }
        }
        if (constants != 1 || matches != 1) {
            throw new Violation("expected one " + constructedOwner.replace('/', '.')
                    + " construction after string '" + constant + "' in "
                    + method.name + method.desc + " but found constants=" + constants
                    + ", matches=" + matches);
        }
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
