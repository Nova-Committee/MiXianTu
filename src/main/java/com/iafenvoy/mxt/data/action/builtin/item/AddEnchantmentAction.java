package com.iafenvoy.mxt.data.action.builtin.item;

import com.iafenvoy.mxt.data.context.action.ItemActionContext;

import com.iafenvoy.mxt.data.action.ItemAction;
import com.iafenvoy.mxt.util.codec.CollectionCodecs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments.Mutable;
import org.jspecify.annotations.NonNull;

/**
 * Adds or upgrades enchantments on the acted item stack.
 */
public record AddEnchantmentAction(Object2IntMap<Holder<Enchantment>> enchantments,
                                   boolean override) implements ItemAction {
    public static final MapCodec<AddEnchantmentAction> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            CollectionCodecs.intMap(Enchantment.CODEC).fieldOf("enchantments").forGetter(AddEnchantmentAction::enchantments),
            Codec.BOOL.optionalFieldOf("override", false).forGetter(AddEnchantmentAction::override)
    ).apply(i, AddEnchantmentAction::new));

    @Override
    public void execute(@NonNull ItemActionContext ctx) {
        Entity holder = ctx.holder();
        ItemStack stack = ctx.stack();
        Mutable enchantments = new Mutable(stack.getAllEnchantments(holder.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)));
        for (Entry<Holder<Enchantment>> entry : this.enchantments.object2IntEntrySet())
            if (this.override || enchantments.getLevel(entry.getKey()) < entry.getIntValue())
                enchantments.set(entry.getKey(), entry.getIntValue());
        stack.set(DataComponents.ENCHANTMENTS, enchantments.toImmutable());
    }

    @Override
    public @NonNull MapCodec<AddEnchantmentAction> codec() {
        return CODEC;
    }
}
