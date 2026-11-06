package com.mahghuuuls.jawmsintegrations.integration.setworks;

import com.mahghuuuls.jawmsintegrations.Tags;
import com.mahghuuuls.jawmsintegrations.integration.stat.ManaStatProjection;
import com.mahghuuuls.jawmsintegrations.integration.stat.ManaStatProjection.Stat;
import com.mahghuuuls.setworks.api.CombinationRule;
import com.mahghuuuls.setworks.api.IEffectKind;
import com.mahghuuuls.setworks.api.RegistrationResult;
import com.mahghuuuls.setworks.api.SetworksApi;
import net.minecraft.entity.player.EntityPlayerMP;

import java.math.BigDecimal;
import java.util.Map;

/** Native metadata for a pack-authored mana effect. */
final class SetworksManaEffect implements IEffectKind {
    static void register() {
        RegistrationResult result = SetworksApi.registerEffectKind(new SetworksManaEffect());
        if (!result.isOk()) throw new IllegalStateException(result.message());
    }

    @Override
    public String id() {
        return Tags.MOD_ID + ":mana";
    }

    @Override
    public CombinationRule combinationRule() {
        return CombinationRule.SUM;
    }

    @Override
    public String validate(Map<String, String> params) {
        try {
            parse(params);
            return null;
        } catch (IllegalArgumentException exception) {
            return exception.getMessage();
        }
    }

    @Override
    public String describe(Map<String, String> params) {
        try {
            Parameters effect = parse(params);
            String amount = BigDecimal.valueOf(effect.amount).stripTrailingZeros().toPlainString();
            String unit = effect.stat.unit();
            String suffix = unit.isEmpty() ? "" : " " + unit;
            String sign = effect.amount > 0 ? "+" : "";
            // Setworks resolves embedded language keys in generated client tooltips.
            return sign + amount + (effect.percent ? "%" : suffix)
                    + " ${property." + effect.stat.getId().replace(':', '.') + "}";
        } catch (IllegalArgumentException exception) {
            return id() + " (invalid: " + exception.getMessage() + ")";
        }
    }

    @Override
    public void apply(EntityPlayerMP player, String instanceId, Map<String, String> params) {
        // The native definition can be registered with mana gameplay inactive.
    }

    @Override
    public void withdraw(EntityPlayerMP player, String instanceId, Map<String, String> params) {
        // An inert definition owns no applied contribution.
    }

    private static Parameters parse(Map<String, String> params) {
        if (params == null || params.size() != 3 || params.get("stat") == null
                || params.get("operation") == null || params.get("amount") == null) {
            throw new IllegalArgumentException("Expected exactly stat, operation and amount");
        }
        Stat stat = Stat.fromName(params.get("stat"));
        if (stat == null) throw new IllegalArgumentException("Unknown mana stat: " + params.get("stat"));
        String operation = params.get("operation");
        if (!"flat".equals(operation) && !"percent".equals(operation)) {
            throw new IllegalArgumentException("Expected operation flat or percent: " + operation);
        }
        boolean percent = "percent".equals(operation);
        BigDecimal authored;
        try {
            authored = new BigDecimal(params.get("amount").trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Expected a finite numeric amount", exception);
        }
        double amount = authored.doubleValue();
        ManaStatProjection.Reason reason = ManaStatProjection.invalidAmount(stat, percent, amount);
        if (reason != null) {
            throw new IllegalArgumentException("Invalid amount for " + stat.getId() + ": " + reason);
        }
        // A decimal just below a reduction or efficiency floor can round onto the bound.
        if ((percent || !stat.supportsPercent())
                && authored.compareTo(BigDecimal.valueOf(stat.minimum(percent))) < 0) {
            throw new IllegalArgumentException("Amount is below the minimum for " + stat.getId());
        }
        if (stat == Stat.MAX_MANA && !percent) {
            try {
                authored.intValueExact();
            } catch (ArithmeticException exception) {
                throw new IllegalArgumentException("Flat maximum mana must be a whole signed integer", exception);
            }
        }
        return new Parameters(stat, percent, amount);
    }

    private static final class Parameters {
        private final Stat stat;
        private final boolean percent;
        private final double amount;

        private Parameters(Stat stat, boolean percent, double amount) {
            this.stat = stat;
            this.percent = percent;
            this.amount = amount;
        }
    }
}
