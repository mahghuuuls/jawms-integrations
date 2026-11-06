package com.mahghuuuls.jawmsintegrations.validation.setworks.mixin;

import com.mahghuuuls.jawms.api.ManaProfileReadiness;
import com.mahghuuuls.jawms.api.client.ClientManaApi;
import com.mahghuuuls.jawms.api.client.ClientManaSnapshot;
import com.mahghuuuls.jawms.internal.mana.ManaReadinessMessage;
import com.mahghuuuls.jawmsintegrations.validation.setworks.SetworksReadinessFixture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(targets = "com.mahghuuuls.jawms.client.ClientManaState", remap = false)
abstract class ClientAcceptanceObserver {
    @Shadow private static UUID managedGeneration;
    @Shadow private static long managedSequence;
    @Unique private static UUID setworksvalidation$lastGeneration;
    @Unique private static ManaProfileReadiness setworksvalidation$lastPhase;

    @Inject(method = "acceptManaged", at = @At("RETURN"))
    private static void observeAcceptedPacket(ManaReadinessMessage message, Object handler, Object activeConnection,
                                              CallbackInfo ci) {
        if (handler == null || handler != activeConnection || message.getSequence() != managedSequence
                || !message.getGeneration().equals(managedGeneration)) return;
        ClientManaSnapshot snapshot = ClientManaApi.getSnapshot();
        ManaProfileReadiness phase = message.getPhase();
        if ((phase == ManaProfileReadiness.READY) != snapshot.isAvailable()) return;
        if (managedGeneration.equals(setworksvalidation$lastGeneration) && phase == setworksvalidation$lastPhase) return;
        setworksvalidation$lastGeneration = managedGeneration;
        setworksvalidation$lastPhase = phase;
        SetworksReadinessFixture.LOG.info(
                "CASE_CLIENT generation={} sequence={} phase={} available={} current={} maximum={} revision={}",
                managedGeneration, managedSequence, phase, snapshot.isAvailable(), snapshot.getCurrentMana(),
                snapshot.getMaximumMana(), snapshot.getRevision());
    }
}
