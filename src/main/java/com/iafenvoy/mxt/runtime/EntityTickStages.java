package com.iafenvoy.mxt.runtime;

import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/**
 * The per-entity tick of the pipeline modules, running the stages they registered in a declared order. A module
 * registers into a named slot instead of relying on the order its own event handler happens to be called in; the
 * order is part of the table below, so moving a stage is a visible edit rather than a side effect of where a call
 * was added.
 *
 * <p>Server side only, once per living entity per tick.
 */
@EventBusSubscriber
public final class EntityTickStages {
    // The declared order. Numbers are spacing, not importance: anything may be inserted between them.
    public static final int RESOURCES = 100;
    public static final int AURA_REGEN = 200;
    public static final int TRIGGERS = 300;
    public static final int ATTRIBUTES = 400;
    public static final int CURIOS = 500;
    public static final int PROGRESSION = 510;
    public static final int ABILITIES = 600;
    public static final int CASTS = 700;
    public static final int CHANNEL = 800;

    // Ordered on the first server tick, which is after every module has registered; server thread only.
    private static List<Stage> ordered;

    private EntityTickStages() {
    }

    public static void register(String id, int order, Consumer<LivingEntity> handler) {
        ModuleHooks.register(Stage.class, new Stage(id, order, handler));
    }

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity entity) || entity.level().isClientSide()) return;
        for (Stage stage : ordered()) stage.handler().accept(entity);
    }

    private static List<Stage> ordered() {
        List<Stage> stages = ordered;
        if (stages == null)
            ordered = stages = ModuleHooks.all(Stage.class).stream()
                    .sorted(Comparator.comparingInt(Stage::order)).toList();
        return stages;
    }

    // One stage: the id names it in a report, the order places it, and the handler does the work.
    record Stage(String id, int order, Consumer<LivingEntity> handler) {
    }
}
