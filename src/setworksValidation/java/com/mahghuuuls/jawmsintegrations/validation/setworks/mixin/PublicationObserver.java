package com.mahghuuuls.jawmsintegrations.validation.setworks.mixin;

import com.mahghuuuls.jawms.api.ManaContribution;
import com.mahghuuuls.jawms.api.ManaContributionPublicationResult;
import com.mahghuuuls.jawms.api.ManaContributionPublicationStatus;
import com.mahghuuuls.jawms.api.ManaProfileGeneration;
import com.mahghuuuls.jawms.api.ManaReadinessProvider;
import com.mahghuuuls.jawmsintegrations.validation.setworks.SetworksReadinessFixture;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.OptionalLong;

@Mixin(value = ManaReadinessProvider.class, remap = false)
abstract class PublicationObserver {
    @Shadow @Final private ResourceLocation id;

    @Inject(method = "completeInitial", at = @At("RETURN"))
    private void initial(EntityPlayer player, ManaProfileGeneration token, ManaContribution group,
                         CallbackInfoReturnable<ManaContributionPublicationResult> ci) {
        setworksvalidation$record("initial", player, token, 0, group.getFlatMaximumMana(), ci.getReturnValue());
    }

    @Inject(method = "suspend", at = @At("HEAD"), cancellable = true)
    private void suppressOnlyForSensitivity(EntityPlayer player, ManaProfileGeneration token, long expected,
                                            CallbackInfoReturnable<ManaContributionPublicationResult> ci) {
        if ("jawmsintegrations:setworks".equals(id.toString()) && SetworksReadinessFixture.suppressSuspension(player)) {
            SetworksReadinessFixture.LOG.info("CASE_SUSPEND_SUPPRESSED player={} expected={}",
                    player.getUniqueID(), expected);
            ci.setReturnValue(new ManaContributionPublicationResult(
                    ManaContributionPublicationStatus.INAPPLICABLE, OptionalLong.empty()));
        }
    }

    @Inject(method = "suspend", at = @At("RETURN"))
    private void suspended(EntityPlayer player, ManaProfileGeneration token, long expected,
                           CallbackInfoReturnable<ManaContributionPublicationResult> ci) {
        setworksvalidation$record("suspend", player, token, expected, 0, ci.getReturnValue());
    }

    @Inject(method = "update", at = @At("RETURN"))
    private void updated(EntityPlayer player, ManaProfileGeneration token, long expected, ManaContribution group,
                         CallbackInfoReturnable<ManaContributionPublicationResult> ci) {
        setworksvalidation$record("update", player, token, expected, group.getFlatMaximumMana(), ci.getReturnValue());
        if ("jawmsintegrations:setworks".equals(id.toString())
                && ci.getReturnValue().getStatus() == ManaContributionPublicationStatus.UPDATE_RECORDED
                && SetworksReadinessFixture.testOldReceipt(player)) {
            ManaContributionPublicationResult stale = ((ManaReadinessProvider) (Object) this)
                    .update(player, token, expected, ManaContribution.EMPTY);
            if (stale.getStatus() != ManaContributionPublicationStatus.REVISION_MISMATCH) {
                throw new IllegalStateException("Expected rejection of an old receipt, got " + stale.getStatus());
            }
        }
    }

    @Unique
    private void setworksvalidation$record(String operation, EntityPlayer player, ManaProfileGeneration token,
                                           long expected, int flatMaximum, ManaContributionPublicationResult result) {
        if (!"jawmsintegrations:setworks".equals(id.toString())) return;
        SetworksReadinessFixture.LOG.info(
                "CASE_PUBLICATION operation={} player={} entity={} dimension={} tick={} token={} expected={} "
                        + "status={} receipt={} flatMaximum={}",
                operation, player.getUniqueID(), player.getEntityId(), player.dimension,
                player.getServer().getTickCounter(), System.identityHashCode(token), expected,
                result.getStatus(), result.getRevision(), flatMaximum);
    }
}
