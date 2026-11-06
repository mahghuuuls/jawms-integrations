package com.mahghuuuls.jawmsintegrations.integration.jafs;

import com.mahghuuuls.jawmsintegrations.integration.stat.ManaStatProjection;

import com.mahghuuuls.jafs.api.IFoodService;
import com.mahghuuuls.jafs.api.IPropertyApplier;
import com.mahghuuuls.jafs.api.JafsApi;
import com.mahghuuuls.jafs.api.PropertyOperation;
import com.mahghuuuls.jafs.api.PropertySpec;
import com.mahghuuuls.jafs.api.PropertyValue;
import com.mahghuuuls.jawms.api.IContextualManaModifierProvider;
import com.mahghuuuls.jawms.api.ManaApi;
import com.mahghuuuls.jawms.api.ManaContribution;
import com.mahghuuuls.jawmsintegrations.JawmsIntegrationsMod;
import com.mahghuuuls.jawmsintegrations.Tags;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.versioning.DefaultArtifactVersion;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/** Owns native food access, guarded registration and bounded source warnings. */
public final class JafsIntegration implements IContextualManaModifierProvider, FoodSourceAccess {
    public static final ResourceLocation PROVIDER_ID = new ResourceLocation(Tags.MOD_ID, "jafs_foods");

    private final IFoodService foods;
    private final Consumer<EntityPlayer> dirty;
    private final Consumer<String> warnings;
    private final Consumer<String> failures;
    // Finite stat/operation/reason fingerprints per online player, never food snapshots.
    private final Map<UUID, Set<ManaStatProjection.Problem>> warned = new HashMap<>();
    private final Set<UUID> warnedUnavailable = new HashSet<>();
    private volatile boolean enabled;

    private JafsIntegration(IFoodService foods, Consumer<EntityPlayer> dirty, Consumer<String> warnings,
                            Consumer<String> failures) {
        this.foods = foods;
        this.dirty = dirty;
        this.warnings = warnings;
        this.failures = failures;
    }

    public static FoodSourceAccess initialize(boolean effectsEnabled, Consumer<String> failure) {
        JafsIntegration module = install(effectsEnabled, new NativeRegistration(), ManaApi::markModifierCacheDirty,
                message -> JawmsIntegrationsMod.LOGGER.warn(message), failure);
        return effectsEnabled ? module : null;
    }

    /** Platform registration is irreversible; tests substitute that boundary, not food math. */
    interface Registration {
        void validateMetadata();
        IFoodService validatedService();
        void contextual(IContextualManaModifierProvider provider);
        void property(PropertySpec spec, IPropertyApplier applier);
        void hooks(JafsIntegration module);
    }

    static JafsIntegration install(boolean effectsEnabled, Registration registration, Consumer<EntityPlayer> dirty,
                                   Consumer<String> warnings, Consumer<String> failures) {
        // Validate every required contract before the first irreversible registry write.
        List<PropertySpec> specs = propertySpecs();
        registration.validateMetadata();
        IFoodService foods = effectsEnabled ? registration.validatedService() : null;
        if (effectsEnabled && foods == null) throw new IllegalStateException("JAFS food service is unavailable");
        JafsIntegration module = new JafsIntegration(foods, dirty, warnings, failures);
        for (PropertySpec spec : specs) {
            registration.property(spec, (player, flat, percent) -> {
                // Callbacks are refresh notifications, never an aggregate-state mirror.
                if (module.enabled && player != null) module.dirty.accept(player);
            });
        }
        if (effectsEnabled) {
            registration.contextual(module);
            registration.hooks(module);
            module.enabled = true;
        }
        return module;
    }

    static List<PropertySpec> propertySpecs() {
        List<PropertySpec> result = new ArrayList<>();
        for (ManaStatProjection.Stat stat : ManaStatProjection.Stat.values()) {
            PropertySpec.Builder spec = PropertySpec.builder(stat.getId()).unit(stat.unit()).color("blue")
                    .operation(PropertyOperation.FLAT, stat.minimum(false), Double.MAX_VALUE);
            if (stat.supportsPercent()) {
                spec.operation(PropertyOperation.PERCENT, stat.minimum(true), Double.MAX_VALUE);
            }
            result.add(spec.build());
        }
        return result;
    }

