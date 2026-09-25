package com.iafenvoy.mxt.runtime.formation;

import com.iafenvoy.mxt.runtime.friend.FriendService;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import net.minecraft.core.UUIDUtil;
import net.minecraft.util.TriState;
import net.minecraft.world.entity.Entity;
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
public record FormationOwners(List<UUID> ids) {
    public static final FormationOwners NONE = new FormationOwners(List.of());
    public static final Codec<FormationOwners> CODEC = Codec.either(UUIDUtil.CODEC, UUIDUtil.CODEC.listOf())
            .xmap(FormationOwners::ofEither, FormationOwners::toEither);

    public FormationOwners {
        ids = List.copyOf(ids);
    }

    public static FormationOwners of(UUID id) {
        return new FormationOwners(List.of(id));
    }

    private static FormationOwners ofEither(Either<UUID, List<UUID>> value) {
        return new FormationOwners(value.map(List::of, Function.identity()));
    }

    private static Either<UUID, List<UUID>> toEither(FormationOwners owners) {
        return owners.ids.size() == 1 ? Either.left(owners.ids.getFirst()) : Either.right(owners.ids);
    }

    public boolean isEmpty() {
        return this.ids.isEmpty();
    }

    public boolean contains(Entity entity) {
        return this.ids.contains(entity.getUUID());
    }

    // The owner a single answer has to come from: taking an array down is one decision, and the first listed
    // player is the one who raised it.
    public Optional<UUID> primary() {
        return this.ids.isEmpty() ? Optional.empty() : Optional.of(this.ids.getFirst());
    }

    public @Nullable Entity primaryEntity(Level level) {
        return this.primary().map(level::getEntity).orElse(null);
    }

    public FormationOwners with(UUID id) {
        if (this.ids.contains(id)) return this;
        List<UUID> added = new ArrayList<>(this.ids);
        added.add(id);
        return new FormationOwners(added);
    }

    public FormationOwners without(UUID id) {
        if (!this.ids.contains(id)) return this;
        List<UUID> left = new ArrayList<>(this.ids);
        left.remove(id);
        return new FormationOwners(left);
    }

    // Any owner recognising the entity settles it; when no owner recognises it and at least one can tell, the
    // answer is no, and a list nobody can answer for leaves it unanswered rather than refused.
    public TriState identify(Level level, Entity entity) {
        TriState answer = TriState.DEFAULT;
        for (UUID id : this.ids) {
            TriState one = FriendService.identify(id, level.getEntity(id), entity);
            if (one == TriState.TRUE) return TriState.TRUE;
            if (one == TriState.FALSE) answer = TriState.FALSE;
        }
        return answer;
    }
}
