package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.formation.FormationStroke;
import com.mojang.serialization.MapCodec;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The two things a figure can be drawn with. A datapack writes one of these ids in a stroke's {@code type}, and nothing
 * else can be added: the renderer walks a circle by angle and a polyline by arc length, and has no third walk.
 */
@SuppressWarnings("unused")
public final class MxtFormationStrokeTypes {
    public static final DeferredRegister<MapCodec<? extends FormationStroke>> REGISTRY = DeferredRegister.create(MxtRegistries.FORMATION_STROKE_TYPE, MiXianTu.MOD_ID);

    public static final DeferredHolder<MapCodec<? extends FormationStroke>, MapCodec<FormationStroke.Circle>> CIRCLE = REGISTRY.register("circle", () -> FormationStroke.Circle.CODEC);
    public static final DeferredHolder<MapCodec<? extends FormationStroke>, MapCodec<FormationStroke.Polyline>> POLYLINE = REGISTRY.register("polyline", () -> FormationStroke.Polyline.CODEC);

    private MxtFormationStrokeTypes() {
    }
}
