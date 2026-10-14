package com.iafenvoy.mxt.api;

import com.iafenvoy.mxt.data.aura.Aura;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An item whose individual stack can exchange whole units of one aura - see {@link AuraAccess} for why what is
 * moved is an aura rather than the value it is counted in.
 * Both operations return the part of {@code amount} that could not be moved.
 * <p>
 * Storage only, asked from wherever the stack is: a display stand reads and writes a store through this, and the
 * shared {@code item_aura} definition describes one in these terms. The gesture of holding a stack down and
 * pouring aura into it is {@link UseItemAuraAccess}, which extends this.
 * <p>
 * The registry access comes in as an argument rather than off the entity for the same reason it does in
 * {@link AuraAccess}: the stand holding the stack is not the entity asking.
 */
public interface ItemAuraAccess {
    Object2IntMap<Holder<Aura>> getCapacity(@Nullable LivingEntity entity, @NotNull Provider registries, ItemStack stack);

    default int getCapacity(@Nullable LivingEntity entity, @NotNull Provider registries, ItemStack stack, Holder<Aura> aura) {
        return this.getCapacity(entity, registries, stack).getInt(aura);
    }

    int insert(@Nullable LivingEntity entity, @NotNull Provider registries, ItemStack stack, Holder<Aura> aura, int amount, boolean simulate);

    int extract(@Nullable LivingEntity entity, @NotNull Provider registries, ItemStack stack, Holder<Aura> aura, int amount, boolean simulate);
}
