package com.mahghuuuls.jawmsintegrations.integration;

import java.util.EnumMap;
import java.util.Map;

/** Bridges bootstrap metadata evidence into normal Forge lifecycle status. */
public final class OptionalIntegrationEvidenceRegistry {

    private static final Map<IntegrationId, Evidence> EVIDENCE = new EnumMap<>(IntegrationId.class);

    private OptionalIntegrationEvidenceRegistry() {
    }

    public static synchronized void record(IntegrationId integration, Evidence evidence) {
        if (integration == null || evidence == null) {
            throw new IllegalArgumentException("Integration evidence must not be null");
        }
        EVIDENCE.put(integration, evidence);
    }

    public static synchronized Evidence get(IntegrationId integration) {
        Evidence evidence = EVIDENCE.get(integration);
        return evidence == null ? Evidence.unknown() : evidence;
    }

    public enum Decision {
        UNKNOWN,
        ABSENT,
        SUPPORTED,
        UNSUPPORTED,
        ERROR
    }

    public static final class Evidence {
        private final Decision decision;
        private final String detectedVersion;
        private final String detail;

        private Evidence(Decision decision, String detectedVersion, String detail) {
            this.decision = decision;
            this.detectedVersion = detectedVersion;
            this.detail = detail;
        }

        public static Evidence unknown() {
            return new Evidence(Decision.UNKNOWN, null, "Bootstrap gate did not publish evidence");
        }

        public static Evidence absent() {
            return new Evidence(Decision.ABSENT, null,
                    "Optional dependency was not visible during bootstrap");
        }

        public static Evidence supported(String version) {
            return new Evidence(Decision.SUPPORTED, version,
                    "A version meeting the minimum was visible during bootstrap");
        }

        public static Evidence unsupported(String version) {
            return new Evidence(Decision.UNSUPPORTED, version,
                    "Unsupported version was visible during bootstrap");
        }

        public static Evidence error(String detail) {
            return new Evidence(Decision.ERROR, null, detail);
        }

        public static Evidence error(String version, String detail) {
            return new Evidence(Decision.ERROR, version, detail);
        }

        public Decision getDecision() {
            return decision;
        }

        public String getDetectedVersion() {
            return detectedVersion;
        }

        public String getDetail() {
            return detail;
        }
    }
}
