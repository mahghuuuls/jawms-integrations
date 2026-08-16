package com.mahghuuuls.jawmsintegrations.integration.ancientspellcraft;

import com.mahghuuuls.jawms.api.IManaService;
import com.mahghuuuls.jawms.api.ManaApi;
import com.mahghuuuls.jawms.api.ManaMutationResult;
import com.mahghuuuls.jawmsintegrations.Tags;
import com.mahghuuuls.jawmsintegrations.config.IntegrationConfigSnapshot;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;

import java.util.function.IntUnaryOperator;

/** Server-authoritative Everfull regeneration and JAWMS restoration service. */
public final class EverfullManaService {

    public static final ResourceLocation RESTORE_CAUSE =
            new ResourceLocation(Tags.MOD_ID, "ancient_everfull_flask");
    private static volatile EverfullManaService active = disabled();

    private final boolean integrationActive;
    private final IntegrationConfigSnapshot.EverfullManaFlaskConfig config;
    private final EverfullManaState state;

    public EverfullManaService(boolean integrationActive,
                               IntegrationConfigSnapshot.EverfullManaFlaskConfig config) {
        if (config == null) {
            throw new NullPointerException("config");
        }
        this.integrationActive = integrationActive;
        this.config = config;
        this.state = new EverfullManaState();
    }

    public static EverfullManaService disabled() {
        return new EverfullManaService(false,
                new IntegrationConfigSnapshot.EverfullManaFlaskConfig(false, 1, 12, 10));
    }

    public static void install(EverfullManaService service) {
        if (service == null) {
            throw new NullPointerException("service");
        }
        active = service;
    }

    public static EverfullManaService active() {
        return active;
    }

    public boolean handles(ItemStack stack) {
        return integrationActive && config.isEnabled()
                && AncientReplacement.EVERFULL_MANA_FLASK
                == AncientReplacementPolicy.active().replacementFor(stack);
    }

    public boolean tick(ItemStack stack, int legacyMana, long worldTime) {
        if (!handles(stack)) {
            return false;
        }
        int before = state.peek(stack);
        int stored = state.getOrImport(stack, legacyMana);
        if (stored == EverfullManaState.UNAVAILABLE) {
            return false;
        }
        int intervalTicks = config.getRegenerationIntervalSeconds() * 20;
        if (worldTime % intervalTicks == 0 && stored < EverfullManaState.CAPACITY) {
            state.set(stack, stored + config.getRegenerationAmount());
        }
        return before != state.peek(stack);
    }

    public ActionResult<ItemStack> use(World world,
                                       EntityPlayer player,
                                       EnumHand hand,
                                       ItemStack stack,
                                       int legacyMana) {
        if (!handles(stack)) {
            return new ActionResult<>(EnumActionResult.PASS, stack);
        }
        if (hand != EnumHand.OFF_HAND) {
            return new ActionResult<>(EnumActionResult.FAIL, stack);
        }
        if (world.isRemote) {
            return new ActionResult<>(EnumActionResult.SUCCESS, stack);
        }
        IManaService mana = ManaApi.getManaService();
        int actual = transfer(stack, legacyMana, mana.getCurrentMana(player),
                mana.getMaximumMana(player), requested -> {
                    ManaMutationResult result = mana.restoreMana(player, requested, RESTORE_CAUSE);
                    return result.isSuccessful() ? result.getActualAmount() : 0;
                });
        if (actual > 0) {
            player.inventory.markDirty();
            return new ActionResult<>(EnumActionResult.SUCCESS, stack);
        }
        return new ActionResult<>(EnumActionResult.FAIL, stack);
    }

    int transfer(ItemStack stack,
                 int legacyMana,
                 int currentMana,
                 int maximumMana,
                 IntUnaryOperator restore) {
        if (!handles(stack) || restore == null) {
            return 0;
        }
        int stored = state.getOrImport(stack, legacyMana);
        if (stored == EverfullManaState.UNAVAILABLE) {
            return 0;
        }
        int requested = Math.min(config.getTransferAmount(),
                Math.min(stored, Math.max(0, maximumMana - currentMana)));
        if (requested <= 0) {
            return 0;
        }
        int actual = Math.max(0, Math.min(requested, restore.applyAsInt(requested)));
        if (actual > 0) {
            state.set(stack, stored - actual);
        }
        return actual;
    }

    public IntegrationConfigSnapshot.EverfullManaFlaskConfig getConfig() {
        return config;
    }
}
