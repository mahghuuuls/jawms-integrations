package com.mahghuuuls.jawmsintegrations.integration.crafttweaker;

import com.mahghuuuls.jawms.api.IManaService;
import com.mahghuuuls.jawms.api.ManaApi;
import com.mahghuuuls.jawms.api.ManaMutationResult;
import com.mahghuuuls.jawms.api.ManaPublicState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;

/** Owns activation, logical-server validation, and all JAWMS script delegation. */
public final class CraftTweakerManaService {

    public static final ResourceLocation SET_CAUSE =
            new ResourceLocation("jawmsintegrations", "crafttweaker_set_current_mana");
    public static final ResourceLocation RESTORE_CAUSE =
            new ResourceLocation("jawmsintegrations", "crafttweaker_restore_mana");
    public static final ResourceLocation CONSUME_CAUSE =
            new ResourceLocation("jawmsintegrations", "crafttweaker_consume_mana");
    public static final ResourceLocation DRAIN_CAUSE =
            new ResourceLocation("jawmsintegrations", "crafttweaker_drain_mana");
    public static final ResourceLocation LOCKOUT_CAUSE =
            new ResourceLocation("jawmsintegrations", "crafttweaker_regeneration_lockout");

    private static volatile CraftTweakerManaService installed = inactive(
            "CraftTweaker integration has not been activated"
    );

    private final boolean active;
    private final String inactiveReason;
    private final PlayerValidator playerValidator;
    private final ManaBackend backend;

    CraftTweakerManaService(boolean active,
                            String inactiveReason,
                            PlayerValidator playerValidator,
                            ManaBackend backend) {
        if (inactiveReason == null || playerValidator == null || backend == null) {
            throw new IllegalArgumentException("CraftTweaker mana service inputs must not be null");
        }
        this.active = active;
        this.inactiveReason = inactiveReason;
        this.playerValidator = playerValidator;
        this.backend = backend;
    }

    public static CraftTweakerManaService get() {
        return installed;
    }

    public static void installActive() {
        installed = new CraftTweakerManaService(true, "", new ServerPlayerValidator(),
                new JawmsManaBackend(ManaApi.getManaService()));
    }

    public static void installInactive(String reason) {
        installed = inactive(reason);
    }

    private static CraftTweakerManaService inactive(String reason) {
        String detail = reason == null || reason.trim().isEmpty()
                ? "CraftTweaker integration is inactive" : reason.trim();
        return new CraftTweakerManaService(false, detail, player -> {
            throw new IllegalStateException("Inactive service must not validate players");
        }, new UnavailableManaBackend());
    }

    public CraftTweakerManaState getState(EntityPlayer player) {
        validate(player);
        return CraftTweakerManaState.from(backend.getState(player));
    }

    public CraftTweakerManaMutationResult setCurrentMana(EntityPlayer player, int target) {
        validate(player);
        validateAmount(target, "target");
        return CraftTweakerManaMutationResult.from(backend.setCurrentMana(player, target, SET_CAUSE));
    }

    public CraftTweakerManaMutationResult restoreMana(EntityPlayer player, int amount) {
        validate(player);
        validateAmount(amount, "amount");
        return CraftTweakerManaMutationResult.from(backend.restoreMana(player, amount, RESTORE_CAUSE));
    }

    public CraftTweakerManaMutationResult consumeMana(EntityPlayer player, int amount) {
        validate(player);
        validateAmount(amount, "amount");
        return CraftTweakerManaMutationResult.from(backend.consumeMana(player, amount, CONSUME_CAUSE));
    }

    public CraftTweakerManaMutationResult drainMana(EntityPlayer player, int maximumAmount) {
        validate(player);
        validateAmount(maximumAmount, "maximumAmount");
        return CraftTweakerManaMutationResult.from(backend.drainMana(player, maximumAmount, DRAIN_CAUSE));
    }

    public long startRegenerationLockout(EntityPlayer player) {
        validate(player);
        try {
            return backend.startRegenerationLockout(player, LOCKOUT_CAUSE);
        } catch (UnsupportedOperationException exception) {
            throw new IllegalStateException(
                    "JAWMS API does not support explicit regeneration lockout", exception);
        }
    }

