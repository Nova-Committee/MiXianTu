package com.iafenvoy.mxt.api;

import com.iafenvoy.mxt.data.aura.Aura;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

/**
 * A block entity that can exchange whole units of one aura at a time.
 * Both operations return the part of {@code amount} that could not be moved.
 * <p>
 * What is exchanged is named by an {@link Aura} and not by the {@code resource} it happens to be counted in.
 * A resource is only a number with bounds, an icon and bars - it does not know what its own number is for, so
 * it cannot say which aura a store holds. The aura is the identity; the resource is the unit it is measured
 * in, reached through {@code Aura#resource()} wherever a bound, a default or a formula needs it.
 */
public interface AuraAccess {
    Object2IntMap<Holder<Aura>> getCapacity(@Nullable LivingEntity entity);

    default int getCapacity(@Nullable LivingEntity entity, Holder<Aura> aura) {
        return this.getCapacity(entity).getInt(aura);
    }

    int insert(@Nullable LivingEntity entity, Holder<Aura> aura, int amount, boolean simulate);

    int extract(@Nullable LivingEntity entity, Holder<Aura> aura, int amount, boolean simulate);

    static int requireNonNegative(int amount) {
        if (amount < 0) throw new IllegalArgumentException("Aura amount must not be negative");
        return amount;
    }
}
