package com.iafenvoy.mxt.data.creature;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

/**
 * One order an owner can give a bound creature: its identity, the name it shows and whether it stays in force.
 *
 * <p>A class rather than an enum on purpose. An order belongs to the creature that answers it, so a content mod
 * adds one by making one of these and registering it - it never has to edit a list only the framework could
 * change. The ones the framework ships live in {@link ContractBehaviors}, which is also the id lookup both sides
 * read a stored order back through.</p>
 *
 * <p>{@code momentary} says the order happens once instead of becoming the state in force: a recall is offered by
 * the same wheel as "stay", but it must not replace what the creature was told to do.</p>
 */
public record ContractBehavior(Identifier id, boolean momentary) {
    /**
     * The name shown for this order, keyed {@code contract.mxt.behavior.<namespace>.<path>}.
     */
    public Component name() {
        return Component.translatable("contract.mxt.behavior." + this.id.toLanguageKey());
    }

    // Identity is the id, not the object: an order read back from a save is the same order it was written as.
    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof ContractBehavior behavior && this.id.equals(behavior.id);
    }

    @Override
    public int hashCode() {
        return this.id.hashCode();
    }

    @Override
    public @NonNull String toString() {
        return this.id.toString();
    }
}
