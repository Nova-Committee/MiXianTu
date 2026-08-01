package com.iafenvoy.mxt.data.action.builtin;

import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.event.CurseRemoveEvent;
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

/** Removes every held curse sharing a requested datapack tag. */
public record RemoveCursesByTagEntityAction(List<Identifier> tags) implements EntityAction {
    public static final MapCodec<RemoveCursesByTagEntityAction> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Identifier.CODEC.listOf().fieldOf("tags").forGetter(RemoveCursesByTagEntityAction::tags)
    ).apply(instance, RemoveCursesByTagEntityAction::new));

    public RemoveCursesByTagEntityAction {
        tags = List.copyOf(tags);
        if (tags.isEmpty()) throw new IllegalArgumentException("tags must not be empty");
    }

    @Override public void execute(Entity entity) { this.execute(entity, FormulaContext.EMPTY); }

    @Override public void execute(Entity entity, FormulaContext context) {
        List<Identifier> matches = entity.getData(MxtAttachments.CURSE_HOLDER).instances().keySet().stream()
                .filter(id -> this.tags.stream().anyMatch(tag -> MxtDatapackRegistries.isTagged(MxtDatapackRegistries.CURSE, id, tag)))
                .toList();
        matches.forEach(id -> CurseService.remove(entity, id, Reason.CONTENT_ACTION, entity.level().getGameTime()));
    }

    @Override public MapCodec<RemoveCursesByTagEntityAction> codec() { return CODEC; }
}
