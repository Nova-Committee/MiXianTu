package com.iafenvoy.mxt.data.action.builtin.entity;

import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.Entity;

import java.util.List;

/**
 * Applies several independently configured curses through the standard server transaction.
 */
public record ApplyCursesAction(List<ApplyCurseAction> curses) implements EntityAction {
    public static final MapCodec<ApplyCursesAction> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ApplyCurseAction.CODEC.codec().listOf().fieldOf("curses").forGetter(ApplyCursesAction::curses)
    ).apply(instance, ApplyCursesAction::new));

    public ApplyCursesAction {
        if (curses.isEmpty()) throw new IllegalArgumentException("curses must not be empty");
    }

    @Override
    public void execute(Entity entity, FormulaContext context) {
        this.curses.forEach(curse -> curse.execute(entity, context));
    }

    @Override
    public MapCodec<ApplyCursesAction> codec() {
        return CODEC;
    }
}
