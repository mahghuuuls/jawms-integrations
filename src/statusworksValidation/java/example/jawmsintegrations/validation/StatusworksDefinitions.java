package example.jawmsintegrations.validation;

import com.mahghuuuls.statusworks.api.RegistrationResult;
import com.mahghuuuls.statusworks.api.StatusworksApi;
import com.mahghuuuls.statusworks.api.contribution.ContributionGroup;
import com.mahghuuuls.statusworks.api.definition.Contribution;
import com.mahghuuuls.statusworks.api.definition.CooldownDefinition;
import com.mahghuuuls.statusworks.api.definition.ExpirationMode;
import com.mahghuuuls.statusworks.api.definition.MarkDefinition;
import com.mahghuuuls.statusworks.api.definition.MarkSide;
import com.mahghuuuls.statusworks.api.definition.Ownership;
import com.mahghuuuls.statusworks.api.definition.PoolDefinition;
import com.mahghuuuls.statusworks.api.definition.StatusDefinition;
import com.mahghuuuls.statusworks.api.definition.ValueForm;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.passive.EntityCow;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;

final class StatusworksDefinitions {
    private static final String PREFIX = "jawmsint_authoring_validation:";
    private static final String MAX_FLAT = "jawmsintegrations:max_mana_flat";
    private static final String MAX_PERCENT = "jawmsintegrations:max_mana_percent";
    private static final String REGEN_FLAT = "jawmsintegrations:mana_regen_flat";
    private static final String FLAT = PREFIX + "flat";

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

    static void registerGameplayDefinitions() {
        registerStatus(status(FLAT).contribute(Contribution.type(MAX_FLAT, ValueForm.FIXED, 20.0D)));
        registerStatus(status(PREFIX + "replacement")
                .contribute(Contribution.type(MAX_FLAT, ValueForm.FIXED, 20.0D))
                .contribute(Contribution.type(REGEN_FLAT, ValueForm.FIXED, 1.0D))
                .onApplied(context -> StatusworksApi.statuses().clear(context.entity(), FLAT)));
        registerStatus(status(PREFIX + "stacked").limit(3)
                .contribute(Contribution.type(REGEN_FLAT, ValueForm.PER_STACK, 1.5D)));
        registerStatus(status("ActorMana").limit(3).ownership(Ownership.PER_SOURCE)
                .contribute(Contribution.type(MAX_PERCENT, ValueForm.PER_STACK, 10.0D)));
        registerStatus(status(PREFIX + "percent")
                .contribute(Contribution.type(MAX_PERCENT, ValueForm.FIXED, 10.0D)));
        registerStatus(status(PREFIX + "invalid_aggregate")
                .contribute(Contribution.type(MAX_PERCENT, ValueForm.FIXED, -60.0D))
                .contribute(Contribution.type(MAX_PERCENT, ValueForm.FIXED, -60.0D))
                .contribute(Contribution.type(REGEN_FLAT, ValueForm.FIXED, 3.0D)));
        registerPool(PoolDefinition.builder(PREFIX + "stepped").capacity(128).startAmount(0).updateStep(25)
                .contribute(Contribution.type(MAX_FLAT, ValueForm.PER_AMOUNT, 0.125D))
                .contribute(Contribution.type(REGEN_FLAT, ValueForm.PER_FILL, 2.0D))
                .contribute(Contribution.type("jawmsintegrations:mana_regen_delay_percent", ValueForm.PER_FILL, -20)));
        registerPool(PoolDefinition.builder(PREFIX + "fixed_empty").capacity(100).startAmount(0)
                .contribute(Contribution.type(MAX_FLAT, ValueForm.FIXED, 5.0D)));
        registerPool(PoolDefinition.builder(PREFIX + "fractions").capacity(100).startAmount(0)
                .contribute(Contribution.type(MAX_FLAT, ValueForm.PER_FILL, 1.0D))
                .contribute(Contribution.type(MAX_FLAT, ValueForm.PER_FILL, 1.0D)));
        requireRegistered(StatusworksApi.registerMark(MarkDefinition.builder(PREFIX + "inert_mark")
                .contribute(MarkSide.TARGET, Contribution.type(MAX_FLAT, ValueForm.FIXED, 40.0D))
                .build()));
        if (Boolean.getBoolean("statusworksProof.invalidCooldown")) {
            requireRegistered(StatusworksApi.registerCooldown(CooldownDefinition.builder(PREFIX + "invalid_cooldown")
                    .defaultDuration(20).contribute(Contribution.type(MAX_FLAT, ValueForm.FIXED, 20)).build()));
        }
    }

    static String availability(EntityLivingBase entity) {
        return StatusworksApi.contributions().group(entity, MAX_FLAT).availability().name();
    }

    static String nativeSnapshot(EntityLivingBase entity) {
        ContributionGroup group = StatusworksApi.contributions().group(entity, MAX_FLAT);
        return group.availability() + " values=" + group.values()
                + " flatStatusCount=" + StatusworksApi.statuses().count(entity, FLAT);
    }

    static String verifyNonPlayers(EntityPlayerMP player) {
        FakePlayer fake = FakePlayerFactory.getMinecraft(player.getServerWorld());
        EntityCow cow = new EntityCow(player.getServerWorld());
        cow.setPosition(player.posX + 2, player.posY, player.posZ);
        if (!player.getServerWorld().spawnEntity(cow)) throw new IllegalStateException("Could not spawn test cow");
        try {
            StatusworksApi.statuses().apply(fake, FLAT, 1);
            StatusworksApi.statuses().apply(cow, FLAT, 1);
            return "fakeGroup=" + availability(fake) + " mobGroup=" + availability(cow)
                    + " mobNativeCount=" + StatusworksApi.statuses().count(cow, FLAT)
                    + " playerGroup=" + availability(player);
        } finally {
            StatusworksApi.statuses().clear(fake, FLAT);
            cow.setDead();
        }
    }

    private static StatusDefinition.Builder status(String id) {
        return StatusDefinition.builder(id).expiration(ExpirationMode.NONE);
    }

    private static void registerStatus(StatusDefinition.Builder builder) {
        requireRegistered(StatusworksApi.registerStatus(builder.build()));
    }

    private static void registerPool(PoolDefinition.Builder builder) {
        requireRegistered(StatusworksApi.registerPool(builder.build()));
    }

    private static void requireRegistered(RegistrationResult result) {
        if (!result.isAccepted()) throw new IllegalStateException(result.error());
    }
}
