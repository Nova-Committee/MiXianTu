package com.iafenvoy.mxt.runtime.formation;

import com.iafenvoy.mxt.data.Formation;
import com.iafenvoy.mxt.event.FormationEvent.Deactivate;
import com.iafenvoy.mxt.event.FormationEvent.Tick;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.runtime.formation.FormationInstance.Snapshot;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.LevelTickEvent.Post;

import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;

/**
 * Low-frequency lifecycle dispatcher; it never scans unloaded chunks.
 */
public final class FormationWorldTicker {
    private static final FormationStructureValidator VALIDATOR = FormationStructureValidator.TEMPLATE;

    private FormationWorldTicker() {
    }

    public static void onLevelTick(Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.getGameTime() % 20L != 0L) return;
        FormationWorldData world = level.getData(MxtAttachments.FORMATION_WORLD);
        for (Entry<BlockPos, Snapshot> entry : world.formations().entrySet()) {
            Optional<Formation> definition = MxtDatapackRegistries.get(MxtDatapackRegistries.FORMATION, entry.getValue().formation());
            if (definition.isEmpty() || !VALIDATOR.matches(level, entry.getKey(), definition.get())) {
                world.remove(entry.getKey());
                definition.ifPresent(value -> value.deactivateAction().execute(level, entry.getKey(), FormulaContext.of(level)));
                NeoForge.EVENT_BUS.post(new Deactivate(level, entry.getKey(), entry.getValue().formation(), FormationInstance.restore(entry.getValue())));
                continue;
            }
            FormationInstance instance = FormationInstance.restore(entry.getValue());
            if (!definition.get().maintenanceCosts().isEmpty()) {
                Entity payer = instance.owner().map(level.getEntities()::get).orElse(null);
                if (payer == null || !FormationService.maintain(instance, definition.get(), payer.getData(MxtAttachments.RESOURCE_HOLDER), FormulaContext.of(payer)).maintained()) {
                    world.remove(entry.getKey());
                    definition.get().deactivateAction().execute(level, entry.getKey(), FormulaContext.of(level));
                    NeoForge.EVENT_BUS.post(new Deactivate(level, entry.getKey(), entry.getValue().formation(), instance));
                    continue;
                }
            }
            if (!NeoForge.EVENT_BUS.post(new Tick(level, entry.getKey(), entry.getValue().formation(), instance)).isCanceled()) {
                definition.get().tickAction().execute(level, entry.getKey(), FormulaContext.of(level));
                executeEntityTickAction(level, entry.getKey(), instance, definition.get());
                world.replace(entry.getKey(), instance);
            }
        }
    }

    /**
     * Executes once per 20 ticks for every entity inside the formation's spherical range.
     */
    private static void executeEntityTickAction(ServerLevel level, BlockPos controller, FormationInstance instance,
                                                Formation definition) {
        double radius = instance.radius();
        double radiusSquared = radius * radius;
        Vec3 center = controller.getCenter();
        for (Entity entity : level.getEntities(null, AABB.ofSize(center, radius * 2.0D, radius * 2.0D, radius * 2.0D))) {
            double distanceSquared = entity.distanceToSqr(center);
            if (distanceSquared > radiusSquared) continue;
            definition.entityTickAction().execute(entity, FormulaContext.of(entity, Map.of(
                    "formation_radius", radius,
                    "distance", Math.sqrt(distanceSquared)
            )));
        }
    }
}
