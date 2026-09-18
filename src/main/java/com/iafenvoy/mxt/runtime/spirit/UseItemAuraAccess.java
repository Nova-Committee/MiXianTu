package com.iafenvoy.mxt.runtime.spirit;

import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * An {@link ItemAuraAccess} item that is filled by hand: holding it down pours the holder's own aura into it,
 * one whole unit a tick - the gesture {@link SpiritChargeService} drives and {@link SpiritChargeHold} declares.
 * <p>
 * Implementing it means "this can be poured into", not "this is special": the spirit stone implements it and
 * answers {@link #pour} with the shared {@code item_aura} reading, while a store sized by the stack itself
 * answers for itself. How fast a tick moves and what it costs is the gesture's to state, so none of the three
 * questions below is about rates.
 */
public interface UseItemAuraAccess extends ItemAuraAccess {
    /**
     * What this stack takes: one entry per aura, in the order a pour fills them. Empty means this item has
     * nothing of its own to say, and the {@code item_aura} definition that matches it describes the store
     * instead - that definition describes one item, so a stack of them moves as many times as it has items,
     * while an item that answers here gives its numbers for the whole stack.
     * <p>
     * Asked by both sides of a connection, so it must read the registries it is handed rather than the running
     * server, and must answer the same for the same stack.
     */
    default Optional<SpiritPour> pour(Provider registries, ItemStack stack) {
        return Optional.empty();
    }

    /**
     * Whether a tick of pouring is worth paying for right now. {@link SpiritChargeService} asks this before it
     * moves anything, so an item that would refuse what a tick buys says so here rather than in
     * {@link #onCharged}, where the payment has already been made.
     * <p>
     * The default is yes, which is right for a store that only takes. An item that fires itself once full
     * answers no while that is not worth doing - see
     * {@link com.iafenvoy.mxt.runtime.talisman.TalismanService#canFireFrom}. Whether a definition may be used by
     * hand is a different question for a different caller, and a ward that only answers being filled still has to
     * be fillable.
     */
    default boolean canPourInto(@Nullable LivingEntity holder, ItemStack stack) {
        return true;
    }

    /**
     * Called by whoever moved aura into this store, once that move is done - what being filled means, if
     * anything. Real moves only: a simulated one changed nothing.
     * <p>
     * The source carries both the place and the actor because they differ: a store on a display stand is filled
     * by an entity standing somewhere else.
     */
    default void onCharged(SpiritSource source, ItemStack stack) {
    }
}

