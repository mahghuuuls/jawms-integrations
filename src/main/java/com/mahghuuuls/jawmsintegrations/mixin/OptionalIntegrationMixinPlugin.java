package com.mahghuuuls.jawmsintegrations.mixin;

import com.mahghuuuls.jawmsintegrations.integration.IntegrationId;
import com.mahghuuuls.jawmsintegrations.integration.EarlyModMetadataScanner;
import com.mahghuuuls.jawmsintegrations.integration.OptionalIntegrationEvidenceRegistry;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/** Minimum-version gate evaluated before optional target classes are resolved. */
public final class OptionalIntegrationMixinPlugin implements IMixinConfigPlugin {

    private IntegrationId integration;
    private OptionalIntegrationEvidenceRegistry.Evidence evidence =
            OptionalIntegrationEvidenceRegistry.Evidence.unknown();

    @Override
    public void onLoad(String mixinPackage) {
        integration = integrationForPackage(mixinPackage);
        if (integration == null) {
            evidence = OptionalIntegrationEvidenceRegistry.Evidence.error(
                    "Unknown optional Mixin package '" + mixinPackage + "'"
            );
            return;
        }
        ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
        if (contextLoader == null) {
            contextLoader = OptionalIntegrationMixinPlugin.class.getClassLoader();
        }
        evidence = EarlyModMetadataScanner.scan(contextLoader, integration);
        evidence = OptionalIntegrationContractValidator.validate(contextLoader, integration, evidence);
        OptionalIntegrationEvidenceRegistry.record(integration, evidence);
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return integration != null
                && evidence.getDecision() == OptionalIntegrationEvidenceRegistry.Decision.SUPPORTED;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return Collections.emptyList();
    }

    @Override
    public void preApply(String targetClassName,
                         ClassNode targetClass,
                         String mixinClassName,
                         IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName,
                          ClassNode targetClass,
                          String mixinClassName,
                          IMixinInfo mixinInfo) {
    }

    private static IntegrationId integrationForPackage(String mixinPackage) {
        if ("com.mahghuuuls.jawmsintegrations.mixin.qualitytools".equals(mixinPackage)) {
            return IntegrationId.QUALITY_TOOLS;
        }
        if ("com.mahghuuuls.jawmsintegrations.mixin.ancientspellcraft".equals(mixinPackage)) {
            return IntegrationId.ANCIENT_SPELLCRAFT;
        }
        return null;
    }
}
