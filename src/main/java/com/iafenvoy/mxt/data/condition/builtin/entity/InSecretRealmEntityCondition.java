package com.iafenvoy.mxt.data.condition.builtin.entity;

import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.context.condition.EntityConditionContext;
import com.iafenvoy.mxt.data.secretrealm.SecretRealm;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.world.SecretRealmRecord;
import com.iafenvoy.mxt.runtime.world.SecretRealmRegistry;
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
 * True when the entity is inside a secret realm, optionally one this condition names. The {@code role} field
 * asks about membership and claims, so a secret realm definition needs no permission field of its own.
 */
public record InSecretRealmEntityCondition(Optional<Either<Holder<SecretRealm>, TagKey<SecretRealm>>> definition,
                                           Role role) implements EntityCondition {
    public static final MapCodec<InSecretRealmEntityCondition> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            RegistryCodecs.holderOrTag(MxtResourceKeys.SECRET_REALM).optionalFieldOf("definition").forGetter(InSecretRealmEntityCondition::definition),
            Role.CODEC.optionalFieldOf("role", Role.ANY).forGetter(InSecretRealmEntityCondition::role)
    ).apply(i, InSecretRealmEntityCondition::new));

    @Override
    public boolean test(@NonNull EntityConditionContext ctx) {
        Entity entity = ctx.entity();
        Optional<SecretRealmRecord> found = SecretRealmRegistry.ofMember(entity.getUUID());
        if (found.isEmpty()) return false;
        SecretRealmRecord record = found.get();
        if (this.definition.isPresent() && !matches(this.definition.get(), record.definition())) return false;
        return this.role.holds(record, entity.getUUID());
    }

    @Override
    public @NonNull MapCodec<InSecretRealmEntityCondition> codec() {
        return CODEC;
    }

    private static boolean matches(Either<Holder<SecretRealm>, TagKey<SecretRealm>> value, Holder<SecretRealm> candidate) {
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

        boolean holds(SecretRealmRecord record, UUID member) {
            return switch (this) {
                case ANY -> true;
                case OWNER -> record.isOwner(member);
                case GUEST -> !record.isOwner(member);
            };
        }
    }
}
