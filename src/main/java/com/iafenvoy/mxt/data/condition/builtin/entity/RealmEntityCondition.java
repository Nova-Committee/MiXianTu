package com.iafenvoy.mxt.data.condition.builtin.entity;

import com.iafenvoy.mxt.attachment.CultivationAttachment;
import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.context.condition.EntityConditionContext;
import com.iafenvoy.mxt.data.cultivation.RealmStage;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.ServerCache;
import com.iafenvoy.mxt.util.HolderHelper;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.Holder.Reference;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.NonNull;

import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

public record RealmEntityCondition(Holder<RealmStage> realm,
                                   Comparison comparison) implements EntityCondition {
    public static final MapCodec<RealmEntityCondition> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            RealmStage.CODEC.fieldOf("realm").forGetter(RealmEntityCondition::realm),
            Comparison.CODEC.optionalFieldOf("comparison", Comparison.EXACT).forGetter(RealmEntityCondition::comparison)
    ).apply(i, RealmEntityCondition::new));

    @Override
    public boolean test(@NonNull EntityConditionContext ctx) {
        Entity entity = ctx.entity();
        Identifier required = HolderHelper.id(this.realm);
        CultivationAttachment cultivation = entity.getData(MxtAttachments.CULTIVATION);
        Stream<Holder<RealmStage>> stages = cultivation.realmStages().values().stream();
        // A missing stage is the mortal state; for a resource chain its
        // first realm is the pending stage used by cultivation formulas.
        Stream<Reference<Resource>> registry = MxtDatapackRegistries.holders(MxtResourceKeys.RESOURCE);
        stages = Stream.concat(stages,
                registry.map(Holder::value).map(Resource::firstRealm).flatMap(Optional::stream)
                        .filter(first -> cultivation.realmStage(first.value().resource()) == null));
        return stages.anyMatch(current -> switch (this.comparison) {
            case EXACT -> current.equals(this.realm);
            case AT_LEAST ->
                    ServerCache.get().map(cache -> cache.isRealmAtLeast(HolderHelper.id(current), required)).orElse(false);
            case AT_MOST ->
                    ServerCache.get().map(cache -> cache.isRealmAtLeast(required, HolderHelper.id(current))).orElse(false);
        });
    }

    @Override
    public @NonNull MapCodec<RealmEntityCondition> codec() {
        return CODEC;
    }

    public enum Comparison {
        EXACT,
        AT_LEAST,
        AT_MOST;

        public static final Codec<Comparison> CODEC = Codec.STRING.xmap(
                value -> valueOf(value.toUpperCase(Locale.ROOT)),
                value -> value.name().toLowerCase(Locale.ROOT)
        );
    }
}
