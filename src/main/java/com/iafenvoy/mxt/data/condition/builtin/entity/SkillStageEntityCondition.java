package com.iafenvoy.mxt.data.condition.builtin.entity;

import com.iafenvoy.mxt.attachment.SpiritIdentityAttachment;
import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.context.condition.EntityConditionContext;
import com.iafenvoy.mxt.data.cultivation.SkillStage;
import com.iafenvoy.mxt.data.cultivation.Technique;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.runtime.ServerCache;
import com.iafenvoy.mxt.runtime.cultivation.SkillStageService;
import com.iafenvoy.mxt.runtime.cultivation.TechniqueService;
import com.iafenvoy.mxt.util.HolderHelper;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

import java.util.Optional;

/**
 * Asks how far a learned technique has climbed its skill chain, read as the level the body reached. A level is
 * ordered by the chain the server indexed, so the comparison names one level and asks where the body stands.
 */
public record SkillStageEntityCondition(Holder<SkillStage> stage, RealmEntityCondition.Comparison comparison,
                                        Optional<Holder<Technique>> technique) implements EntityCondition {
    public static final MapCodec<SkillStageEntityCondition> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            SkillStage.CODEC.fieldOf("stage").forGetter(SkillStageEntityCondition::stage),
            RealmEntityCondition.Comparison.CODEC.optionalFieldOf("comparison", RealmEntityCondition.Comparison.EXACT).forGetter(SkillStageEntityCondition::comparison),
            Technique.CODEC.optionalFieldOf("technique").forGetter(SkillStageEntityCondition::technique)
    ).apply(i, SkillStageEntityCondition::new));

    @Override
    public boolean test(@NonNull EntityConditionContext ctx) {
        SpiritIdentityAttachment spirit = ctx.entity().getExistingData(MxtAttachments.SPIRIT_IDENTITY).orElse(null);
        if (spirit == null) return false;
        Identifier wanted = this.technique.map(HolderHelper::id).orElse(null);
        for (Holder<Technique> learned : TechniqueService.known(spirit)) {
            if (wanted != null && !HolderHelper.id(learned).equals(wanted)) continue;
            Holder<SkillStage> current = SkillStageService.currentStage(spirit, learned).orElse(null);
            if (current != null && this.reached(current)) return true;
        }
        return false;
    }

    // Both bounds come from the indexed chain, so a level of another chain satisfies neither.
    private boolean reached(Holder<SkillStage> current) {
        Identifier currentId = HolderHelper.id(current);
        Identifier required = HolderHelper.id(this.stage);
        return switch (this.comparison) {
            case EXACT -> currentId.equals(required);
            case AT_LEAST -> ServerCache.get().map(cache -> cache.isStageAtLeast(currentId, required)).orElse(false);
            case AT_MOST -> ServerCache.get().map(cache -> cache.isStageAtLeast(required, currentId)).orElse(false);
        };
    }

    @Override
    public @NonNull MapCodec<SkillStageEntityCondition> codec() {
        return CODEC;
    }
}
