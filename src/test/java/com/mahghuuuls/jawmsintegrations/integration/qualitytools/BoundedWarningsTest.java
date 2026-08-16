package com.mahghuuuls.jawmsintegrations.integration.qualitytools;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BoundedWarningsTest {

    @Test
    void repeatedProblemsAreLoggedOnlyOncePerFiniteProblemCategory() {
        QualityToolsIntegration.BoundedWarnings warnings = new QualityToolsIntegration.BoundedWarnings();

        warnings.warn(QualityAttributeProjection.Problem.UNSUPPORTED_OPERATION,
                QualityAttributeProjection.MAX_MANA_PERCENT, 1, 5.0D);
        warnings.warn(QualityAttributeProjection.Problem.UNSUPPORTED_OPERATION,
                QualityAttributeProjection.MANA_REGEN_PERCENT, 2, 500.0D);
        warnings.warn(QualityAttributeProjection.Problem.NON_FINITE_VALUE,
                QualityAttributeProjection.MAX_MANA_PERCENT, 0, Double.NaN);

        assertEquals(2, warnings.getEmissionCount());
    }
}
