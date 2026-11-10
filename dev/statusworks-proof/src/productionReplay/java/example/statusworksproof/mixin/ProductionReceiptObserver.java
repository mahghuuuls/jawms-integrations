package example.statusworksproof.mixin;

import com.mahghuuuls.jawms.api.ManaContribution;
import com.mahghuuuls.jawms.api.ManaContributionPublicationResult;
import com.mahghuuuls.jawms.api.ManaProfileGeneration;
import com.mahghuuuls.jawms.api.ManaReadinessProvider;
import example.statusworksproof.NativeProof;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ManaReadinessProvider.class, remap = false)
public abstract class ProductionReceiptObserver {
    @Shadow @Final private ResourceLocation id;

    @Inject(method = "completeInitial", at = @At("RETURN"), require = 1)
    private void observeInitial(EntityPlayer player, ManaProfileGeneration generation, ManaContribution contribution,
                                CallbackInfoReturnable<ManaContributionPublicationResult> callback) {
        NativeProof.observeProductionReceipt(id, player, generation, 0, contribution, callback.getReturnValue());
    }

    @Inject(method = "suspend", at = @At("RETURN"), require = 1)
    private void observeSuspension(EntityPlayer player, ManaProfileGeneration generation, long expectedRevision,
                                   CallbackInfoReturnable<ManaContributionPublicationResult> callback) {
        NativeProof.observeProductionReceipt(id, player, generation, expectedRevision, null, callback.getReturnValue());
    }

    @Inject(method = "update", at = @At("RETURN"), require = 1)
    private void observeUpdate(EntityPlayer player, ManaProfileGeneration generation, long expectedRevision,
                               ManaContribution contribution,
                               CallbackInfoReturnable<ManaContributionPublicationResult> callback) {
        NativeProof.observeProductionReceipt(id, player, generation, expectedRevision, contribution,
                callback.getReturnValue());
    }
}
