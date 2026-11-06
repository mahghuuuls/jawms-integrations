package com.mahghuuuls.jawmsintegrations.integration.setworks;

import com.mahghuuuls.jawmsintegrations.Tags;
import com.mahghuuuls.jawmsintegrations.integration.stat.ManaStatProjection;
import com.mahghuuuls.jawmsintegrations.integration.stat.ManaStatProjection.Stat;
import com.mahghuuuls.setworks.api.CombinationRule;
import com.mahghuuuls.setworks.api.IEffectKind;
import com.mahghuuuls.setworks.api.RegistrationResult;
import com.mahghuuuls.setworks.api.SetworksApi;
import com.mahghuuuls.setworks.api.WithdrawReason;
import net.minecraft.entity.player.EntityPlayerMP;

import java.math.BigDecimal;
import java.util.Map;

/** Native authoring and callbacks for a pack-authored mana effect. */
final class SetworksManaEffect implements IEffectKind {
    private Callbacks callbacks;

    static SetworksBootstrap.Authoring register() {
        SetworksManaEffect effect = new SetworksManaEffect();
        RegistrationResult result = SetworksApi.registerEffectKind(effect);
        if (!result.isOk()) throw new IllegalStateException(result.message());
        return () -> SetworksGameplay.activate(effect);
    }

    void install(Callbacks callbacks) {
        this.callbacks = callbacks;
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
        if (callbacks != null) callbacks.apply(player, instanceId, params);
    }

    @Override
    public void withdraw(EntityPlayerMP player, String instanceId, Map<String, String> params) {
        withdraw(player, instanceId, params, WithdrawReason.ENDED);
    }

    @Override
    public void withdraw(EntityPlayerMP player, String instanceId, Map<String, String> params, WithdrawReason reason) {
        if (callbacks != null) callbacks.withdraw(player, instanceId, reason);
    }

    @Override
    public void afterGroup(EntityPlayerMP player) {
        if (callbacks != null) callbacks.afterGroup(player);
    }

    static Parameters parse(Map<String, String> params) {
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

    static final class Parameters {
        final Stat stat;
        final boolean percent;
        final double amount;

        private Parameters(Stat stat, boolean percent, double amount) {
            this.stat = stat;
            this.percent = percent;
            this.amount = amount;
        }
    }

    interface Callbacks {
        void apply(EntityPlayerMP player, String instanceId, Map<String, String> params);
        void withdraw(EntityPlayerMP player, String instanceId, WithdrawReason reason);
        void afterGroup(EntityPlayerMP player);
    }
}
