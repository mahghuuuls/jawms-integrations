package com.mahghuuuls.jawmsintegrations.integration.statusworks;

import com.mahghuuuls.jawms.api.ConfiguredFlaskSnapshot;
import com.mahghuuuls.jawms.api.ManaApi;
import com.mahghuuuls.jawms.api.ManaContribution;
import com.mahghuuuls.jawms.api.ManaContributionDisposition;
import com.mahghuuuls.jawms.api.ManaContributionExplanation;
import com.mahghuuuls.jawms.api.ManaContributionPublicationResult;
import com.mahghuuuls.jawms.api.ManaContributionPublicationStatus;
import com.mahghuuuls.jawms.api.ManaFirstAcceptanceVerifier;
import com.mahghuuuls.jawms.api.ManaProfileExplanation;
import com.mahghuuuls.jawms.api.ManaProfileGeneration;
import com.mahghuuuls.jawms.api.ManaProfileReadiness;
import com.mahghuuuls.jawms.api.ManaReadinessProvider;
import com.mahghuuuls.statusworks.api.ContributionService;
import com.mahghuuuls.statusworks.api.StatusworksApi;
import com.mahghuuuls.statusworks.api.contribution.ContributionGroup;
import com.mahghuuuls.statusworks.api.contribution.ContributionGroupType;
import com.mahghuuuls.statusworks.api.contribution.ContributionType;
import com.mahghuuuls.statusworks.api.contribution.GroupAvailability;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;

/** Links the gameplay contract only after both metadata gates pass. */
final class StatusworksGameplayContract {
    private StatusworksGameplayContract() {
    }

    static void verifyBound() throws ReflectiveOperationException {
        verify();
        if (StatusworksApi.contributions() == null) throw new NoSuchMethodException("Bound contribution service");
    }

    static void verify() throws ReflectiveOperationException {
        if (!ContributionGroupType.class.isInterface() || !ContributionService.class.isInterface()
                || !ContributionType.class.isAssignableFrom(ContributionGroupType.class)
                || !ManaFirstAcceptanceVerifier.class.isInterface()) {
            throw new NoSuchMethodException("Compatible contribution group and readiness verifier interfaces");
        }
        requireMethod(StatusworksApi.class, "contributions", ContributionService.class, true);
        requireMethod(ContributionService.class, "group", ContributionGroup.class, false, Entity.class, String.class);
        requireMethod(ContributionGroup.class, "availability", GroupAvailability.class, false);
        requireMethod(ContributionGroup.class, "values", Map.class, false);
        requireMethod(ContributionGroupType.class, "invalidate", boolean.class, false, EntityLivingBase.class);
        requireMethod(ContributionGroupType.class, "settled", void.class, false, EntityLivingBase.class, Map.class);
        requireConstants(GroupAvailability.class, "UNAVAILABLE", "FAILED", "PENDING", "UPDATING", "AVAILABLE");

        verifyManaApi(ManaApi.class);
        requireMethod(ManaFirstAcceptanceVerifier.class, "verify", boolean.class, false,
                EntityPlayer.class, ManaProfileGeneration.class, long.class, ManaContribution.class);
        verifyReadinessProvider(ManaReadinessProvider.class);
        requireMethod(ManaContributionPublicationResult.class, "getStatus", ManaContributionPublicationStatus.class,
                false);
        requireMethod(ManaContributionPublicationResult.class, "getRevision", OptionalLong.class, false);
        requireConstants(ManaContributionPublicationStatus.class, "INITIAL_RECORDED", "SUSPENDED", "UPDATE_RECORDED",
                "ALREADY_ACCEPTED", "BUSY", "STALE_GENERATION");
        requireConstants(ManaProfileReadiness.class, "LEGACY", "READY", "PENDING", "UNAVAILABLE");
        requireMethod(ManaProfileExplanation.class, "getContributions", List.class, false);
        requireMethod(ManaContributionExplanation.class, "getSourceId", ResourceLocation.class, false);
        requireMethod(ManaContributionExplanation.class, "getDisposition", ManaContributionDisposition.class, false);
        requireMethod(ManaContributionExplanation.class, "hasAcceptedContribution", boolean.class, false);
        requireMethod(ManaContributionExplanation.class, "getAcceptedContribution", ManaContribution.class, false);
        requireConstants(ManaContributionDisposition.class, "EMPTY", "DISABLED");
        requireMethod(ConfiguredFlaskSnapshot.class, "getRevision", long.class, false);
    }

    static void verifyManaApi(Class<?> api) throws NoSuchMethodException {
        requireMethod(api, "registerReadinessProvider", ManaReadinessProvider.class, true,
                ResourceLocation.class, ManaFirstAcceptanceVerifier.class);
        requireMethod(api, "explainManaProfile", ManaProfileExplanation.class, true, EntityPlayer.class);
        requireMethod(api, "getProfileReadiness", ManaProfileReadiness.class, true, EntityPlayer.class);
        requireMethod(api, "getConfiguredFlaskSnapshot", ConfiguredFlaskSnapshot.class, true);
    }

    static void verifyReadinessProvider(Class<?> provider) throws NoSuchMethodException {
        requireMethod(provider, "getGeneration", Optional.class, false, EntityPlayer.class);
        requireMethod(provider, "completeInitial", ManaContributionPublicationResult.class, false,
                EntityPlayer.class, ManaProfileGeneration.class, ManaContribution.class);
        requireMethod(provider, "suspend", ManaContributionPublicationResult.class, false,
                EntityPlayer.class, ManaProfileGeneration.class, long.class);
        requireMethod(provider, "update", ManaContributionPublicationResult.class, false,
                EntityPlayer.class, ManaProfileGeneration.class, long.class, ManaContribution.class);
    }

    private static void requireMethod(Class<?> owner, String name, Class<?> result, boolean staticMethod,
                                      Class<?>... parameters)
            throws NoSuchMethodException {
        Method method = owner.getMethod(name, parameters);
        if (method.getReturnType() != result || Modifier.isStatic(method.getModifiers()) != staticMethod) {
            throw new NoSuchMethodException("Incompatible signature for " + owner.getName() + "." + name);
        }
    }

    private static void requireConstants(Class<?> owner, String... names) throws NoSuchFieldException {
        for (String name : names) {
            if (!owner.getField(name).isEnumConstant()) {
                throw new NoSuchFieldException(owner.getName() + "." + name + " is not an enum constant");
            }
        }
    }
}
