package com.iafenvoy.mxt.probe.ability;

import com.iafenvoy.mxt.probe.Probe;
import com.iafenvoy.mxt.probe.ProbeContext;
import com.iafenvoy.mxt.registry.MxtEntityTypes;
import com.iafenvoy.mxt.runtime.sword.SwordAuraEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * The sword aura entity: a flight of blades laid out in front of the caster and taken away again. It used to be a
 * visual scenario the operator looked at; the same spawn is now checked as a number, and the clear half proves the
 * tag is what removes them.
 */
public final class SwordAuraProbes {
    private static final String TAG = "mxt_test_sword_aura";
    private static final int COUNT = 12;
    private static final double DISTANCE = 6.0D;

    private SwordAuraProbes() {
    }

    public static List<Probe> probes() {
        return List.of(
                Probe.of("ability/sword_aura/spawn", SwordAuraProbes::spawn),
                Probe.of("ability/sword_aura/clear", SwordAuraProbes::clear));
    }

    private static void spawn(ProbeContext context) {
        List<SwordAuraEntity> spawned = lay(context);
        context.expect("spawned blades", spawned.size(), COUNT);
        context.check("every blade carries the probe tag", spawned.stream().allMatch(sword -> sword.entityTags().contains(TAG)));
        context.check("every blade is alive", spawned.stream().allMatch(Entity::isAlive));
        context.expect("blades visible to the level", tagged(context), COUNT);
    }

    private static void clear(ProbeContext context) {
        List<SwordAuraEntity> spawned = lay(context);
        context.expect("spawned blades", spawned.size(), COUNT);
        int removed = remove(context);
        context.expect("removed blades", removed, COUNT);
        context.expect("blades left behind", tagged(context), 0);
    }

    // The old command's own layout: a square of blades centred {@code distance} blocks ahead, in the caster's own
    // frame, so the flight is seen the way a player sees it.
    private static List<SwordAuraEntity> lay(ProbeContext context) {
        remove(context);
        Vec3 forward = context.player().getLookAngle().normalize();
        Vec3 right = forward.cross(new Vec3(0, 1, 0));
        if (right.lengthSqr() < 1.0E-6) right = new Vec3(1, 0, 0);
        else right = right.normalize();
        Vec3 up = right.cross(forward).normalize();
        Vec3 centre = context.player().getEyePosition().add(forward.scale(DISTANCE));
        int side = (int) Math.ceil(Math.sqrt(COUNT));
        List<SwordAuraEntity> spawned = new ArrayList<>();
        for (int index = 0; index < COUNT; index++) {
            Vec3 position = centre.add(right.scale((index % side - (side - 1) * 0.5) * 1.25))
                    .add(up.scale((index / side - (side - 1) * 0.5) * 2.4));
            SwordAuraEntity sword = MxtEntityTypes.SWORD_AURA.get().create(context.level(), EntitySpawnReason.COMMAND);
            if (sword == null) continue;
            sword.setPos(position);
            sword.setLifetime(12000);
            sword.addTag(TAG);
            if (context.level().addFreshEntity(sword)) spawned.add(sword);
        }
        return spawned;
    }

    private static int remove(ProbeContext context) {
        List<Entity> removed = new ArrayList<>();
        for (Entity entity : context.level().getAllEntities())
            if (entity instanceof SwordAuraEntity && entity.entityTags().contains(TAG)) removed.add(entity);
        removed.forEach(Entity::discard);
        return removed.size();
    }

    private static int tagged(ProbeContext context) {
        int count = 0;
        for (Entity entity : context.level().getAllEntities())
            if (entity instanceof SwordAuraEntity && entity.entityTags().contains(TAG)) count++;
        return count;
    }
}
