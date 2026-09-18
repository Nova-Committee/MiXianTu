package com.iafenvoy.mxt.runtime.spirit;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Where an item was filled and who filled it: the level and the place the store was at, and the entity that
 * answered for the transfer.
 * <p>
 * A store is not always in somebody's hands - a display stand keeps one too, and what fills it there is a
 * spirit burst whose owner is standing somewhere else entirely. So the position is not the actor's: an item
 * that answers being filled answers from where it stands, and its formulas read that place rather than the
 * place of whoever wound it. Carrying both is the whole reason this exists instead of an entity.
 * <p>
 * The actor may be absent when nothing living moved the aura. An item that needs one - to pay, to be credited,
 * to answer for an ability - has to say so rather than reach for a holder the source does not have.
 * <p>
 * {@code consumedByHand} says which way in this is, and only the party that assembles the source can know it:
 * a hand is the player holding the item, while everything else - a stand, a hopper, a machine - is a placed
 * store. What a talisman does differently is decided by that one answer, so it travels here rather than as a
 * parameter a caller could pass wrongly.
 */
public record SpiritSource(Level level, Vec3 position, @Nullable LivingEntity actor, boolean consumedByHand) {
    /**
     * A store in somebody's own hands: they are both where it is and who answers for it, and spending it is a
     * hand's spending.
     */
    public static SpiritSource of(LivingEntity holder) {
        return new SpiritSource(holder.level(), holder.position(), holder, true);
    }

    /**
     * A store standing somewhere: the actor filled it from wherever they are, and spending it is not a hand's
     * spending - so nothing about a hand's use window applies.
     */
    public static SpiritSource placed(Level level, Vec3 position, @Nullable LivingEntity actor) {
        return new SpiritSource(level, position, actor, false);
    }
}
