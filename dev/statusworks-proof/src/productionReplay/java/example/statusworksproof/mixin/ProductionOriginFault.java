package example.statusworksproof.mixin;

import com.mahghuuuls.statusworks.api.contribution.ContributionGroup;
import example.statusworksproof.NativeProof;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "com.mahghuuuls.statusworks.core.contribution.ContributionManager", remap = false)
public abstract class ProductionOriginFault {
    @Inject(method = "group", at = @At("RETURN"), cancellable = true, require = 1)
    private void supplyArmedUnknownOrigin(Entity entity, String type,
                                          CallbackInfoReturnable<ContributionGroup> callback) {
        ContributionGroup original = callback.getReturnValue();
        ContributionGroup observed = NativeProof.productionRead(entity, type, original);
        if (observed != original) callback.setReturnValue(observed);
    }
}
