package com.mahghuuuls.jawmsintegrations.integration.jafs;

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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/** Owns native food access, guarded registration and bounded source warnings. */
public final class JafsIntegration implements IContextualManaModifierProvider {
    public static final ResourceLocation PROVIDER_ID = new ResourceLocation(Tags.MOD_ID, "jafs_foods");

    private final IFoodService foods;
    private final Consumer<EntityPlayer> dirty;
    private final Consumer<String> warnings;
    private final Consumer<String> failures;
    // At most one entry per catalog stat/operation/reason, not a player-state mirror.
    private final Set<FoodStatProjection.Problem> warned = new HashSet<>();
    private boolean warnedUnavailable;
    private volatile boolean enabled;

    private JafsIntegration(IFoodService foods, Consumer<EntityPlayer> dirty, Consumer<String> warnings,
                            Consumer<String> failures) {
        this.foods = foods;
        this.dirty = dirty;
        this.warnings = warnings;
        this.failures = failures;
    }

    public static void activate(Consumer<String> failure) {
        install(new NativeRegistration(), ManaApi::markModifierCacheDirty,
                message -> JawmsIntegrationsMod.LOGGER.warn(message), failure);
    }

    /** Platform registration is irreversible; tests substitute that boundary, not food math. */
    interface Registration {
        IFoodService validatedService();
        void contextual(IContextualManaModifierProvider provider);
        void property(PropertySpec spec, IPropertyApplier applier);
    }

    static JafsIntegration install(Registration registration, Consumer<EntityPlayer> dirty,
                                   Consumer<String> warnings, Consumer<String> failures) {
        IFoodService foods = registration.validatedService();
        if (foods == null) throw new IllegalStateException("JAFS food service is unavailable");
        List<PropertySpec> specs = propertySpecs();
        JafsIntegration module = new JafsIntegration(foods, dirty, warnings, failures);
        registration.contextual(module);
        for (PropertySpec spec : specs) {
            registration.property(spec, (player, flat, percent) -> {
                // Callbacks are refresh notifications, never an aggregate-state mirror.
                if (module.enabled && player != null) module.dirty.accept(player);
            });
        }
        module.enabled = true;
        return module;
    }

    static List<PropertySpec> propertySpecs() {
        List<PropertySpec> result = new ArrayList<>();
        for (FoodStatProjection.Stat stat : FoodStatProjection.Stat.values()) {
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
        FoodStatProjection.Result result;
        try {
            result = FoodStatProjection.project(stat -> {
                PropertyValue value = foods.getPropertyValue((EntityPlayerMP) player, stat.getId());
                if (value == null) throw new IllegalStateException("JAFS returned no property total");
                return new FoodStatProjection.Totals(value.flat(), value.percent());
            });
        } catch (LinkageError exception) {
            enabled = false;
            failures.accept("JAFS runtime API linkage failed: " + exception.getClass().getSimpleName()
                    + "; food contribution disabled until restart");
            return ManaContribution.EMPTY;
        } catch (RuntimeException exception) {
            if (!warnedUnavailable) {
                warnedUnavailable = true;
                warnings.accept("JAFS food contribution unavailable: "
                        + exception.getClass().getSimpleName() + "; contributing zero");
            }
            return ManaContribution.EMPTY;
        }
        for (FoodStatProjection.Problem problem : result.getProblems()) {
            if (warned.add(problem)) warnings.accept("JAFS food field omitted: " + problem);
        }
        return result.getContribution();
    }

    static void requireRegistration(String apiVersion, boolean bound, boolean foodFrozen,
                                    boolean manaFrozen) {
        if (apiVersion == null || !apiVersion.matches("[0-9]+\\.[0-9]+(?:\\.[0-9]+)*(?:[-+].+)?")
                || new DefaultArtifactVersion(apiVersion).compareTo(new DefaultArtifactVersion("1.0")) < 0) {
            throw new IllegalStateException("JAFS API 1.0 or newer is required");
        }
        if (!bound) throw new IllegalStateException("JAFS public API is not bound");
        if (foodFrozen || manaFrozen) {
            throw new IllegalStateException("Food/mana provider registration is already frozen");
        }
    }

    private static final class NativeRegistration implements Registration {
        @Override
        public IFoodService validatedService() {
            String version;
            try {
                // Read the actual runtime field; a direct constant access would be inlined.
                version = (String) JafsApi.class.getField("API_VERSION").get(null);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("JAFS public API version is unavailable", exception);
            }
            requireRegistration(version, JafsApi.isBound(), JafsApi.isRegistrationFrozen(),
                    ManaApi.isProviderRegistrationFrozen());
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
    }
}

