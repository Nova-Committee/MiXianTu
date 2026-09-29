package com.iafenvoy.mxt.data.condition.builtin.entity;

import com.iafenvoy.mxt.attachment.ProgressionAttachment;
import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.context.condition.EntityConditionContext;
import com.iafenvoy.mxt.data.progression.Progression;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.runtime.ServerCache;
import com.iafenvoy.mxt.runtime.progression.ProgressionService;
import com.iafenvoy.mxt.runtime.progression.ProgressionSources;
import com.iafenvoy.mxt.util.HolderHelper;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

import java.util.List;

/**
 * Asks how far a body has climbed a progression, read as the level it reached. A level is ordered by the chain the
 * server indexed, so the comparison names one level and asks where the body stands; {@code owner} narrows the
 * question to some owners instead of every progression the body holds.
 */
public record ProgressionEntityCondition(Holder<Progression> level, RealmEntityCondition.Comparison comparison,
                                         List<Identifier> owner) implements EntityCondition {
    private static final Codec<List<Identifier>> OWNERS = Codec.either(Identifier.CODEC, Identifier.CODEC.listOf())
            .xmap(either -> either.map(List::of, list -> list),
                    list -> list.size() == 1 ? Either.left(list.getFirst()) : Either.right(list));
    public static final MapCodec<ProgressionEntityCondition> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Progression.CODEC.fieldOf("level").forGetter(ProgressionEntityCondition::level),
            RealmEntityCondition.Comparison.CODEC.optionalFieldOf("comparison", RealmEntityCondition.Comparison.EXACT).forGetter(ProgressionEntityCondition::comparison),
            OWNERS.optionalFieldOf("owner", List.of()).forGetter(ProgressionEntityCondition::owner)
    ).apply(i, ProgressionEntityCondition::new));

    @Override
    public boolean test(@NonNull EntityConditionContext ctx) {
        ProgressionAttachment progress = ctx.entity().getExistingData(MxtAttachments.PROGRESSION).orElse(null);
        for (ProgressionSources.Owner held : ProgressionSources.heldBy(ctx.entity())) {
            if (!this.owner.isEmpty() && !this.owner.contains(held.id())) continue;
            Holder<Progression> current = ProgressionService.currentLevel(progress, held.id(), held.definition()).orElse(null);
            if (current != null && this.reached(current)) return true;
        }
        return false;
    }

    // Both bounds come from the indexed chain, so a level of another chain satisfies neither.
    private boolean reached(Holder<Progression> current) {
        Identifier currentId = HolderHelper.id(current);
        Identifier required = HolderHelper.id(this.level);
        return switch (this.comparison) {
            case EXACT -> currentId.equals(required);
            case AT_LEAST -> ServerCache.get().map(cache -> cache.isLevelAtLeast(currentId, required)).orElse(false);
            case AT_MOST -> ServerCache.get().map(cache -> cache.isLevelAtLeast(required, currentId)).orElse(false);
        };
    }

    @Override
    public @NonNull MapCodec<ProgressionEntityCondition> codec() {
        return CODEC;
    }
}
