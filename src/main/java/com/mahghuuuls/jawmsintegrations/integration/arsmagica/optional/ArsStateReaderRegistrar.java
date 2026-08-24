package com.mahghuuuls.jawmsintegrations.integration.arsmagica.optional;

import am2.api.ArsMagicaAPI;
import am2.api.affinity.Affinity;
import am2.api.extensions.IEntityExtension;
import am2.api.extensions.ISkillData;
import am2.common.extensions.AffinityData;
import am2.common.extensions.EntityExtension;
import am2.common.extensions.SkillData;
import am2.common.skill.Discipline;
import com.mahghuuuls.jawmsintegrations.integration.arsmagica.ArsPlayerStateInspectionService;
import net.minecraft.entity.player.EntityPlayer;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/** Ars-linked implementation loaded only after the integration reaches ACTIVE. */
public final class ArsStateReaderRegistrar {

    private ArsStateReaderRegistrar() {
    }

    public static void register() {
        ArsPlayerStateInspectionService.install(ArsStateReaderRegistrar::inspect);
    }

    private static List<String> inspect(EntityPlayer player) {
        List<String> lines = new ArrayList<>();
        IEntityExtension extension = EntityExtension.For(player);
        if (extension == null) {
            lines.add("Ars state: capability unavailable");
            return lines;
        }
        lines.add("Ars state: mana=" + number(extension.getCurrentMana())
                + "/" + number(extension.getMaxMana())
                + ", burnout=" + number(extension.getCurrentBurnout())
                + "/" + number(extension.getMaxBurnout())
                + ", level=" + extension.getCurrentLevel()
                + ", magic XP=" + number(extension.getCurrentXP())
                + "/" + number(extension.getMaxXP()));

        AffinityData affinities = AffinityData.For(player);
        List<String> nonZero = new ArrayList<>();
        if (affinities != null) {
            for (Affinity affinity : ArsMagicaAPI.getAffinityRegistry().getValuesCollection()) {
                double percent = affinities.getAffinityDepth(affinity) * 100.0D;
                if (percent != 0.0D) {
                    nonZero.add(affinity.getName() + "=" + number(percent) + "%");
                }
            }
        }
        lines.add("Ars affinities: " + (nonZero.isEmpty() ? "none" : String.join(", ", nonZero)));

        ISkillData skills = SkillData.For(player);
        List<String> disciplines = new ArrayList<>();
        if (skills != null) {
            for (Discipline discipline : Discipline.values()) {
                int level = skills.getDisciplineLevel(discipline);
                if (level != 0) {
                    disciplines.add(discipline.getName() + "=" + level);
                }
            }
        }
        lines.add("Ars disciplines: " + (disciplines.isEmpty()
                ? "none" : String.join(", ", disciplines)));
        return lines;
    }

    private static String number(double value) {
        if (!Double.isFinite(value)) {
            return Double.toString(value);
        }
        return BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString();
    }
}
