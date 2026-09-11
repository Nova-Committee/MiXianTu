package com.iafenvoy.mxt.runtime.forging;

import com.iafenvoy.mxt.data.forging.ForgingBlueprint;
import com.iafenvoy.mxt.data.forging.ForgingMaterial;
import com.iafenvoy.mxt.registry.MxtDataComponents;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

/**
 * Inventory/UI-neutral contract for a placed forge table.
 *
 * <p>Runtime services only see this interface, so they never depend on the concrete block entity
 * type. This mirrors {@code AlchemyWorkstation} and keeps the forge surface testable without a
 * level.</p>
 *
 * <h2>Slot layout</h2>
 * <pre>
 * 0..2     blueprint slots (blueprint/books, each carrying mxt:blueprint_binding)
 * 3..5     tool slots      (each carrying mxt:tool_binding)
 * 6..17    material input  (4x3)
 * 18       result output   (mayPlace is always false)
 * </pre>
 *
 * <p>On screen these are laid out as ten slot columns measured against the player inventory band:
 * blueprint, a half-column spacer, four input columns, a one-column arrow, the output, a
 * half-column spacer, then the tool column stacked to the right of the inputs.</p>
 */
public interface ForgingSurface {
    int BLUEPRINT_SLOTS = 3;
    int TOOL_SLOTS = 3;
    int INPUT_COLUMNS = 4;
    int INPUT_ROWS = 3;
    int INPUT_SLOTS = INPUT_COLUMNS * INPUT_ROWS;
    int BLUEPRINT_START = 0;
    int TOOL_START = BLUEPRINT_START + BLUEPRINT_SLOTS;
    int INPUT_START = TOOL_START + TOOL_SLOTS;
    int OUTPUT_SLOT = INPUT_START + INPUT_SLOTS;
    int TOTAL_SLOTS = OUTPUT_SLOT + 1;

    /**
     * The block entity container backing the slots above.
     */
    Container forgingContainer();

    /**
     * The persistent session state of this table.
     */
    ForgingTableState forgingState();

    /**
     * Marks the container contents and session state dirty and pushes them to tracking clients.
     */
    void forgingChanged();

    /**
     * The slot classification. Static rather than per-instance because both the block entity and the
     * menu half classify the same fixed layout, and the client half of the menu has no surface object
     * to ask.
     */
    static boolean isBlueprintSlot(int slot) {
        return slot >= BLUEPRINT_START && slot < BLUEPRINT_START + BLUEPRINT_SLOTS;
    }

    static boolean isToolSlot(int slot) {
        return slot >= TOOL_START && slot < TOOL_START + TOOL_SLOTS;
    }

    static boolean isInputSlot(int slot) {
        return slot >= INPUT_START && slot < INPUT_START + INPUT_SLOTS;
    }

    static boolean isOutputSlot(int slot) {
        return slot == OUTPUT_SLOT;
    }

    /**
     * Whether the surface accepts a stack in a slot.
     *
     * <p>This is the one placement rule, and it is static on purpose. Two halves ask it - the block
     * entity, which is the authority for hoppers and shift-clicks, and the menu, whose
     * {@code Slot#mayPlace} is what a plain click asks - and a second copy would be free to drift from
     * the first. The slot classification comes from the layout above, so the rule cannot disagree with
     * the slots it describes either.
     *
     * <h2>What a running session locks</h2>
     * Only what it owns. The <b>output</b> is written by the service, so it never takes anything by hand.
     * The <b>inputs</b> are frozen: the session consumed from them and hands back exactly what it took on
     * cancel or failure, so they have to still be there and have to have room.
     *
     * <p>The <b>tools</b> are deliberately <em>not</em> frozen. They are the session's configuration, not
     * its state, and keeping them live is the way out of having started with the wrong ones: the method
     * list is the blueprint's allowed methods intersected with the tools', so adding a hammer mid-session
     * widens what can be struck. Nothing about the session depends on them.
     *
     * <p>The <b>blueprint</b> is frozen while a session runs, because the session has locked one and a
     * second one appearing in the slot would let the grid claim something the table is not forging.
     *
     * <p>{@code locked} is the blueprint the surface locked a session in with. It is only reachable
     * while the surface is <em>unlocked</em>: {@code active} and "a blueprint is locked" are the same
     * condition ({@link ForgingTableState#active()}), and an active surface refuses input placements
     * before the material rule is consulted. A caller that has no blueprint to hand - the client half of
     * the menu, which cannot read the table at all - therefore still gets the right answer.
     */
    static boolean canPlace(int slot, ItemStack stack, boolean active, ForgingBlueprint locked) {
        if (isOutputSlot(slot)) return false;
        if (isToolSlot(slot)) return stack.has(MxtDataComponents.TOOL_BINDING.get());
        if (isBlueprintSlot(slot)) return !active && stack.has(MxtDataComponents.BLUEPRINT_BINDING.get());
        if (!isInputSlot(slot)) return false;
        if (active) return false;
        if (locked == null) return true;
        for (ForgingMaterial requirement : locked.input())
            if (requirement.matches(stack)) return true;
        return false;
    }

    /**
     * Whether a slot may be emptied. The mirror of {@link #canPlace}: a session locks what it owns, and
     * the tool slots are not part of that, so a hammer can be swapped mid-session as well as added.
     */
    static boolean canTake(int slot, boolean active) {
        return !active || isToolSlot(slot);
    }
}
