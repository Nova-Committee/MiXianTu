package com.iafenvoy.mxt.data.condition.builtin.item;

import com.iafenvoy.mxt.data.condition.ItemCondition;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryFixedCodec;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

/**
 * Matches the serialized value of a data component using partial NBT comparison.
 */
public record ComponentCondition(Holder<DataComponentType<?>> component, CompoundTag nbt) implements ItemCondition {
    public static final MapCodec<ComponentCondition> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            RegistryFixedCodec.create(Registries.DATA_COMPONENT_TYPE).fieldOf("component").forGetter(ComponentCondition::component),
            CompoundTag.CODEC.fieldOf("nbt").forGetter(ComponentCondition::nbt)
    ).apply(i, ComponentCondition::new));

    @Override
    public boolean test(Entity holder, ItemStack stack, FormulaContext context) {
        if (!stack.has(this.component.value())) return false;
        return NbtUtils.compareNbt(this.nbt, serialize(this.component.value(), stack), true);
    }

    private static <T> Tag serialize(DataComponentType<T> type, ItemStack stack) {
        T value = stack.get(type);
        if (value == null) return new CompoundTag();
        return type.codecOrThrow().encodeStart(NbtOps.INSTANCE, value).result().orElseGet(CompoundTag::new);
    }

    @Override
    public MapCodec<ComponentCondition> codec() {
        return CODEC;
    }
}
