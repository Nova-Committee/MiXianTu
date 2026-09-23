package com.iafenvoy.mxt.data.ability;

import com.iafenvoy.mxt.registry.MxtRegistries;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

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
}
