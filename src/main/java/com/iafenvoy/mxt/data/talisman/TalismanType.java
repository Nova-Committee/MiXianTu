package com.iafenvoy.mxt.data.talisman;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.action.BiEntityAction;
import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.ability.ActionCarrier;
import com.iafenvoy.mxt.data.context.action.BiEntityActionContext;
import com.iafenvoy.mxt.data.context.action.EntityActionContext;
import com.iafenvoy.mxt.registry.MxtRegistries;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;

import java.util.List;
import java.util.function.Function;

/**
 * Code-owned talisman behaviour selected by a datapack {@code type}: what one invocation of a carrier lands on.
 * A type owns its own parameters; the definition holds only what every invocation shares, so the two action fields
 * mean here what they mean on every other action carrier.
 *
 * <p>The difference between the types is only where the targets come from, and whether having none means the use
 * cannot happen at all - a crosshair that sees nobody is refused before the definition's own condition and before
 * anything is paid, which is the one place a target requirement outranks that gate.
 */
public interface TalismanType {
    MapCodec<TalismanType> CODEC = MxtRegistries.TALISMAN_TYPE.byNameCodec().dispatchMap("type", TalismanType::codec, Function.identity());
    // The fall a thrown carrier travels with until its own definition says otherwise.
    double DEFAULT_GRAVITY = 0.03D;

    MapCodec<? extends TalismanType> codec();

    Plan plan(TalismanUse use);

    void apply(TalismanUse use, Plan plan);

    // The half a use that lands later runs on the one entity it reached: the caster's own action already happened
    // when the carrier was used, so a delayed type only has the bi-entity half left.
    default void landOn(TalismanUse use, Entity target) {
    }

    // The same half for a landing in the world instead of on somebody: a block has no entity to act with, so the
    // type's own block half answers here. The face is the one that was struck.
    default void landOnBlock(TalismanUse use, BlockPos pos, Direction direction) {
    }

    // The fall this use travels with; only a thrown one answers anything but the default.
    default double gravity(FormulaContext context) {
        return DEFAULT_GRAVITY;
    }

    // How many invocations this inscription gives its carrier before it is spent. Zero is "this type declares none":
    // the carrier is then spent whole, one item per invocation, which is what every type without `max_use` costs.
    default int maxUse() {
        return 0;
    }

    /**
     * Who an invocation lands on, and whether it can happen at all.
     */
    record Plan(boolean runnable, List<Entity> targets) {
        public static Plan of(List<Entity> targets) {
            return new Plan(true, List.copyOf(targets));
        }

        public static Plan impossible() {
            return new Plan(false, List.of());
        }
    }

    /**
     * The shared reading of the two action fields: the caster's own action once, then one bi-entity action per target
     * the type admitted. Shared here rather than spelled out per type, so a type only ever decides its target set.
     */
    static void runActions(EntityAction entityAction, BiEntityAction biEntityAction, TalismanUse use, List<Entity> targets) {
        try {
            entityAction.execute(new EntityActionContext(use.user(), use.formula(), use.origin()));
        } catch (RuntimeException exception) {
            MiXianTu.LOGGER.error("Talisman entity action failed", exception);
        }
        for (Entity target : targets) {
            try {
                biEntityAction.execute(use.user(), target, new BiEntityActionContext(use.user(), target,
                        ActionCarrier.targetContext(use.user(), target, use.formula()), use.origin()));
            } catch (RuntimeException exception) {
                MiXianTu.LOGGER.error("Talisman target action failed", exception);
            }
        }
    }
}
