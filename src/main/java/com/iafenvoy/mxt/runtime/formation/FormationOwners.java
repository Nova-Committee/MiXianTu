package com.iafenvoy.mxt.runtime.formation;

import com.iafenvoy.mxt.runtime.friend.FriendService;
import com.iafenvoy.mxt.util.EntityReferences;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import net.minecraft.util.TriState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * The subjects a formation belongs to. Most arrays have the one player who raised them, but a shared one lists
 * several: being on the list is the whole of being its owner, and the friend sources are asked on behalf of every
 * listed player rather than of one.
 *
 * <p>One owner writes as the single UUID vanilla saves everywhere (a four-int array) and several as a list of
 * them, so the common case stays readable in a save.
 */
public record FormationOwners(List<EntityReference<Entity>> references) {
    public static final FormationOwners NONE = new FormationOwners(List.of());
    private static final Codec<EntityReference<Entity>> OWNER_CODEC = EntityReference.codec();
    public static final Codec<FormationOwners> CODEC = Codec.either(OWNER_CODEC, OWNER_CODEC.listOf())
            .xmap(FormationOwners::ofEither, FormationOwners::toEither);

    public FormationOwners {
        references = List.copyOf(references);
    }

    public static FormationOwners of(UUID id) {
        return new FormationOwners(List.of(EntityReference.of(id)));
    }

    private static FormationOwners ofEither(Either<EntityReference<Entity>, List<EntityReference<Entity>>> value) {
        return new FormationOwners(value.map(List::of, Function.identity()));
    }

    private static Either<EntityReference<Entity>, List<EntityReference<Entity>>> toEither(FormationOwners owners) {
        return owners.references.size() == 1 ? Either.left(owners.references.getFirst()) : Either.right(owners.references);
    }

    public List<UUID> ids() {
        return this.references.stream().map(EntityReference::getUUID).toList();
    }

    public boolean isEmpty() {
        return this.references.isEmpty();
    }

    public boolean contains(Entity entity) {
        for (EntityReference<Entity> reference : this.references)
            if (reference.matches(entity)) return true;
        return false;
    }

    // The owner a single answer has to come from: taking an array down is one decision, and the first listed
    // player is the one who raised it.
    public Optional<UUID> primary() {
        return this.isEmpty() ? Optional.empty() : Optional.of(this.references.getFirst().getUUID());
    }

    public @Nullable Entity primaryEntity(Level level) {
        return this.isEmpty() ? null : EntityReferences.inLevel(this.references.getFirst(), level);
    }

    public FormationOwners with(UUID id) {
        EntityReference<Entity> reference = EntityReference.of(id);
        if (this.references.contains(reference)) return this;
        List<EntityReference<Entity>> added = new ArrayList<>(this.references);
        added.add(reference);
        return new FormationOwners(added);
    }

    public FormationOwners without(UUID id) {
        int index = this.references.indexOf(EntityReference.of(id));
        if (index < 0) return this;
        List<EntityReference<Entity>> left = new ArrayList<>(this.references);
        left.remove(index);
        return new FormationOwners(left);
    }

    // Any owner recognising the entity settles it; when no owner recognises it and at least one can tell, the
    // answer is no, and a list nobody can answer for leaves it unanswered rather than refused.
    public TriState identify(Level level, Entity entity) {
        TriState answer = TriState.DEFAULT;
        for (EntityReference<Entity> reference : this.references) {
            TriState one = FriendService.identify(reference.getUUID(), EntityReferences.inLevel(reference, level), entity);
            if (one == TriState.TRUE) return TriState.TRUE;
            if (one == TriState.FALSE) answer = TriState.FALSE;
        }
        return answer;
    }
}
