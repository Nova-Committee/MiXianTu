package com.iafenvoy.mxt.screen.menu;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;

/**
 * Server-owned monitor readout. The client paints it and never re-evaluates a recipe.
 */
public record AlchemyFurnaceView(Status status, Numbers numbers) {
    private static final Codec<Double> FINITE = Codec.DOUBLE.validate(value -> Double.isFinite(value)
            ? DataResult.success(value) : DataResult.error(() -> "Non-finite alchemy readout"));
    public static final Codec<AlchemyFurnaceView> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Status.CODEC.fieldOf("status").forGetter(AlchemyFurnaceView::status),
            Numbers.CODEC.fieldOf("numbers").forGetter(AlchemyFurnaceView::numbers)
    ).apply(instance, AlchemyFurnaceView::new));
    public static final AlchemyFurnaceView EMPTY = new AlchemyFurnaceView(
            new Status(Component.empty(), Component.empty(), "IDLE",
                    Component.translatable("screen.mxt.alchemy.waiting"), false, false, false, 0, 0),
            new Numbers(0, 0, 0, 0, 0, 0, 0, 0));

    public record Status(Component furnace, Component quality, String phase,
                         Component message, boolean canStart, boolean locked, boolean formed,
                         int mainSlots, int auxiliarySlots) {
        public static final Codec<Status> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ComponentSerialization.CODEC.fieldOf("furnace").forGetter(Status::furnace),
                ComponentSerialization.CODEC.fieldOf("quality").forGetter(Status::quality),
                Codec.STRING.fieldOf("phase").forGetter(Status::phase),
                ComponentSerialization.CODEC.fieldOf("message").forGetter(Status::message),
                Codec.BOOL.fieldOf("can_start").forGetter(Status::canStart),
                Codec.BOOL.fieldOf("locked").forGetter(Status::locked),
                Codec.BOOL.fieldOf("formed").forGetter(Status::formed),
                Codec.INT.fieldOf("main_slots").forGetter(Status::mainSlots),
                Codec.INT.fieldOf("auxiliary_slots").forGetter(Status::auxiliarySlots)
        ).apply(instance, Status::new));
    }

    public record Numbers(double temperature, double target, double maximum, double recipeTarget, double tolerance,
                          long remaining, long total, int badTicks) {
        public static final Codec<Numbers> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                FINITE.fieldOf("temperature").forGetter(Numbers::temperature),
                FINITE.fieldOf("target").forGetter(Numbers::target),
                FINITE.fieldOf("maximum").forGetter(Numbers::maximum),
                FINITE.fieldOf("recipe_target").forGetter(Numbers::recipeTarget),
                FINITE.fieldOf("tolerance").forGetter(Numbers::tolerance),
                Codec.LONG.fieldOf("remaining").forGetter(Numbers::remaining),
                Codec.LONG.fieldOf("total").forGetter(Numbers::total),
                Codec.INT.fieldOf("bad_ticks").forGetter(Numbers::badTicks)
        ).apply(instance, Numbers::new));
    }
}
