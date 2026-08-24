package com.mahghuuuls.jawmsintegrations.mixin.arsmagica;

import am2.api.extensions.IEntityExtension;
import com.mahghuuuls.jawmsintegrations.integration.arsmagica.ArsWizardryPaymentPolicy;
import electroblob.wizardry.event.SpellCastEvent;
import electroblob.wizardry.util.SpellModifiers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Map;

@Mixin(targets = "am2.common.compat.electroblob.EBWizardryCompatHandler", remap = false)
public abstract class MixinEBWizardryCompatHandler {
    private static final String PRE =
            "onEBWizSpellCastPre(Lelectroblob/wizardry/event/SpellCastEvent$Pre;)V";
    private static final String POST =
            "onEBWizSpellCastPost(Lelectroblob/wizardry/event/SpellCastEvent$Post;)V";

    @Redirect(method = PRE, at = @At(value = "INVOKE",
            target = "Ljava/util/Map;put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"),
            require = 2, remap = false)
    private Object jawmsintegrations$suppressClientPendingPut(Map<?, ?> pending,
                                                              Object key,
                                                              Object value,
                                                              SpellCastEvent.Pre event) {
        if (event.getCaster() != null && ArsWizardryPaymentPolicy.active()
                .suppressClientBookkeeping(event.getCaster().world.isRemote)) {
            return null;
        }
        @SuppressWarnings("unchecked")
        Map<Object, Object> writable = (Map<Object, Object>) pending;
        return writable.put(key, value);
    }

    @Redirect(method = POST, at = @At(value = "INVOKE",
            target = "Ljava/util/Map;remove(Ljava/lang/Object;)Ljava/lang/Object;"),
            require = 1, remap = false)
    private Object jawmsintegrations$suppressClientPendingRemove(Map<?, ?> pending,
                                                                 Object key,
                                                                 SpellCastEvent.Post event) {
        if (event.getCaster() != null && ArsWizardryPaymentPolicy.active()
                .suppressClientBookkeeping(event.getCaster().world.isRemote)) {
            return null;
        }
        return pending.remove(key);
    }

    @Redirect(method = PRE, at = @At(value = "INVOKE",
            target = "Lam2/api/extensions/IEntityExtension;hasEnoughMana(F)Z"),
            require = 2, remap = false)
    private boolean jawmsintegrations$bypassArsAffordability(IEntityExtension extension,
                                                             float amount) {
        return ArsWizardryPaymentPolicy.active().ownsWizardryPayment()
                || extension.hasEnoughMana(amount);
    }

    @Redirect(method = PRE, at = @At(value = "INVOKE",
            target = "Lelectroblob/wizardry/event/SpellCastEvent$Pre;setCanceled(Z)V",
            ordinal = 1), require = 1, remap = false)
    private void jawmsintegrations$suppressFirstPaymentCancellation(SpellCastEvent.Pre event,
                                                                    boolean canceled) {
        if (!ArsWizardryPaymentPolicy.active().ownsWizardryPayment()) {
            event.setCanceled(canceled);
        }
    }

    @Redirect(method = PRE, at = @At(value = "INVOKE",
            target = "Lelectroblob/wizardry/event/SpellCastEvent$Pre;setCanceled(Z)V",
            ordinal = 2), require = 1, remap = false)
    private void jawmsintegrations$suppressSecondPaymentCancellation(SpellCastEvent.Pre event,
                                                                     boolean canceled) {
        if (!ArsWizardryPaymentPolicy.active().ownsWizardryPayment()) {
            event.setCanceled(canceled);
        }
    }

    @Redirect(method = PRE, at = @At(value = "INVOKE",
            target = "Lelectroblob/wizardry/util/SpellModifiers;set(Ljava/lang/String;FZ)"
                    + "Lelectroblob/wizardry/util/SpellModifiers;", ordinal = 1),
            require = 1, remap = false)
    private SpellModifiers jawmsintegrations$preserveFirstWizardryCost(SpellModifiers modifiers,
                                                                       String key,
                                                                       float value,
                                                                       boolean syncing) {
        if (ArsWizardryPaymentPolicy.active().ownsWizardryPayment()) return modifiers;
        return modifiers.set(key, value, syncing);
    }

    @Redirect(method = PRE, at = @At(value = "INVOKE",
            target = "Lelectroblob/wizardry/util/SpellModifiers;set(Ljava/lang/String;FZ)"
                    + "Lelectroblob/wizardry/util/SpellModifiers;", ordinal = 2),
            require = 1, remap = false)
    private SpellModifiers jawmsintegrations$preserveSecondWizardryCost(SpellModifiers modifiers,
                                                                        String key,
                                                                        float value,
                                                                        boolean syncing) {
        if (ArsWizardryPaymentPolicy.active().ownsWizardryPayment()) return modifiers;
        return modifiers.set(key, value, syncing);
    }

    @Redirect(method = POST, at = @At(value = "INVOKE",
            target = "Lam2/api/extensions/IEntityExtension;deductMana(F)V"),
            require = 1, remap = false)
    private void jawmsintegrations$suppressArsManaDeduction(IEntityExtension extension,
                                                            float amount) {
        if (!ArsWizardryPaymentPolicy.active().ownsWizardryPayment()) {
            extension.deductMana(amount);
        }
    }

    @Redirect(method = POST, at = @At(value = "INVOKE",
            target = "Lam2/api/extensions/IEntityExtension;setCurrentBurnout(F)V"),
            require = 1, remap = false)
    private void jawmsintegrations$suppressArsBurnout(IEntityExtension extension,
                                                      float amount) {
        if (!ArsWizardryPaymentPolicy.active().ownsWizardryPayment()) {
            extension.setCurrentBurnout(amount);
        }
    }
}
