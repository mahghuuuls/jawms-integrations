package example.statusworksproof.mixin;

import com.mahghuuuls.jawms.api.client.ClientManaApi;
import com.mahghuuuls.jawms.api.client.ClientManaSnapshot;
import com.mahghuuuls.jawms.client.ClientManaState;
import com.mahghuuuls.jawms.internal.mana.ManaReadinessMessage;
import example.statusworksproof.NativeProof;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ClientManaState.class, remap = false)
public abstract class AcceptedManaObserver {
    // The final return is after JAWMS has admitted the packet and applied its phase or payload.
    @Inject(method = "acceptManaged", at = @At("TAIL"), require = 1)
    private static void recordAcceptedPacket(ManaReadinessMessage message, Object handler, Object activeConnection,
            CallbackInfo callback) {
        ClientManaSnapshot snapshot = ClientManaApi.getSnapshot();
        String value = snapshot.isAvailable() ? snapshot.getCurrentMana() + "/" + snapshot.getMaximumMana() : "-";
        NativeProof.LOG.info("PROOF_CLIENT generation={} connection={} sequence={} phase={} accepted={}",
                message.getGeneration(), System.identityHashCode(handler), message.getSequence(),
                message.getPhase(), value);
    }
}
