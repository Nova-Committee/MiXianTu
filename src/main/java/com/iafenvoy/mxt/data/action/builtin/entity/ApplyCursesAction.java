package com.iafenvoy.mxt.data.action.builtin.entity;

import com.iafenvoy.mxt.data.context.action.EntityActionContext;

import com.iafenvoy.mxt.data.action.EntityAction;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.NonNull;

import java.util.List;

/**
 * Applies several independently configured curses through the standard server transaction.
 */
public record ApplyCursesAction(List<ApplyCurseAction> curses) implements EntityAction {
    public static final MapCodec<ApplyCursesAction> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            ApplyCurseAction.CODEC.codec().listOf().fieldOf("curses").forGetter(ApplyCursesAction::curses)
    ).apply(i, ApplyCursesAction::new));

    public ApplyCursesAction {
        if (curses.isEmpty()) throw new IllegalArgumentException("curses must not be empty");
    }

    @Override
    public void execute(@NonNull EntityActionContext ctx) {
        Entity entity = ctx.entity();
        this.curses.forEach(curse -> curse.execute(entity, ctx));
    }

    @Override
    public @NonNull MapCodec<ApplyCursesAction> codec() {
        return CODEC;
    }
}
