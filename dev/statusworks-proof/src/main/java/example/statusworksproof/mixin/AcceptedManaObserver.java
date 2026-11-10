package example.statusworksproof.mixin;

import com.mahghuuuls.jawms.api.client.ClientManaApi;
import com.mahghuuuls.jawms.api.client.ClientManaSnapshot;
import com.mahghuuuls.jawms.client.ClientManaState;
import com.mahghuuuls.jawms.internal.mana.ManaReadinessMessage;
import electroblob.wizardry.constants.Element;
import org.apache.logging.log4j.LogManager;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

@Mixin(value = ClientManaState.class, remap = false)
public abstract class AcceptedManaObserver {
    @Shadow private static Object viewConnection;

    // The final return is after JAWMS has admitted the packet and applied its phase or payload.
    @Inject(method = "acceptManaged", at = @At("TAIL"), require = 1)
    private static void recordAcceptedPacket(ManaReadinessMessage message, Object handler, Object activeConnection,
            CallbackInfo callback) {
        ClientManaSnapshot snapshot = ClientManaApi.getSnapshot();
        String value = snapshot.isAvailable() ? snapshot.getCurrentMana() + "/" + snapshot.getMaximumMana() : "-";
        LogManager.getLogger("statusworks_native_proof").info("PROOF_CLIENT generation={} connection={} sequence={} phase={} accepted={}",
                message.getGeneration(), System.identityHashCode(handler), message.getSequence(),
                message.getPhase(), value);
    }

    // Legacy and managed packets both reach this write when their payload becomes visible.
    @Inject(method = "accept(IIDJDLjava/util/Map;DLjava/util/Map;)V", at = @At(value = "FIELD",
            target = "Lcom/mahghuuuls/jawms/client/ClientManaState;publishedSnapshot:"
                    + "Lcom/mahghuuuls/jawms/client/ClientManaState$PublishedSnapshot;",
            opcode = Opcodes.PUTSTATIC, shift = At.Shift.AFTER), require = 1)
    private static void recordVisiblePayload(int current, int maximum, double regeneration, long revision,
            double globalEfficiency, Map<Element, Double> elementEfficiency, double globalCostMultiplier,
            Map<Element, Double> elementCostMultiplier, CallbackInfo callback) {
        LogManager.getLogger("statusworks_native_proof").info("PROOF_CLIENT_PAYLOAD connection={} revision={} accepted={}/{}",
                System.identityHashCode(viewConnection), revision, current, maximum);
    }
}
