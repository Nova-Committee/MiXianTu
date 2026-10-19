package com.iafenvoy.mxt.runtime.failure;

import net.minecraft.resources.Identifier;

/**
 * A refusal that names the one resource that ran out, for the modules whose reports are per resource. This is a
 * sub-interface rather than part of {@link Failure} because most modules have nothing to name.
 */
public record ResourcedFailure(Failure failure, Identifier resource) implements Failure {
    @Override
    public String name() {
        return this.failure.name();
    }

    @Override
    public FailureSource source() {
        return this.failure.source();
    }
}
