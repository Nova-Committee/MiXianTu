package com.iafenvoy.mxt.probe.verify;

import com.iafenvoy.mxt.data.ability.TargetSelector;
import com.iafenvoy.mxt.data.ability.target.AreaTargetSelector;
import com.iafenvoy.mxt.data.ability.target.ConeTargetSelector;
import com.iafenvoy.mxt.data.ability.target.RayTargetSelector;
import com.iafenvoy.mxt.data.ability.target.TargetOrder;
import com.iafenvoy.mxt.probe.Probe;
import com.iafenvoy.mxt.probe.ProbeContext;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.cultivation.CultivationIdentityService;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.number.Constant;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.level.block.Blocks;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The selectors a cast can name: the cap and order every capped selector shares, the cone's angle, and the ray
 * stopping at a block. Every number is measured against beings laid out on fixed offsets.
 */
public final class TargetSelectorVerifyProbes {
    private TargetSelectorVerifyProbes() {
    }

    public static List<Probe> probes() {
        return List.of(Probe.of("verify/target_selectors", TargetSelectorVerifyProbes::targetSelectors));
    }

    private static void targetSelectors(ProbeContext context) {
        String failure = verifyTargetSelectors(context);
        if (failure != null) context.fail(failure);
    }

    // The actor's own rotation is written down, so none of the numbers depend on where the player happens to look.
    private static String verifyTargetSelectors(ProbeContext context) {
        ServerLevel level = context.level();
        // The old leg laid this patch 30 blocks out and 20 up, which is outside the arena; the offsets below only
        // need the beings clear of the probe's own player, so the patch sits at arena relative (12, 0, 12).
        BlockPos base = context.player().blockPosition().offset(8, 0, 8);
        LivingEntity actor = spawnProbe(context, base, null);
        LivingEntity closest = spawnProbe(context, base.offset(2, 0, 0), null);
        LivingEntity middle = spawnProbe(context, base.offset(4, 0, 0), null);
        LivingEntity outside = spawnProbe(context, base.offset(8, 0, 0), null);
        if (actor == null || closest == null || middle == null || outside == null)
            return "the selector probe could not spawn its beings";
        // Yaw 0 faces +Z, which is the direction every ray and cone leg below is measured along.
        actor.setYRot(0.0F);
        actor.setXRot(0.0F);
        LivingEntity ahead = null, beside = null, inCone = null, beyondWall = null;
        try {
            Set<UUID> capped = selected(new AreaTargetSelector(new Constant(5.0D), false, 1, TargetOrder.NEAREST), actor);
            if (!capped.equals(Set.of(closest.getUUID())))
                return "an area capped at one nearest took " + capped.size() + " beings instead of the closest one";
            if (!selected(new AreaTargetSelector(new Constant(5.0D), false, 1, TargetOrder.FARTHEST), actor)
                    .equals(Set.of(middle.getUUID())))
                return "an area capped at one farthest did not take the farther being";
            if (selected(new AreaTargetSelector(new Constant(5.0D), false, 0, TargetOrder.NEAREST), actor).size() != 2)
                return "an uncapped area did not hold both beings inside it";
            // The cone: an angle either side of the look, and nothing outside it.
            ahead = spawnProbe(context, base.offset(0, 0, 2), null);
            beside = spawnProbe(context, base.offset(2, 0, 0), null);
            inCone = spawnProbe(context, base.offset(1, 0, 4), null);
            beyondWall = spawnProbe(context, base.offset(0, 0, 6), null);
            if (ahead == null || beside == null || inCone == null || beyondWall == null)
                return "the selector probe could not spawn the beings its ray and cone legs need";
            Set<UUID> cone = selected(new ConeTargetSelector(new Constant(8.0D), new Constant(30.0D), false, 0, TargetOrder.NEAREST), actor);
            if (!cone.contains(ahead.getUUID())) return "the cone missed the being straight ahead";
            if (!cone.contains(inCone.getUUID())) return "the cone missed a being inside its angle";
            if (cone.contains(beside.getUUID())) return "the cone caught a being ninety degrees off its look";
            // The ray: a cylinder along the look, stopped by a block in the way.
            if (!level.setBlockAndUpdate(base.offset(0, 0, 3), Blocks.STONE.defaultBlockState()))
                return "the selector probe could not place the wall its ray leg needs";
            Set<UUID> ray = selected(new RayTargetSelector(new Constant(8.0D), new Constant(0.6D), false, 0, TargetOrder.NEAREST), actor);
            if (!ray.contains(ahead.getUUID())) return "the ray missed the being in front of it";
            if (ray.contains(beside.getUUID())) return "the ray caught a being beside it";
            if (ray.contains(beyondWall.getUUID())) return "the ray reached past the block in its way";
            return null;
        } finally {
            for (LivingEntity being : new LivingEntity[]{actor, closest, middle, outside, ahead, beside, inCone, beyondWall})
                if (being != null) being.discard();
        }
    }

    private static Set<UUID> selected(TargetSelector selector, Entity actor) {
        return selector.select(actor, FormulaContext.of(actor)).map(Entity::getUUID).collect(Collectors.toSet());
    }

    // One disposable probe entity carrying exactly one spirit root, so the element it strikes or is struck with is
    // never a question. A pig has no armour and no innate resistance; a null root leaves it carrying none.
    private static LivingEntity spawnProbe(ProbeContext context, BlockPos pos, @Nullable Identifier root) {
        ServerLevel level = context.level();
        Pig pig = EntityType.PIG.create(level, EntitySpawnReason.COMMAND);
        if (pig == null) return null;
        pig.setNoAi(true);
        pig.setPos(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
        level.addFreshEntity(pig);
        if (root != null)
            CultivationIdentityService.grantSpiritRoot(pig, context.require(MxtResourceKeys.SPIRIT_ROOT, root));
        return pig;
    }
}
