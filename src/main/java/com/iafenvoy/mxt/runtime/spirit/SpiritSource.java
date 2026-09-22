package com.iafenvoy.mxt.runtime.spirit;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Where an item was filled and who filled it. The position is the store's, not the actor's: a display stand
 * keeps a store too, and what fills it is a burst whose owner stands elsewhere, so formulas read the place the
 * item stands. The actor may be absent when nothing living moved the aura.
 * <p>
 * {@code consumedByHand} says which way in this is, and only the party assembling the source can know it: a
 * hand is the player holding the item, everything else is a placed store. What a talisman does differently is
 * decided by that one answer, so it travels here rather than as a caller-supplied parameter.
 */
public record SpiritSource(Level level, Vec3 position, @Nullable LivingEntity actor, boolean consumedByHand) {
    public static SpiritSource of(LivingEntity holder) {
        return new SpiritSource(holder.level(), holder.position(), holder, true);
    }

    public static SpiritSource placed(Level level, Vec3 position, @Nullable LivingEntity actor) {
        return new SpiritSource(level, position, actor, false);
    }
}
