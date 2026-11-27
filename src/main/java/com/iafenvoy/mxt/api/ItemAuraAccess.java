package com.iafenvoy.mxt.api;

import com.iafenvoy.mxt.data.aura.Aura;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An item whose individual stack can exchange fractional amounts of one aura. {@code exact} requires the full
 * transfer or leaves the stack unchanged.
 * Both operations return the part of {@code amount} that could not be moved.
 * Amounts and capacities must be finite and non-negative; simulation and exact refusals must not write.
 * <p>
 * Stack-level storage used by the built-in item capability adapters. The gesture of holding a stack down and
 * pouring aura into it is {@link UseItemAuraAccess}, which extends this.
 * <p>
 * The caller supplies registry access explicitly because the actor may be absent.
 */
public interface ItemAuraAccess {
    Object2DoubleMap<Holder<Aura>> getCapacity(@Nullable LivingEntity entity, @NotNull Provider registries, ItemStack stack);

    default double getCapacity(@Nullable LivingEntity entity, @NotNull Provider registries, ItemStack stack, Holder<Aura> aura) {
        return this.getCapacity(entity, registries, stack).getDouble(aura);
    }

    double insert(@Nullable LivingEntity entity, @NotNull Provider registries, ItemStack stack, Holder<Aura> aura, double amount, boolean exact, boolean simulate);

    double extract(@Nullable LivingEntity entity, @NotNull Provider registries, ItemStack stack, Holder<Aura> aura, double amount, boolean exact, boolean simulate);
}
