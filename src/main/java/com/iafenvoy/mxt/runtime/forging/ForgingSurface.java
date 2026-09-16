package com.iafenvoy.mxt.runtime.forging;

import com.iafenvoy.mxt.data.forging.ForgingBlueprint;
import com.iafenvoy.mxt.data.forging.ForgingMaterial;
import com.iafenvoy.mxt.registry.MxtDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

/**
 * Inventory/UI-neutral contract for a placed forge table, so runtime services never depend on the concrete
 * block entity type. Slots run blueprint, then tool, then material input, then result output.
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
     * The block this surface is attached to, as a position rather than the surface itself: the container and
     * the session state that go with it are writable, and an event listener is not a place to write them from.
     */
    BlockPos pos();

    /**
     * Marks the container contents and session state dirty and pushes them to tracking clients.
     */
    void forgingChanged();

    /**
     * The slot classification, static because both the block entity and the menu classify the same fixed
     * layout, and the client half of the menu has no surface object to ask.
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
     * The one placement rule, shared by the block entity, which is the authority for hoppers and shift-clicks,
     * and the menu. A session freezes the output, the inputs and the blueprint; tools stay live.
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
