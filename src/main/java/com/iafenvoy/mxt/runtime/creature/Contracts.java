package com.iafenvoy.mxt.runtime.creature;

import com.iafenvoy.mxt.api.CaptureListener;
import com.iafenvoy.mxt.api.Contractable;
import com.iafenvoy.mxt.api.ContractOperations;
import com.iafenvoy.mxt.data.creature.ContractContext;
import com.iafenvoy.mxt.data.creature.ContractType;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/**
 * The single place that answers "is this entity a contract subject, who owns it, and what does it do". Every
 * caller - the scroll, the bell, the bag, the command, both event bridges - goes through here, so the interface
 * checks and the owner logic exist once instead of in each caller.
 */
public final class Contracts {
    private Contracts() {
    }

    public static Optional<Contractable> of(Entity entity) {
        return entity instanceof Contractable value ? Optional.of(value) : Optional.empty();
    }

    public static Optional<ContractOperations> operations(Entity entity) {
        return entity instanceof ContractOperations value ? Optional.of(value) : Optional.empty();
    }

    public static Optional<CaptureListener> captureListener(Entity entity) {
        return entity instanceof CaptureListener value ? Optional.of(value) : Optional.empty();
    }

    // The owner reference the creature answers with, whichever store it came from: a tamed animal's own, or the
    // contract record the interface default reads. The id works with the owner offline; the player does not.
    public static Optional<EntityReference<LivingEntity>> ownerReference(Entity entity) {
        return entity instanceof Contractable contractable
                ? Optional.ofNullable(contractable.getOwnerReference())
                : Optional.empty();
    }

    public static Optional<UUID> ownerOf(Entity entity) {
        return ownerReference(entity).map(EntityReference::getUUID);
    }

    // Vanilla resolution, so a creature whose class stores its owner answers here the way the rest of the game
    // does; empty when the owner is nobody the level can find.
    public static @Nullable ServerPlayer owner(Entity entity) {
        return entity instanceof Contractable contractable && contractable.getOwner() instanceof ServerPlayer player
                ? player : null;
    }

    // Built for an entity that is already bound; the owner is asked of the entity, never of the record.
    public static ContractContext context(Mob self, Holder<ContractType> type) {
        return ContractContext.of(self, ownerOf(self).orElse(null), owner(self), type);
    }

    public static ContractContext context(Mob self, ServerPlayer owner, Holder<ContractType> type) {
        return ContractContext.of(self, owner.getUUID(), owner, type);
    }
}
