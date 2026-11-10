package example.statusworksproof;

import zone.rong.mixinbooter.ILateMixinLoader;

import java.util.Collections;
import java.util.List;

public final class ProofMixinLoader implements ILateMixinLoader {
    @Override
    public List<String> getMixinConfigs() {
        return Boolean.getBoolean("statusworksProof.enabled")
                ? Collections.singletonList("mixins.statusworks-proof.json") : Collections.emptyList();
    }
}
