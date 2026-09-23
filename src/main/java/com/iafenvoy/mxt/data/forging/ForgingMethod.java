package com.iafenvoy.mxt.data.forging;

import com.iafenvoy.mxt.data.IconReference;
import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.cost.Cost;
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
 * A single datapack-defined forging operation. {@code sound} is played at the table when the method is used, for
 * everyone in range, and defaults to the noise the table stands in for.
 */
public record ForgingMethod(int valueDelta, List<Cost> costs, EntityCondition condition,
                            Optional<IconReference> icon, int cooldown, SoundEvent sound) {
    public static final Codec<Holder<ForgingMethod>> CODEC = RegistryFixedCodec.create(MxtResourceKeys.FORGING_METHOD);
    public static final Codec<ForgingMethod> DIRECT_CODEC = RecordCodecBuilder.<ForgingMethod>create(i -> i.group(
            Codec.INT.fieldOf("value_delta").forGetter(ForgingMethod::valueDelta),
            Cost.LIST_CODEC.optionalFieldOf("costs", List.of()).forGetter(ForgingMethod::costs),
            EntityCondition.optionalCodec("condition").forGetter(ForgingMethod::condition),
            IconReference.CODEC.optionalFieldOf("icon").forGetter(ForgingMethod::icon),
            Codec.intRange(0, 72_000).optionalFieldOf("cooldown", 0).forGetter(ForgingMethod::cooldown),
            // By name rather than by id resolved later: a sound event is a built-in registry entry, so a typo
            // fails the datapack load instead of silently playing nothing.
            BuiltInRegistries.SOUND_EVENT.byNameCodec().optionalFieldOf("sound", SoundEvents.ANVIL_PLACE).forGetter(ForgingMethod::sound)
    ).apply(i, ForgingMethod::new)).validate(ForgingMethod::validate);

    private static DataResult<ForgingMethod> validate(ForgingMethod value) {
        if (value.valueDelta == 0) return DataResult.error(() -> "value_delta must not be zero");
        return DataResult.success(value);
    }

    // The item branch is materialised here because a datapack registry is parsed before item components are bound.
    public ItemStack iconStack() {
        return this.icon.flatMap(IconReference::stack).orElse(ItemStack.EMPTY);
    }

    // id covers what the icon cannot (no icon, or one drawn from a texture), where an empty stack would report
    // itself as "Air".
    public MutableComponent displayName(Identifier id) {
        ItemStack icon = this.iconStack();
        return icon.isEmpty() ? Component.literal(id.toString()) : icon.getHoverName().copy();
    }
}
