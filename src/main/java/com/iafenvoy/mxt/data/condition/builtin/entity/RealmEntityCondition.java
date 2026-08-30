package com.iafenvoy.mxt.data.condition.builtin.entity;

import com.iafenvoy.mxt.data.context.condition.EntityConditionContext;

import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.data.cultivation.RealmStage;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.runtime.ServerCache;
import com.iafenvoy.mxt.util.HolderHelper;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.NonNull;

import java.util.Locale;

public record RealmEntityCondition(Holder<RealmStage> realm,
                                   Comparison comparison) implements EntityCondition {
    public static final MapCodec<RealmEntityCondition> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            RealmStage.CODEC.fieldOf("realm").forGetter(RealmEntityCondition::realm),
            Comparison.CODEC.optionalFieldOf("comparison", Comparison.EXACT).forGetter(RealmEntityCondition::comparison)
    ).apply(i, RealmEntityCondition::new));

    @Override
    public boolean test(@NonNull EntityConditionContext ctx) {
        Entity entity = ctx.entity();
        FormulaContext context = ctx.formula();
        Identifier required = HolderHelper.id(this.realm);
        return entity.getData(MxtAttachments.CULTIVATION).realmStages().values().stream().anyMatch(current -> switch (this.comparison) {
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
