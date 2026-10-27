package dev.goshadows.createtrainssable.integration;

import java.util.Optional;

/** Used when Sable is missing: Create keeps full control and no conversion is attempted. */
public final class DegradedBackend implements PhysicsBackend {

    private final String reason;

    public DegradedBackend(String reason) {
        this.reason = reason;
    }

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public String description() {
        return "degraded (vanilla Create): " + reason;
    }

    @Override
    public Optional<AttachedBody> attach(ConversionRequest request) {
        return Optional.empty();
    }

    @Override
    public void shutdown() {
    }
}
