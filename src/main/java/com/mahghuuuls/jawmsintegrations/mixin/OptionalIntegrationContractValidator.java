package com.mahghuuuls.jawmsintegrations.mixin;

import com.mahghuuuls.jawmsintegrations.integration.IntegrationId;
import com.mahghuuuls.jawmsintegrations.integration.OptionalIntegrationEvidenceRegistry;
import com.mahghuuuls.jawmsintegrations.mixin.ancientspellcraft.AncientSpellcraftMixinContract;
import com.mahghuuuls.jawmsintegrations.mixin.qualitytools.QualityToolsMixinContract;

import java.io.IOException;

/** Dispatches no-load validation to the owner of each optional Mixin contract. */
final class OptionalIntegrationContractValidator {
    private OptionalIntegrationContractValidator() {
    }

    static OptionalIntegrationEvidenceRegistry.Evidence validate(ClassLoader loader,
            IntegrationId integration, OptionalIntegrationEvidenceRegistry.Evidence evidence) {
        if (evidence.getDecision() != OptionalIntegrationEvidenceRegistry.Decision.SUPPORTED) return evidence;
        try {
            switch (integration) {
                case QUALITY_TOOLS: QualityToolsMixinContract.validate(loader); break;
                case ANCIENT_SPELLCRAFT: AncientSpellcraftMixinContract.validate(loader); break;
                case CRAFTTWEAKER:
                case ARS_MAGICA: break;
                default: throw new IllegalStateException("Unhandled integration " + integration);
            }
            return evidence;
        } catch (BytecodeContract.Violation | IOException | RuntimeException | LinkageError failure) {
            String message = failure.getMessage();
            if (message == null || message.trim().isEmpty()) message = failure.getClass().getSimpleName();
            return OptionalIntegrationEvidenceRegistry.Evidence.error(evidence.getDetectedVersion(),
                    "Adapter contract failed: " + message);
        }
    }
}
