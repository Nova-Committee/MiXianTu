package com.iafenvoy.mxt.data.action.builtin.entity;
import com.iafenvoy.mxt.registry.MxtResourceKeys;

import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.curse.Curse;
import com.iafenvoy.mxt.event.CurseRemoveEvent.Reason;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.runtime.curse.CurseService;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;

import java.util.List;

/**
 * Removes every held curse sharing a requested datapack tag.
 */
public record RemoveCursesByTagAction(List<Identifier> tags) implements EntityAction {
    public static final MapCodec<RemoveCursesByTagAction> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Identifier.CODEC.listOf().fieldOf("tags").forGetter(RemoveCursesByTagAction::tags)
    ).apply(i, RemoveCursesByTagAction::new));

    public RemoveCursesByTagAction {
        if (tags.isEmpty()) throw new IllegalArgumentException("tags must not be empty");
    }

    @Override
    public void execute(Entity entity, FormulaContext context) {
        List<Holder<Curse>> matches = entity.getData(MxtAttachments.CURSE_HOLDER).instances().keySet().stream()
                .filter(curse -> this.tags.stream().anyMatch(tag -> MxtDatapackRegistries.isTagged(MxtResourceKeys.CURSE, curse, tag)))
                .toList();
        matches.forEach(curse -> CurseService.remove(entity, curse, Reason.CONTENT_ACTION, entity.level().getGameTime()));
    }

    @Override
    public MapCodec<RemoveCursesByTagAction> codec() {
        return CODEC;
    }
}