    @Override
    public ManaContribution getContribution(EntityPlayer player) {
        if (!enabled || !(player instanceof EntityPlayerMP)
                || (player.world != null && player.world.isRemote)) {
            return ManaContribution.EMPTY;
        }
        ManaStatProjection.Result result;
        try {
            result = inspect(player);
        } catch (LinkageError exception) {
            enabled = false;
            failures.accept("JAFS runtime API linkage failed: " + exception.getClass().getSimpleName()
                    + "; food contribution disabled until restart");
            return ManaContribution.EMPTY;
        } catch (RuntimeException exception) {
            if (warnedUnavailable.add(player.getUniqueID())) {
                warnings.accept("JAFS food contribution unavailable for " + player.getUniqueID() + ": "
                        + exception.getClass().getSimpleName() + "; contributing zero");
            }
            return ManaContribution.EMPTY;
        }
        Set<ManaStatProjection.Problem> playerWarnings = warned.computeIfAbsent(
                player.getUniqueID(), ignored -> new HashSet<>());
        for (ManaStatProjection.Problem problem : result.getProblems()) {
            if (playerWarnings.add(problem)) warnings.accept("JAFS food field omitted for "
                    + player.getUniqueID() + ": " + problem);
        }
        return result.getContribution();
    }

    @Override
    public ManaStatProjection.Result inspect(EntityPlayer player) {
        if (!enabled || !(player instanceof EntityPlayerMP)
                || player.world != null && player.world.isRemote) {
            throw new IllegalStateException("JAFS source requires an active server-player module");
        }
        return ManaStatProjection.project(stat -> {
            PropertyValue value = foods.getPropertyValue((EntityPlayerMP) player, stat.getId());
            if (value == null) throw new IllegalStateException("JAFS returned no property total");
            return new ManaStatProjection.Totals(value.flat(), value.percent());
        });
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.player != null) {
            warned.remove(event.player.getUniqueID());
            warnedUnavailable.remove(event.player.getUniqueID());
        }
    }

    @Override
    public void clearWarnings() {
        warned.clear();
        warnedUnavailable.clear();
    }

    static void requireMetadataRegistration(String apiVersion, boolean bound, boolean foodFrozen) {
        if (apiVersion == null || !apiVersion.matches("[0-9]+\\.[0-9]+(?:\\.[0-9]+)*(?:[-+].+)?")
                || new DefaultArtifactVersion(apiVersion).compareTo(new DefaultArtifactVersion("1.0")) < 0) {
            throw new IllegalStateException("JAFS API 1.0 or newer is required");
        }
        if (!bound) throw new IllegalStateException("JAFS public API is not bound");
        if (foodFrozen) {
            throw new IllegalStateException("Food property registration is already frozen");
        }
    }

    private static final class NativeRegistration implements Registration {
        @Override
        public void validateMetadata() {
            String version;
            try {
                // Read the actual runtime field; a direct constant access would be inlined.
                version = (String) JafsApi.class.getField("API_VERSION").get(null);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("JAFS public API version is unavailable", exception);
            }
            requireMetadataRegistration(version, JafsApi.isBound(), JafsApi.isRegistrationFrozen());
        }

        @Override
        public IFoodService validatedService() {
            if (ManaApi.isProviderRegistrationFrozen()) {
                throw new IllegalStateException("Mana provider registration is already frozen");
            }
            return JafsApi.getFoodService();
        }

        @Override
        public void contextual(IContextualManaModifierProvider provider) {
            ManaApi.registerContextualProvider(PROVIDER_ID, provider);
        }

        @Override
        public void property(PropertySpec spec, IPropertyApplier applier) {
            JafsApi.registerProperty(spec, applier);
        }

        @Override
        public void hooks(JafsIntegration module) {
            MinecraftForge.EVENT_BUS.register(module);
        }
    }
}

