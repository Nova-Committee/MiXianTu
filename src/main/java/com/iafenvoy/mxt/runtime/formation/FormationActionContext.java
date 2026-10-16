package com.iafenvoy.mxt.runtime.formation;

import com.iafenvoy.mxt.data.context.action.BlockActionContext;
import com.iafenvoy.mxt.data.context.action.EntityActionContext;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * One beat of one formation module: the array it belongs to, and the entity when the beat is that entity's rather
 * than the array's own. It is a module's whole view of the world, which keeps the modules out of the ticker.
 */
public record FormationActionContext(ServerLevel level, BlockPos controller, FormationCarrier carrier,
                                     @Nullable Entity entity, double distanceSquared) {
    // Either the array's own beat or one entity's; the carrier is built once per pass and shared by every entity
    // that pass covers.
    public FormationActionContext(ServerLevel level, FormationCarrier carrier, @Nullable Entity entity) {
        this(level, carrier.controller(), carrier, entity,
                entity == null ? 0.0D : entity.distanceToSqr(carrier.center()));
    }

    public static FormationCarrier carrier(FormationInstance instance, BlockPos controller) {
        return new FormationCarrier(instance.formation(), controller, instance.radius(), instance.owners(),
                instance.activatedAt());
    }

    public static FormationActionContext array(ServerLevel level, BlockPos controller, FormationInstance instance) {
        return new FormationActionContext(level, carrier(instance, controller), null);
    }

    public boolean perEntity() {
        return this.entity != null;
    }

    public double radius() {
        return this.carrier.radius();
    }

    // The array's own beats know nothing but the level, which is one rule for all three of them; an entity's beat
    // adds the numbers a condition about that entity was always able to read.
    public FormulaContext formula() {
        if (this.entity == null) return FormulaContext.of(this.level);
        Vec3 center = this.carrier.center();
        return FormulaContext.of(this.entity, Map.of(
                "formation_radius", this.carrier.radius(),
                "distance", Math.sqrt(this.distanceSquared),
                "formation_x", center.x,
                "formation_y", center.y,
                "formation_z", center.z
        ));
    }

    public BlockActionContext block() {
        return new BlockActionContext(this.level, this.controller, FormulaContext.of(this.level));
    }

    // Only a per-entity beat calls this. The carrier rides along as extension data, which is what the owner and ally
    // conditions read.
    public EntityActionContext entityContext() {
        EntityActionContext context = new EntityActionContext(this.entity, this.formula());
        context.set(FormationCarrier.KEY, this.carrier);
        return context;
    }
}
