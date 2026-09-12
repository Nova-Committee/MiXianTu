package com.iafenvoy.mxt.data.forging;

import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.resource.ResourceCost;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryFixedCodec;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;

/**
 * A single datapack-defined forging operation.
 *
 * <p>{@code sound} is what the strike sounds like: it is played at the table when the method is used, for
 * everyone in range, so a blueprint can give its steps their own voice. It defaults to the anvil being set
 * down - {@link SoundEvents#ANVIL_PLACE} - because that is the noise the table is standing in for, and
 * because a method that says nothing about sound should still sound like smithing.</p>
 */
public record ForgingMethod(int valueDelta, List<ResourceCost> costs, EntityCondition condition,
                            Optional<Identifier> displayIcon, int cooldown, SoundEvent sound) {
    public static final Codec<Holder<ForgingMethod>> CODEC = RegistryFixedCodec.create(MxtResourceKeys.FORGING_METHOD);
    public static final Codec<ForgingMethod> DIRECT_CODEC = RecordCodecBuilder.<ForgingMethod>create(i -> i.group(
            Codec.INT.fieldOf("value_delta").forGetter(ForgingMethod::valueDelta),
            ResourceCost.LIST_CODEC.optionalFieldOf("costs", List.of()).forGetter(ForgingMethod::costs),
            EntityCondition.optionalCodec("condition").forGetter(ForgingMethod::condition),
            Identifier.CODEC.optionalFieldOf("display_icon").forGetter(ForgingMethod::displayIcon),
            Codec.intRange(0, 72_000).optionalFieldOf("cooldown", 0).forGetter(ForgingMethod::cooldown),
            // By name rather than by id resolved later: a sound event is a built-in registry entry, not an
            // item stack, so unlike `display_icon` there is nothing here that has to wait for components to
            // be bound - and a typo fails the datapack load instead of silently playing nothing.
            BuiltInRegistries.SOUND_EVENT.byNameCodec().optionalFieldOf("sound", SoundEvents.ANVIL_PLACE).forGetter(ForgingMethod::sound)
    ).apply(i, ForgingMethod::new)).validate(ForgingMethod::validate);

    private static DataResult<ForgingMethod> validate(ForgingMethod value) {
        if (value.valueDelta == 0) return DataResult.error(() -> "value_delta must not be zero");
        return DataResult.success(value);
    }

    /**
     * The icon stack for the selector list, empty when the id is missing or unknown.
     *
     * <p>Kept as an id in the datapack because native datapack registries are parsed before item
     * components are bound.</p>
     */
    public ItemStack iconStack() {
        return this.displayIcon.flatMap(BuiltInRegistries.ITEM::getOptional).map(ItemStack::new).orElse(ItemStack.EMPTY);
    }

    /**
     * The name this method is listed under, wherever it is listed.
     *
     * <p>Its icon's own name, because that icon is what the selector grid draws and what the player
     * recognises. {@code id} is needed for the one case the icon cannot cover: a method with no
     * {@code display_icon} has a blank cell, and an empty stack would otherwise report itself as "Air".
     * A raw id is a poor name, but it is a name.</p>
     */
    public MutableComponent displayName(Identifier id) {
        ItemStack icon = this.iconStack();
        return icon.isEmpty() ? Component.literal(id.toString()) : icon.getHoverName().copy();
    }
}
