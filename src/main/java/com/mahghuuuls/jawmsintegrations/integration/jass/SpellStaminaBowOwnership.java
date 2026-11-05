package com.mahghuuuls.jawmsintegrations.integration.jass;

import com.mahghuuuls.jass.api.IItemValueProvider;
import com.mahghuuuls.jass.api.JassApi;
import com.mahghuuuls.jawmsintegrations.JawmsIntegrationsMod;
import com.mahghuuuls.jawmsintegrations.Tags;
import electroblob.wizardry.item.ItemWand;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.FMLCommonHandler;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Supplies JASS with the ownership decision for the calling logical side. */
public final class SpellStaminaBowOwnership implements IItemValueProvider {
    static final ResourceLocation ID = new ResourceLocation(Tags.MOD_ID, "spell_stamina");

    private final BooleanSupplier onServerThread;
    private final BooleanSupplier serverEnabled;
    private final BooleanSupplier clientEnabled;
    private final Consumer<LinkageError> serverContractLoss;
    private volatile boolean registered;

    SpellStaminaBowOwnership(BooleanSupplier onServerThread, BooleanSupplier serverEnabled,
                             BooleanSupplier clientEnabled, Consumer<LinkageError> serverContractLoss) {
        this.onServerThread = onServerThread;
        this.serverEnabled = serverEnabled;
        this.clientEnabled = clientEnabled;
        this.serverContractLoss = serverContractLoss;
    }

    public static void registerClientOnly() throws NoSuchMethodException {
        verifyContract();
        SpellStaminaBowOwnership ownership = new SpellStaminaBowOwnership(
                SpellStaminaBowOwnership::isServerThread,
                () -> false, JawmsIntegrationsMod.PROXY::isClientBowOwnershipEnabled, cause -> {});
        ownership.register(provider -> JassApi.registerItemValueProvider(ID, provider));
    }

    static void verifyContract() throws NoSuchMethodException {
        if (IItemValueProvider.class.getMethod("ownsBowUse", ItemStack.class).getReturnType() != boolean.class) {
            throw new NoSuchMethodException("IItemValueProvider.ownsBowUse");
        }
    }

    void register(Consumer<IItemValueProvider> registerItems) {
        registerItems.accept(this);
        registered = true;
    }

    @Override
    public boolean ownsBowUse(ItemStack stack) {
        if (!registered) return false;
        if (!onServerThread.getAsBoolean()) {
            return clientEnabled.getAsBoolean() && isWand(stack);
        }
        try {
            return serverEnabled.getAsBoolean() && isWand(stack);
        } catch (LinkageError incompatible) {
            serverContractLoss.accept(incompatible);
            return false;
        }
    }

    static boolean isServerThread() {
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        return server != null && server.isCallingFromMinecraftThread();
    }

    static boolean isWand(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem() instanceof ItemWand;
    }
}
