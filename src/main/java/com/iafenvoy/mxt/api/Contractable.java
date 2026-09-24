package com.iafenvoy.mxt.api;

import com.iafenvoy.mxt.data.creature.ContractContext;
import com.iafenvoy.mxt.data.creature.ContractTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;

/**
 * A creature that may sign a contract. Implementing this interface is the whole of eligibility: no datapack
 * entry can turn an entity type into a contract subject, so what the framework may bind is a code fact.
 *
 * <p>It is an {@link OwnableEntity} as well, so "who owns this" is the vanilla owner logic and the storage is
 * the creature's own: a class that already keeps an owner (a tamed animal, or any creature with a field of its
 * own) answers {@code getOwnerReference} and writes it back itself. The framework never keeps an owner of its
 * own, so there is one answer to that question and nothing to keep in step.</p>
 */
public interface Contractable extends OwnableEntity {
    /**
     * Records the owner of a contract that is being signed. The creature writes it wherever its class keeps
     * owners - the vanilla setter for a tamed animal, its own field or saved data otherwise - because that is
     * what its own {@code getOwnerReference} reads back.
     */
    void setContractOwner(LivingEntity owner);

    // Whether this creature signs the given contract type. The default reads that contract type's own entity
    // type tag, which a datapack writes to narrow the list; a contract type without the tag is unrestricted.
    default boolean acceptsContract(ContractContext context) {
        return ContractTags.accepts(context);
    }

    // After the owner is written, so both the creature and its owner answer with the new state here.
    default void onContractBound(ContractContext context) {
    }

    // The owner or an operator released the creature from the contract while it lives. Death has its own hook,
    // so neither hook has to guess which of the two happened. Clearing the owner is the creature's own choice:
    // ending a contract is not the same act as forgetting who tamed it.
    default void onContractReleased(ContractContext context) {
    }

    // The creature died and the contract went with it.
    default void onContractDeath(ContractContext context) {
    }
}
