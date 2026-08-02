package com.iafenvoy.mxt.runtime.formation;

import com.iafenvoy.mxt.data.formation.FormationDefinition;
import com.iafenvoy.mxt.event.FormationEvent.Deactivate;
import com.iafenvoy.mxt.event.FormationEvent.Tick;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtTypeRegistries;
import com.iafenvoy.mxt.runtime.behavior.BehaviorContext;
import com.iafenvoy.mxt.runtime.behavior.BehaviorContext.Kind;
import com.iafenvoy.mxt.runtime.behavior.DomainBehaviorService;
import com.iafenvoy.mxt.runtime.formation.FormationInstance.Snapshot;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.LevelTickEvent.Post;

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
            Optional<FormationDefinition> definition = MxtDatapackRegistries.get(MxtDatapackRegistries.FORMATION, entry.getValue().formation());
            if (definition.isEmpty() || !VALIDATOR.matches(level, entry.getKey(), definition.get())) {
                world.remove(entry.getKey());
                NeoForge.EVENT_BUS.post(new Deactivate(level, entry.getKey(), entry.getValue().formation(), FormationInstance.restore(entry.getValue())));
                continue;
            }
            FormationInstance instance = FormationInstance.restore(entry.getValue());
            if (!definition.get().maintenanceCosts().isEmpty()) {
                Entity payer = instance.owner().map(level.getEntities()::get).orElse(null);
                if (payer == null || !FormationService.maintain(instance, definition.get(), payer.getData(MxtAttachments.RESOURCE_HOLDER), FormulaContext.EMPTY).maintained()) {
                    world.remove(entry.getKey());
                    NeoForge.EVENT_BUS.post(new Deactivate(level, entry.getKey(), entry.getValue().formation(), instance));
                    continue;
                }
            }
            if (!NeoForge.EVENT_BUS.post(new Tick(level, entry.getKey(), entry.getValue().formation(), instance)).isCanceled()) {
                DomainBehaviorService.execute(MxtTypeRegistries.FORMATION_LIFECYCLE_BEHAVIOR, definition.get().maintainBehavior(), BehaviorContext.at(
                        Kind.FORMATION_MAINTAIN, entry.getValue().formation(), level, entry.getKey(), FormulaContext.EMPTY, true));
                DomainBehaviorService.execute(MxtTypeRegistries.FORMATION_LIFECYCLE_BEHAVIOR, definition.get().triggerBehavior(), BehaviorContext.at(
                        Kind.FORMATION_TRIGGER, entry.getValue().formation(), level, entry.getKey(), FormulaContext.EMPTY, true));
                world.replace(entry.getKey(), instance);
            }
        }
    }
}
