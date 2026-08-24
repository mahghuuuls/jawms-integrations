package com.mahghuuuls.jawmsintegrations.integration.arsmagica;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArsWizardryPaymentPolicyTest {

    @AfterEach
    void restoreDisabledPolicy() {
        ArsWizardryPaymentPolicy.install(ArsWizardryPaymentPolicy.disabled());
    }

    @Test
    void disabledPolicyPreservesNativeBridge() {
        ArsWizardryPaymentPolicy.install(ArsWizardryPaymentPolicy.disabled());
        assertFalse(ArsWizardryPaymentPolicy.active().ownsWizardryPayment());
        assertSame(ArsWizardryPaymentPolicy.disabled(), ArsWizardryPaymentPolicy.active());
    }

    @Test
    void enabledPolicyMakesJawmsThePaymentOwner() {
        ArsWizardryPaymentPolicy.install(ArsWizardryPaymentPolicy.enabled());
        assertTrue(ArsWizardryPaymentPolicy.active().ownsWizardryPayment());
        assertSame(ArsWizardryPaymentPolicy.enabled(), ArsWizardryPaymentPolicy.active());
    }

    @Test
    void onlyEnabledClientSideBookkeepingIsSuppressed() {
        assertFalse(ArsWizardryPaymentPolicy.disabled().suppressClientBookkeeping(false));
        assertFalse(ArsWizardryPaymentPolicy.disabled().suppressClientBookkeeping(true));
        assertFalse(ArsWizardryPaymentPolicy.enabled().suppressClientBookkeeping(false));
        assertTrue(ArsWizardryPaymentPolicy.enabled().suppressClientBookkeeping(true));
    }

    @Test
    void nullPolicyCannotEraseTheSafeFallback() {
        ArsWizardryPaymentPolicy.install(ArsWizardryPaymentPolicy.disabled());
        assertThrows(IllegalArgumentException.class,
                () -> ArsWizardryPaymentPolicy.install(null));
        assertFalse(ArsWizardryPaymentPolicy.active().ownsWizardryPayment());
    }
}
