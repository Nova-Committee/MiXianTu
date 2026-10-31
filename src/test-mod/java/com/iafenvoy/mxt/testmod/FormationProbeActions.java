package com.iafenvoy.mxt.testmod;

import com.iafenvoy.mxt.data.formation.FormationActionType;
import com.iafenvoy.mxt.data.formation.FormationIntervals;
import com.iafenvoy.mxt.registry.MxtRegistries;
import com.iafenvoy.mxt.runtime.formation.FormationActionContext;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.List;

/**
 * A formation module that writes down every beat it is offered, so one probe can assert the framework's four dispatch
 * points on both scopes without a live array.
 */
public final class FormationProbeActions {
    public static final DeferredRegister<MapCodec<? extends FormationActionType>> REGISTRY =
            DeferredRegister.create(MxtRegistries.FORMATION_ACTION_TYPE, MxtTestMod.MOD_ID);
    public static final DeferredHolder<MapCodec<? extends FormationActionType>, MapCodec<BeatRecorderFormationAction>> BEATS =
            REGISTRY.register("beats", () -> BeatRecorderFormationAction.CODEC);
    private static final List<String> CALLS = new ArrayList<>();

    private FormationProbeActions() {
    }

    public static void reset() {
        CALLS.clear();
    }

    public static List<String> calls() {
        return List.copyOf(CALLS);
    }

    public record BeatRecorderFormationAction(String tag, int intervalTicks) implements FormationActionType {
        public static final MapCodec<BeatRecorderFormationAction> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.STRING.fieldOf("tag").forGetter(BeatRecorderFormationAction::tag),
                FormationIntervals.CODEC.optionalFieldOf("interval_ticks", FormationIntervals.TICK).forGetter(BeatRecorderFormationAction::intervalTicks)
        ).apply(i, BeatRecorderFormationAction::new));

        @Override
        public MapCodec<BeatRecorderFormationAction> codec() {
            return CODEC;
        }

        @Override
        public void activate(FormationActionContext context) {
            this.record("activate", context);
        }

        @Override
        public void tick(FormationActionContext context) {
            this.record("tick", context);
        }

        @Override
        public void interval(FormationActionContext context) {
            this.record("interval", context);
        }

        @Override
        public void inactivate(FormationActionContext context) {
            this.record("inactivate", context);
        }

        private void record(String beat, FormationActionContext context) {
            CALLS.add(this.tag + ":" + beat + (context.perEntity() ? ":entity" : ":array"));
        }
    }
}
