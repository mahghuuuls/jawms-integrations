package com.mahghuuuls.jawmsintegrations.integration.ancientspellcraft;

import com.mahghuuuls.jawms.api.ConfiguredFlaskSnapshot;
import com.mahghuuuls.jawms.api.IManaService;
import com.mahghuuuls.jawms.api.ManaApi;
import com.mahghuuuls.jawms.api.ManaMutationResult;
import com.mahghuuuls.jawmsintegrations.Tags;
import com.mahghuuuls.jawmsintegrations.config.IntegrationConfigSnapshot;
import electroblob.wizardry.registry.WizardryItems;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.IntUnaryOperator;
import java.util.function.Supplier;

/** Owns the complete server-authoritative automatic ordinary-flask transaction. */
public final class DagorimFlaskService {

    private static final ResourceLocation RESTORE_CAUSE =
            new ResourceLocation(Tags.MOD_ID, "ancient_ring_of_dagorim");
    private static volatile DagorimFlaskService active = disabled();

    private final boolean integrationActive;
    private final IntegrationConfigSnapshot.RingOfDagorimConfig config;
    private final Item largeFlask;
    private final Item mediumFlask;
    private final Item smallFlask;
    private final Supplier<ConfiguredFlaskSnapshot> flaskSnapshots;
    private final Map<Object, Integer> lastCheckedTick = new WeakHashMap<>();
    private volatile ActivationRecord latestActivation;

    public DagorimFlaskService(boolean integrationActive,
                               IntegrationConfigSnapshot.RingOfDagorimConfig config) {
        this(integrationActive, config, WizardryItems.large_mana_flask,
                WizardryItems.medium_mana_flask, WizardryItems.small_mana_flask,
                ManaApi::getConfiguredFlaskSnapshot);
    }

    DagorimFlaskService(boolean integrationActive,
                        IntegrationConfigSnapshot.RingOfDagorimConfig config,
                        Item largeFlask, Item mediumFlask, Item smallFlask,
                        Supplier<ConfiguredFlaskSnapshot> flaskSnapshots) {
        if (config == null || largeFlask == null || mediumFlask == null
                || smallFlask == null || flaskSnapshots == null) {
            throw new NullPointerException("Dagorim service dependencies");
        }
        this.integrationActive = integrationActive;
        this.config = config;
        this.largeFlask = largeFlask;
        this.mediumFlask = mediumFlask;
        this.smallFlask = smallFlask;
        this.flaskSnapshots = flaskSnapshots;
    }

    public static DagorimFlaskService disabled() {
        Item placeholder = new Item();
        return new DagorimFlaskService(false,
                new IntegrationConfigSnapshot.RingOfDagorimConfig(false, 5, 20, 20.0D),
                placeholder, placeholder, placeholder, ConfiguredFlaskSnapshot::unavailable);
    }

    public static void install(DagorimFlaskService service) {
        if (service == null) throw new NullPointerException("service");
        active = service;
    }

    public static DagorimFlaskService active() { return active; }

    public boolean handles(ItemStack ring) {
        return integrationActive && config.isEnabled()
                && AncientReplacementPolicy.active().shouldReplaceDagorim(ring);
    }

    public boolean onWornTick(ItemStack ring, EntityLivingBase wearer) {
        if (!handles(ring) || !(wearer instanceof EntityPlayer) || wearer.world.isRemote) {
            return false;
        }
        EntityPlayer player = (EntityPlayer) wearer;
        return tick(new PlayerWearerAccess(player, ManaApi.getManaService()));
    }

    boolean tick(WearerAccess wearer) {
        if (wearer == null || !claimInterval(wearer.key(), wearer.ticksExisted())) return false;
        int currentMana = wearer.currentMana();
        if (currentMana >= config.getManaThreshold()) return false;
        double randomValue = wearer.randomValue();
        if (!eligible(wearer.ticksExisted(), currentMana, randomValue)) return false;
        ConfiguredFlaskSnapshot snapshot = flaskSnapshots.get();
        if (!usable(snapshot)) return false;
        Activation activation = attempt(wearer.ticksExisted(), currentMana,
                randomValue, wearer.inventory(), wearer::restore, snapshot);
        if (activation.isSuccessful()) {
            wearer.markDirty();
            latestActivation = new ActivationRecord(wearer.playerId(),
                    activation.getFlask().getRegistryName(), wearer.ticksExisted(),
                    activation.getRequested(), activation.getActual(),
                    activation.getSnapshotRevision());
        }
        return activation.isSuccessful();
    }

    Activation attempt(int wearerTicks, int currentMana, double randomValue,
                       List<ItemStack> inventory, IntUnaryOperator restore,
                       ConfiguredFlaskSnapshot snapshot) {
        if (!eligible(wearerTicks, currentMana, randomValue) || !usable(snapshot)) {
            return Activation.NONE;
        }
        ItemStack selected = select(inventory);
        if (selected.isEmpty()) return Activation.NONE;
        int requested = restoration(selected, snapshot);
        if (requested <= 0) return Activation.NONE;
        int actual = Math.max(0, Math.min(requested, restore.applyAsInt(requested)));
        if (actual <= 0) return Activation.NONE;
        Item item = selected.getItem();
        selected.shrink(1);
        return new Activation(item, requested, actual, snapshot.getRevision());
    }