    private void validate(EntityPlayer player) {
        if (!active) {
            throw new IllegalStateException("CraftTweaker integration is inactive: " + inactiveReason);
        }
        playerValidator.validate(player);
    }

    private static void validateAmount(int amount, String name) {
        if (amount < 0) {
            throw new IllegalArgumentException(name + " must be a whole non-negative mana amount");
        }
    }

    interface PlayerValidator {
        void validate(EntityPlayer player);
    }

    interface ManaBackend {
        ManaPublicState getState(EntityPlayer player);
        ManaMutationResult setCurrentMana(EntityPlayer player, int target, ResourceLocation cause);
        ManaMutationResult restoreMana(EntityPlayer player, int amount, ResourceLocation cause);
        ManaMutationResult consumeMana(EntityPlayer player, int amount, ResourceLocation cause);
        ManaMutationResult drainMana(EntityPlayer player, int amount, ResourceLocation cause);
        long startRegenerationLockout(EntityPlayer player, ResourceLocation cause);
    }

    private static final class ServerPlayerValidator implements PlayerValidator {
        @Override
        public void validate(EntityPlayer player) {
            if (!(player instanceof EntityPlayerMP) || player.world == null || player.world.isRemote) {
                throw new IllegalArgumentException(
                        "A valid online logical-server player is required");
            }
            EntityPlayerMP serverPlayer = (EntityPlayerMP) player;
            MinecraftServer server = serverPlayer.getServer();
            if (server == null || !server.isCallingFromMinecraftThread()) {
                throw new IllegalStateException(
                        "CraftTweaker mana operations require the logical-server thread");
            }
            EntityPlayerMP online = server.getPlayerList().getPlayerByUUID(player.getUniqueID());
            if (online != player) {
                throw new IllegalArgumentException(
                        "The supplied player is not the current online logical-server player");
            }
        }
    }

    private static final class JawmsManaBackend implements ManaBackend {
        private final IManaService service;

        private JawmsManaBackend(IManaService service) {
            if (service == null) throw new IllegalArgumentException("JAWMS mana service is unavailable");
            this.service = service;
        }

        @Override public ManaPublicState getState(EntityPlayer player) { return service.getState(player); }
        @Override public ManaMutationResult setCurrentMana(EntityPlayer player, int target,
                                                            ResourceLocation cause) {
            return service.setCurrentMana(player, target, cause);
        }
        @Override public ManaMutationResult restoreMana(EntityPlayer player, int amount,
                                                         ResourceLocation cause) {
            return service.restoreMana(player, amount, cause);
        }
        @Override public ManaMutationResult consumeMana(EntityPlayer player, int amount,
                                                         ResourceLocation cause) {
            return service.consumeMana(player, amount, cause);
        }
        @Override public ManaMutationResult drainMana(EntityPlayer player, int amount,
                                                       ResourceLocation cause) {
            return service.drainMana(player, amount, cause);
        }
        @Override public long startRegenerationLockout(EntityPlayer player, ResourceLocation cause) {
            return service.startRegenerationLockout(player, cause);
        }
    }

    private static final class UnavailableManaBackend implements ManaBackend {
        private IllegalStateException unavailable() {
            return new IllegalStateException("Inactive CraftTweaker mana backend was invoked");
        }
        @Override public ManaPublicState getState(EntityPlayer player) { throw unavailable(); }
        @Override public ManaMutationResult setCurrentMana(EntityPlayer player, int target,
                                                            ResourceLocation cause) { throw unavailable(); }
        @Override public ManaMutationResult restoreMana(EntityPlayer player, int amount,
                                                         ResourceLocation cause) { throw unavailable(); }
        @Override public ManaMutationResult consumeMana(EntityPlayer player, int amount,
                                                         ResourceLocation cause) { throw unavailable(); }
        @Override public ManaMutationResult drainMana(EntityPlayer player, int amount,
                                                       ResourceLocation cause) { throw unavailable(); }
        @Override public long startRegenerationLockout(EntityPlayer player, ResourceLocation cause) {
            throw unavailable();
        }
    }
}
