package com.iafenvoy.mxt.api;

import com.iafenvoy.mxt.runtime.spirit.SpiritPour;
import com.iafenvoy.mxt.runtime.spirit.SpiritSource;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * An {@link ItemAuraAccess} item that is filled by hand: holding it down pours the holder's own aura into it,
 * one whole unit a tick - the gesture {@link com.iafenvoy.mxt.runtime.spirit.SpiritChargeService} drives and
 * {@link com.iafenvoy.mxt.runtime.spirit.SpiritChargeHold} declares. Rates and costs belong to that gesture,
 * so none of the three questions below is about them.
 */
public interface UseItemAuraAccess extends ItemAuraAccess {
    /**
     * What this stack takes: one entry per aura, in the order a pour fills them. Empty falls back to the
     * {@code item_aura} definition matching the item, which describes one item - a stack of them then moves as
     * many times as it has items, while an item that answers here gives its numbers for the whole stack.
     * <p>
     * Asked by both sides of a connection: read the registries it is handed, not the running server, and answer
     * the same for the same stack.
     */
    default Optional<SpiritPour> pour(Provider registries, ItemStack stack) {
        return Optional.empty();
    }

    /**
     * Whether a tick of pouring is worth paying for right now, asked before anything moves - an item that would
     * refuse what a tick buys says so here rather than in {@link #onCharged}, where the payment is already made.
     * <p>
     * Default yes, right for a store that only takes; an item that fires itself once full answers no while that
     * is not worth doing (see {@link com.iafenvoy.mxt.runtime.talisman.TalismanService#canFireFrom}). Whether a
     * definition may be used by hand is a different question for a different caller.
     */
    default boolean canPourInto(@Nullable LivingEntity holder, ItemStack stack) {
        return true;
    }

    /**
     * Called by whoever moved aura into this store, once that move is done: what being filled means, if
     * anything. Real moves only - a simulated one changed nothing. The source carries both the place and the
     * actor because they differ: a store on a display stand is filled by an entity standing somewhere else.
     */
    default void onCharged(SpiritSource source, ItemStack stack) {
    }
}
