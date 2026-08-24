package com.mahghuuuls.jawmsintegrations.integration.arsmagica;

/** Owns the single runtime decision that suppresses Ars's Wizardry payment bridge. */
public final class ArsWizardryPaymentPolicy {
    private static final ArsWizardryPaymentPolicy DISABLED = new ArsWizardryPaymentPolicy(false);
    private static final ArsWizardryPaymentPolicy ENABLED = new ArsWizardryPaymentPolicy(true);

    private static volatile ArsWizardryPaymentPolicy installed = DISABLED;

    private final boolean ownsWizardryPayment;

    private ArsWizardryPaymentPolicy(boolean ownsWizardryPayment) {
        this.ownsWizardryPayment = ownsWizardryPayment;
    }

    public static ArsWizardryPaymentPolicy disabled() {
        return DISABLED;
    }

    public static ArsWizardryPaymentPolicy enabled() {
        return ENABLED;
    }

    public static void install(ArsWizardryPaymentPolicy policy) {
        if (policy == null) throw new IllegalArgumentException("policy");
        installed = policy;
    }

    public static ArsWizardryPaymentPolicy active() {
        return installed;
    }

    public boolean ownsWizardryPayment() {
        return ownsWizardryPayment;
    }

    public boolean suppressClientBookkeeping(boolean clientSide) {
        return ownsWizardryPayment && clientSide;
    }
}
