package com.iafenvoy.mxt.data.condition.builtin.entity;

import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.context.condition.EntityConditionContext;
import com.iafenvoy.mxt.data.realm.RealmInstance;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.world.RealmInstanceRegistry;
import com.iafenvoy.mxt.runtime.world.RealmRecord;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.codec.RegistryCodecs;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.NonNull;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * True when the entity is inside a realm instance, optionally one this condition names.
 *
 * <p>This is the data pack's window onto membership and claims: {@code owned} records who claimed an instance,
 * and a pack turns that into "only the owner may bring guests" or "the owner earns the clear reward" without
 * the realm definition needing a permission field of its own.
 */
public record InRealmInstanceEntityCondition(Optional<Either<Holder<RealmInstance>, TagKey<RealmInstance>>> definition,
                                             Role role) implements EntityCondition {
    public static final MapCodec<InRealmInstanceEntityCondition> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            RegistryCodecs.holderOrTag(MxtResourceKeys.REALM_INSTANCE).optionalFieldOf("definition").forGetter(InRealmInstanceEntityCondition::definition),
            Role.CODEC.optionalFieldOf("role", Role.ANY).forGetter(InRealmInstanceEntityCondition::role)
    ).apply(i, InRealmInstanceEntityCondition::new));

    @Override
    public boolean test(@NonNull EntityConditionContext ctx) {
        Entity entity = ctx.entity();
        Optional<RealmRecord> found = RealmInstanceRegistry.ofMember(entity.getUUID());
        if (found.isEmpty()) return false;
        RealmRecord record = found.get();
        if (this.definition.isPresent() && !matches(this.definition.get(), record.definition())) return false;
        return this.role.holds(record, entity.getUUID());
    }

    @Override
    public @NonNull MapCodec<InRealmInstanceEntityCondition> codec() {
        return CODEC;
    }

    private static boolean matches(Either<Holder<RealmInstance>, TagKey<RealmInstance>> value, Holder<RealmInstance> candidate) {
        return value.map(holder -> HolderHelper.id(holder).equals(HolderHelper.id(candidate)), candidate::is);
    }

    /**
     * The three roles a pack can ask about. Membership is implied: an entity that is not inside has no role.
     */
    public enum Role {
        ANY,
        OWNER,
        GUEST;

        public static final Codec<Role> CODEC = Codec.STRING.xmap(
                value -> valueOf(value.toUpperCase(Locale.ROOT)),
                value -> value.name().toLowerCase(Locale.ROOT));

        boolean holds(RealmRecord record, UUID member) {
            return switch (this) {
                case ANY -> true;
                case OWNER -> record.isOwner(member);
                case GUEST -> !record.isOwner(member);
            };
        }
    }
}
