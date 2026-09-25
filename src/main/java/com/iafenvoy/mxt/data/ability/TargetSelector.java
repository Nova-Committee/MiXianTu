package com.iafenvoy.mxt.data.ability;

import com.iafenvoy.mxt.data.ability.target.TargetOrder;
import com.iafenvoy.mxt.registry.MxtRegistries;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Selects the entities that receive an ability's bi-entity action.
 */
public interface TargetSelector {
    Codec<TargetSelector> CODEC = MxtRegistries.ABILITY_TARGET_SELECTOR_TYPE.byNameCodec().dispatch("type", TargetSelector::codec, Function.identity());

    Stream<Entity> select(Entity actor, FormulaContext context);

    // For an activation that happens somewhere other than where the actor stands (an item cast from a display
    // stand). The default ignores it, because most selectors ask about the actor itself rather than about a place.
    default Stream<Entity> select(Entity actor, FormulaContext context, @Nullable Vec3 origin) {
        return this.select(actor, context);
    }

    MapCodec<? extends TargetSelector> codec();

    // Shared by every selector that can be capped: order the candidates (by distance from the activation's own
    // place, or at random) and keep the first `limit` of them. A limit of zero or less keeps everything, in the
    // order the level handed it over. Ties are broken by entity id so one moment always selects the same beings.
    static Stream<Entity> limited(Stream<Entity> candidates, Entity actor, @Nullable Vec3 origin, int limit,
                                  TargetOrder order) {
        if (limit <= 0) return candidates;
        List<Entity> found = candidates.toList();
        if (order == TargetOrder.RANDOM) {
            RandomSource random = actor.level().getRandom();
            found = found.stream().sorted(Comparator.comparingInt(entity -> random.nextInt())).toList();
        } else {
            Vec3 centre = origin == null ? actor.position() : origin;
            Comparator<Entity> byDistance = Comparator
                    .comparingDouble((Entity entity) -> centre.distanceToSqr(entity.position()))
                    .thenComparingInt(Entity::getId);
            found = found.stream().sorted(order == TargetOrder.FARTHEST ? byDistance.reversed() : byDistance).toList();
        }
        return found.stream().limit(limit);
    }
}