    private static boolean usable(ConfiguredFlaskSnapshot snapshot) {
        return snapshot != null && snapshot.isAvailable() && snapshot.hasKnownRevision();
    }

    private int restoration(ItemStack selected, ConfiguredFlaskSnapshot snapshot) {
        Item item = selected.getItem();
        if (item == largeFlask) return snapshot.getLargeRestoration();
        if (item == mediumFlask) return snapshot.getMediumRestoration();
        if (item == smallFlask) return snapshot.getSmallRestoration();
        return 0;
    }

    boolean eligible(int wearerTicks, int currentMana, double randomValue) {
        return isIntervalBoundary(wearerTicks)
                && currentMana < config.getManaThreshold()
                && randomValue >= 0.0D && randomValue < 1.0D
                && randomValue * 100.0D < config.getActivationChancePercent();
    }

    private boolean isIntervalBoundary(int wearerTicks) {
        return wearerTicks >= 0 && wearerTicks % (config.getIntervalSeconds() * 20) == 0;
    }

    boolean claimInterval(Object wearerKey, int wearerTicks) {
        if (wearerKey == null || !isIntervalBoundary(wearerTicks)) return false;
        Integer previousTick = lastCheckedTick.put(wearerKey, wearerTicks);
        return previousTick == null || previousTick != wearerTicks;
    }

    private ItemStack select(List<ItemStack> inventory) {
        if (inventory == null) return ItemStack.EMPTY;
        ItemStack found = find(inventory, largeFlask);
        if (!found.isEmpty()) return found;
        found = find(inventory, mediumFlask);
        return found.isEmpty() ? find(inventory, smallFlask) : found;
    }

    private static ItemStack find(List<ItemStack> inventory, Item item) {
        for (ItemStack stack : inventory) {
            if (stack != null && !stack.isEmpty() && stack.getItem() == item) return stack;
        }
        return ItemStack.EMPTY;
    }

    public String latestActivationSummary() {
        ActivationRecord record = latestActivation;
        return record == null ? "none" : record.toString();
    }

    interface WearerAccess {
        Object key();
        int ticksExisted();
        int currentMana();
        double randomValue();
        List<ItemStack> inventory();
        int restore(int requested);
        void markDirty();
        String playerId();
    }

    private static final class PlayerWearerAccess implements WearerAccess {
        private final EntityPlayer player;
        private final IManaService mana;
        private PlayerWearerAccess(EntityPlayer player, IManaService mana) {
            this.player = player; this.mana = mana;
        }
        @Override public Object key() { return player; }
        @Override public int ticksExisted() { return player.ticksExisted; }
        @Override public int currentMana() { return mana.getCurrentMana(player); }
        @Override public double randomValue() { return player.world.rand.nextDouble(); }
        @Override public List<ItemStack> inventory() {
            List<ItemStack> result = new ArrayList<>(player.inventory.getSizeInventory());
            for (int slot = 0; slot < player.inventory.getSizeInventory(); slot++) {
                result.add(player.inventory.getStackInSlot(slot));
            }
            return result;
        }
        @Override public int restore(int requested) {
            ManaMutationResult result = mana.restoreMana(player, requested, RESTORE_CAUSE);
            return result == null ? 0 : result.getActualAmount();
        }
        @Override public void markDirty() { player.inventory.markDirty(); }
        @Override public String playerId() { return player.getUniqueID().toString(); }
    }

    static final class Activation {
        static final Activation NONE = new Activation(
                null, 0, 0, ConfiguredFlaskSnapshot.UNKNOWN_REVISION);
        private final Item flask;
        private final int requested;
        private final int actual;
        private final long snapshotRevision;
        Activation(Item flask, int requested, int actual, long snapshotRevision) {
            this.flask = flask; this.requested = requested; this.actual = actual;
            this.snapshotRevision = snapshotRevision;
        }
        boolean isSuccessful() { return flask != null && actual > 0; }
        Item getFlask() { return flask; }
        int getRequested() { return requested; }
        int getActual() { return actual; }
        long getSnapshotRevision() { return snapshotRevision; }
    }

    private static final class ActivationRecord {
        private final String playerId;
        private final ResourceLocation flask;
        private final int tick;
        private final int requested;
        private final int actual;
        private final long snapshotRevision;
        private ActivationRecord(String playerId, ResourceLocation flask, int tick,
                                 int requested, int actual, long snapshotRevision) {
            this.playerId = playerId; this.flask = flask; this.tick = tick;
            this.requested = requested; this.actual = actual;
            this.snapshotRevision = snapshotRevision;
        }
        @Override public String toString() {
            return "player=" + playerId + ", tick=" + tick + ", flask=" + flask
                    + ", requested=" + requested + ", actual=" + actual
                    + ", flaskRevision=" + snapshotRevision;
        }
    }
}
