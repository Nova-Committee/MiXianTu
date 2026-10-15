package com.iafenvoy.mxt.data.action.builtin.entity;

import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.context.action.EntityActionContext;
import com.iafenvoy.mxt.util.codec.OptionalCodecs;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.NonNull;

import java.util.OptionalInt;

/**
 * Grants vanilla experience points and/or levels, to players only.
 */
public record AddExperienceAction(OptionalInt points, OptionalInt levels) implements EntityAction {
    public static final MapCodec<AddExperienceAction> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            OptionalCodecs.intCodec("points").forGetter(AddExperienceAction::points),
            OptionalCodecs.intCodec("levels").forGetter(AddExperienceAction::levels)
    ).apply(i, AddExperienceAction::new));

    @Override
    public void execute(@NonNull EntityActionContext ctx) {
        if (ctx.entity() instanceof Player player) {
            this.points.ifPresent(player::giveExperiencePoints);
            this.levels.ifPresent(player::giveExperienceLevels);
        }
    }

    @Override
    public @NonNull MapCodec<AddExperienceAction> codec() {
        return CODEC;
    }
}
