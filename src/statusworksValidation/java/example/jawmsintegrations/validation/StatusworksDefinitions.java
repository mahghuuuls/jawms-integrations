package example.jawmsintegrations.validation;

import com.mahghuuuls.statusworks.api.RegistrationResult;
import com.mahghuuuls.statusworks.api.StatusworksApi;
import com.mahghuuuls.statusworks.api.definition.Contribution;
import com.mahghuuuls.statusworks.api.definition.ExpirationMode;
import com.mahghuuuls.statusworks.api.definition.PoolDefinition;
import com.mahghuuuls.statusworks.api.definition.StatusDefinition;
import com.mahghuuuls.statusworks.api.definition.ValueForm;

final class StatusworksDefinitions {
    private StatusworksDefinitions() {
    }

    static void register() {
        String[] types = {
                "max_mana_flat", "max_mana_percent", "mana_regen_flat", "mana_regen_percent",
                "mana_regen_delay_flat", "mana_regen_delay_percent", "spell_efficiency",
                "spell_efficiency_magic", "spell_efficiency_fire", "spell_efficiency_ice",
                "spell_efficiency_lightning", "spell_efficiency_necromancy", "spell_efficiency_earth",
                "spell_efficiency_sorcery", "spell_efficiency_healing"
        };
        StatusDefinition.Builder status = StatusDefinition.builder("jawmsint_authoring_validation:java_status")
                .expiration(ExpirationMode.NONE);
        for (String type : types) {
            status.contribute(Contribution.type("jawmsintegrations:" + type, ValueForm.FIXED, 20.0D));
        }
        requireRegistered(StatusworksApi.registerStatus(status.build()));
        requireRegistered(StatusworksApi.registerPool(PoolDefinition.builder("jawmsint_authoring_validation:java_pool")
                .capacity(100.0D).startAmount(50.0D)
                .contribute(Contribution.type("jawmsintegrations:mana_regen_delay_percent", ValueForm.PER_FILL, -10.0D))
                .build()));
    }

    private static void requireRegistered(RegistrationResult result) {
        if (!result.isAccepted()) throw new IllegalStateException(result.error());
    }
}
