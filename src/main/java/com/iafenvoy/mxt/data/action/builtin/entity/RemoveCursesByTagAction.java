package com.iafenvoy.mxt.data.action.builtin.entity;

import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.event.CurseRemoveEvent.Reason;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.runtime.curse.CurseService;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;

import java.util.List;

/**
 * Removes every held curse sharing a requested datapack tag.
 */
public record RemoveCursesByTagAction(List<Identifier> tags) implements EntityAction {
    public static final MapCodec<RemoveCursesByTagAction> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Identifier.CODEC.listOf().fieldOf("tags").forGetter(RemoveCursesByTagAction::tags)
    ).apply(instance, RemoveCursesByTagAction::new));

    public RemoveCursesByTagAction {
        tags = List.copyOf(tags);
        if (tags.isEmpty()) throw new IllegalArgumentException("tags must not be empty");
    }

    @Override
    public void execute(Entity entity, FormulaContext context) {
        List<Identifier> matches = entity.getData(MxtAttachments.CURSE_HOLDER).instances().keySet().stream()
                .filter(id -> this.tags.stream().anyMatch(tag -> MxtDatapackRegistries.isTagged(MxtDatapackRegistries.CURSE, id, tag)))
                .toList();
        matches.forEach(id -> CurseService.remove(entity, id, Reason.CONTENT_ACTION, entity.level().getGameTime()));
    }

    @Override
    public MapCodec<RemoveCursesByTagAction> codec() {
        return CODEC;
    }
}
