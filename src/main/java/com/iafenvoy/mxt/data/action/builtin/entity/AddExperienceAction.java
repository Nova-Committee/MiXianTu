package com.iafenvoy.mxt.data.action.builtin.entity;

import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.context.action.EntityActionContext;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.NonNull;

import java.util.Optional;

/**
 * Origins-compatible player experience action without Origin-specific state.
 */
public record AddExperienceAction(Optional<Integer> points, Optional<Integer> levels) implements EntityAction {
    public static final MapCodec<AddExperienceAction> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.INT.optionalFieldOf("points").forGetter(AddExperienceAction::points),
            Codec.INT.optionalFieldOf("levels").forGetter(AddExperienceAction::levels)
    ).apply(i, AddExperienceAction::new));

    @Override
    public void execute(@NonNull EntityActionContext ctx) {
        Entity entity = ctx.entity();
        if (entity instanceof Player player) {
            this.points.ifPresent(player::giveExperiencePoints);
            this.levels.ifPresent(player::giveExperienceLevels);
        }
    }

    @Override
    public @NonNull MapCodec<AddExperienceAction> codec() {
        return CODEC;
    }
}
