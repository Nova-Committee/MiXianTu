package com.iafenvoy.mxt.api;

import com.iafenvoy.mxt.data.aura.Aura;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A block entity that can exchange fractional amounts of one aura at a time.
 * Both operations return the part of {@code amount} that could not be moved.
 * Amounts and capacities must be finite and non-negative; simulation and exact refusals must not write.
 * <p>
 * {@code exact} is what a payment asks and a fill does not: {@code true} means the whole amount moves or none of it
 * does, so {@code 0} promises that all of it moved and the unchanged {@code amount} promises that nothing did. A store
 * that can check its own stock or room first must do so, because half a price taken by a refusal is half a price
 * lost. {@code false} is the logistics answer - move what fits and report the rest - and is what a caller that means
 * "fill this up" wants.
 * <p>
 * What is exchanged is named by an {@link Aura} and not by the {@code resource} it happens to be counted in.
 * A resource is only a number with bounds, an icon and bars - it does not know what its own number is for, so
 * it cannot say which aura a store holds. The aura is the identity; the resource is the unit it is measured
 * in, reached through {@code Aura#resource()} wherever a bound, a default or a formula needs it.
 * <p>
 * The registry access comes in as an argument rather than off the entity, because a store is also asked where
 * nobody stands - a display stand reading the stack it shows - and naming auras takes a registry either way.
 */
public interface AuraAccess {
    Object2DoubleMap<Holder<Aura>> getCapacity(@Nullable LivingEntity entity, @NotNull Provider registries);

    default double getCapacity(@Nullable LivingEntity entity, @NotNull Provider registries, Holder<Aura> aura) {
        return this.getCapacity(entity, registries).getDouble(aura);
    }

    double insert(@Nullable LivingEntity entity, @NotNull Provider registries, Holder<Aura> aura, double amount, boolean exact, boolean simulate);

    double extract(@Nullable LivingEntity entity, @NotNull Provider registries, Holder<Aura> aura, double amount, boolean exact, boolean simulate);

    static double requireNonNegative(double amount) {
        if (!Double.isFinite(amount) || amount < 0.0D)
            throw new IllegalArgumentException("Aura amount must be finite and non-negative");
        return amount;
    }
}
