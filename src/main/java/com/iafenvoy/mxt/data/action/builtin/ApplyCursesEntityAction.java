package com.iafenvoy.mxt.data.action.builtin;

import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.Entity;

import java.util.List;

/**
 * Applies several independently configured curses through the standard server transaction.
 */
public record ApplyCursesEntityAction(List<ApplyCurseEntityAction> curses) implements EntityAction {
    public static final MapCodec<ApplyCursesEntityAction> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ApplyCurseEntityAction.CODEC.codec().listOf().fieldOf("curses").forGetter(ApplyCursesEntityAction::curses)
    ).apply(instance, ApplyCursesEntityAction::new));

    public ApplyCursesEntityAction {
        curses = List.copyOf(curses);
        if (curses.isEmpty()) throw new IllegalArgumentException("curses must not be empty");
    }

    @Override
    public void execute(Entity entity) {
        this.execute(entity, FormulaContext.EMPTY);
    }

    @Override
    public void execute(Entity entity, FormulaContext context) {
        this.curses.forEach(curse -> curse.execute(entity, context));
    }

    @Override
    public MapCodec<ApplyCursesEntityAction> codec() {
        return CODEC;
    }
}
