package com.mahghuuuls.jawmsintegrations.integration.qualitytools;

import com.mahghuuuls.jawms.api.ManaApi;
import com.mahghuuuls.jawms.api.ManaContribution;
import com.mahghuuuls.jawmsintegrations.JawmsIntegrationsMod;
import com.mahghuuuls.jawmsintegrations.Tags;
import com.mahghuuuls.jawmsintegrations.config.IntegrationConfigSnapshot;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.attributes.AbstractAttributeMap;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttribute;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.ai.attributes.RangedAttribute;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Deep optional module: owns candidate configuration, attribute projection, and refresh lifecycle. */
public final class QualityToolsIntegration {

    private static final ResourceLocation PROVIDER_ID =
            new ResourceLocation(Tags.MOD_ID, "quality_tools_attributes");
    private static final Map<QualityAttributeProjection.Attribute, IAttribute> ATTRIBUTES = attributes();

    private static volatile QualityToolsIntegration active;

    private final QualityValueChangeTracker tracker = new QualityValueChangeTracker();
    private final BoundedWarnings warnings = new BoundedWarnings();
    private final Runnable reloadSummary;

    private QualityToolsIntegration(IntegrationConfigSnapshot.QualityToolsConfig config,
                                    Runnable reloadSummary) {
        QualityCandidateAugmenter.configure(config);
        this.reloadSummary = reloadSummary;
    }

    public static synchronized void activate(IntegrationConfigSnapshot.QualityToolsConfig config,
                                             Runnable reloadSummary) {
        if (active != null) {
            return;
        }
        if (config == null) {
            throw new IllegalArgumentException("Quality Tools configuration must not be null");
        }
        if (reloadSummary == null) {
            throw new IllegalArgumentException("Quality Tools reload summary callback must not be null");
        }
        if (ManaApi.isProviderRegistrationFrozen()) {
            throw new IllegalStateException("JAWMS provider registration is already frozen");
        }
        QualityToolsIntegration integration = new QualityToolsIntegration(config, reloadSummary);
        ManaApi.registerContextualProvider(PROVIDER_ID, integration::contributionFor);
        MinecraftForge.EVENT_BUS.register(integration);
        FMLCommonHandler.instance().bus().register(integration);
        active = integration;
    }

    /** Called by the minimum-version-gated command Mixin after Quality Tools reloads its files. */
    public static void afterConfigReload() {
        QualityToolsIntegration integration = active;
        if (integration != null) {
            integration.reloadSummary.run();
        }
    }

    /** Called only by the minimum-version-gated Quality Tools Mixin after its server attribute update. */
    public static void afterAttributeUpdate(EntityLivingBase entity) {
        QualityToolsIntegration integration = active;
        if (integration == null || !(entity instanceof EntityPlayer) || entity.world.isRemote) {
            return;
        }
        EntityPlayer player = (EntityPlayer) entity;
        QualityAttributeProjection.Values values = integration.readValues(player);
        integration.tracker.observe(player.getUniqueID(), values,
                () -> ManaApi.markModifierCacheDirty(player));
    }

    @SubscribeEvent
    public void onEntityConstructing(EntityEvent.EntityConstructing event) {
        if (event.getEntity() instanceof EntityPlayer) {
            installAttributes((EntityPlayer) event.getEntity());
        }
    }

    @SubscribeEvent
    public void onClone(PlayerEvent.Clone event) {
        tracker.clear(event.getOriginal().getUniqueID());
        tracker.clear(event.getEntityPlayer().getUniqueID());
        installAttributes(event.getEntityPlayer());
    }

    @SubscribeEvent
    public void onLogout(net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedOutEvent event) {
        tracker.clear(event.player.getUniqueID());
    }

    @SubscribeEvent
    public void onLogin(net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent event) {
        tracker.clear(event.player.getUniqueID());
        installAttributes(event.player);
    }

    @SubscribeEvent
    public void onRespawn(net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerRespawnEvent event) {
        tracker.clear(event.player.getUniqueID());
        installAttributes(event.player);
    }

    @SubscribeEvent
    public void onDimensionChange(net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerChangedDimensionEvent event) {
        tracker.clear(event.player.getUniqueID());
        installAttributes(event.player);
    }

    private ManaContribution contributionFor(EntityPlayer player) {
        if (player == null || player.world == null || player.world.isRemote) {
            return ManaContribution.EMPTY;
        }
        return QualityAttributeProjection.toContribution(readValues(player));
    }

    private QualityAttributeProjection.Values readValues(EntityPlayer player) {
        EnumMap<QualityAttributeProjection.Attribute, List<QualityAttributeProjection.ModifierValue>>
                projected = new EnumMap<>(QualityAttributeProjection.Attribute.class);
        for (Map.Entry<QualityAttributeProjection.Attribute, IAttribute> entry : ATTRIBUTES.entrySet()) {
            projected.put(entry.getKey(), modifiers(player, entry.getValue()));
        }
        return QualityAttributeProjection.project(projected, warnings);
    }

    private static List<QualityAttributeProjection.ModifierValue> modifiers(EntityPlayer player,
                                                                            IAttribute attribute) {
        IAttributeInstance instance = player.getEntityAttribute(attribute);
        if (instance == null) {
            return Collections.emptyList();
        }
        List<QualityAttributeProjection.ModifierValue> values = new ArrayList<>();
        for (AttributeModifier modifier : instance.getModifiers()) {
            values.add(new QualityAttributeProjection.ModifierValue(
                    modifier.getAmount(), modifier.getOperation()));
        }
        return values;
    }

    private static void installAttributes(EntityPlayer player) {
        AbstractAttributeMap attributes = player.getAttributeMap();
        for (IAttribute attribute : ATTRIBUTES.values()) {
            registerIfMissing(attributes, attribute);
        }
    }

    private static void registerIfMissing(AbstractAttributeMap map, IAttribute attribute) {
        if (map.getAttributeInstanceByName(attribute.getName()) == null) {
            map.registerAttribute(attribute);
        }
    }

    private static IAttribute attribute(String name) {
        return new RangedAttribute(null, name, 0.0D, -Double.MAX_VALUE, Double.MAX_VALUE)
                .setShouldWatch(true);
    }

    private static Map<QualityAttributeProjection.Attribute, IAttribute> attributes() {
        EnumMap<QualityAttributeProjection.Attribute, IAttribute> attributes =
                new EnumMap<>(QualityAttributeProjection.Attribute.class);
        for (QualityAttributeProjection.Attribute attribute : QualityAttributeProjection.Attribute.values()) {
            attributes.put(attribute, attribute(attribute.getPublicName()));
        }
        return Collections.unmodifiableMap(attributes);
    }

    static final class BoundedWarnings implements QualityAttributeProjection.WarningSink {
        private final Set<QualityAttributeProjection.Problem> emitted =
                EnumSet.noneOf(QualityAttributeProjection.Problem.class);
        private int emissionCount;

        @Override
        public synchronized void warn(QualityAttributeProjection.Problem problem,
                                      String attributeName,
                                      int operation,
                                      double amount) {
            if (emitted.add(problem)) {
                emissionCount++;
                JawmsIntegrationsMod.LOGGER.warn(
                        "Ignoring invalid Quality Tools mana modifier: problem={}, attribute={}, operation={}, amount={}",
                        problem, attributeName, operation, amount
                );
            }
        }

        synchronized int getEmissionCount() {
            return emissionCount;
        }
    }
}
