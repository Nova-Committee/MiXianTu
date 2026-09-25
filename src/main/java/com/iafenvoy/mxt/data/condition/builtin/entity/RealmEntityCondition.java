package com.iafenvoy.mxt.data.condition.builtin.entity;

import com.iafenvoy.mxt.attachment.CultivationAttachment;
import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.context.condition.EntityConditionContext;
import com.iafenvoy.mxt.data.cultivation.RealmStage;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.runtime.ServerCache;
import com.iafenvoy.mxt.runtime.aura.AuraLookup;
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

/**
 * Asks what realm the entity stands in, and optionally how far it has got inside that realm. The minor stage is
 * read from the body's own record rather than from live progress, so a layer that was reached stays reached -
 * that is what makes a threshold on it usable as a gate.
 */
public record RealmEntityCondition(Holder<RealmStage> realm,
                                   Comparison comparison,
                                   Optional<Integer> minMinorStage) implements EntityCondition {
    public static final MapCodec<RealmEntityCondition> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            RealmStage.CODEC.fieldOf("realm").forGetter(RealmEntityCondition::realm),
            Comparison.CODEC.optionalFieldOf("comparison", Comparison.EXACT).forGetter(RealmEntityCondition::comparison),
            Codec.INT.optionalFieldOf("min_minor_stage").forGetter(RealmEntityCondition::minMinorStage)
    ).apply(i, RealmEntityCondition::new));

    @Override
    public boolean test(@NonNull EntityConditionContext ctx) {
        Entity entity = ctx.entity();
        Identifier required = HolderHelper.id(this.realm);
        CultivationAttachment cultivation = entity.getData(MxtAttachments.CULTIVATION);
        Stream<Holder<RealmStage>> stages = cultivation.realmStages().values().stream();
        // A missing stage is the mortal state; for a value whose profile enters a chain its
        // first realm is the pending stage used by cultivation formulas.
        Stream<Aura> profiles = AuraLookup.all(entity.level()).map(Reference::value);
        stages = Stream.concat(stages,
                profiles.map(Aura::firstRealm).flatMap(Optional::stream)
                        .filter(first -> cultivation.realmStage(first.value().aura()) == null));
        boolean inRealm = stages.anyMatch(current -> switch (this.comparison) {
            case EXACT -> current.equals(this.realm);
            case AT_LEAST ->
                    ServerCache.get().map(cache -> cache.isRealmAtLeast(HolderHelper.id(current), required)).orElse(false);
            case AT_MOST ->
                    ServerCache.get().map(cache -> cache.isRealmAtLeast(required, HolderHelper.id(current))).orElse(false);
        });
        if (!inRealm || this.minMinorStage.isEmpty()) return inRealm;
        // -1 for a realm the body never entered, so a threshold is never met by a realm it has not stood in.
        return entity.getExistingData(MxtAttachments.SPIRIT_IDENTITY)
                .map(identity -> identity.minorStageRecord(this.realm) >= this.minMinorStage.get())
                .orElse(false);
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
