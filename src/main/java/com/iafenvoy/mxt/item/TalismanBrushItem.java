package com.iafenvoy.mxt.item;

import com.iafenvoy.mxt.runtime.talisman.BrushPigmentService;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NonNull;

/**
 * A drawing brush. Besides carrying the pigment store it is the thing that fills it: dipping is the vanilla
 * bundle's shape at the item level, so it works in every container rather than only at the workstation.
 *
 * <p>Its bar is the vanilla item bar: the store is the tool's own state, so every slot and the cursor draw how much
 * is left for free and no screen has to. Vanilla damage is deliberately not used: zero damage means "broken" there,
 * which would clamp the value and mark an empty brush as broken.
 */
public class TalismanBrushItem extends Item {
    public TalismanBrushItem(Properties properties) {
        super(properties);
    }

    /**
     * A brush on the cursor, clicked onto a slot that holds pigment: one portion per click. The bundle answers the
     * primary click here and takes one item out on a secondary one; a brush has no item form of pigment to take
     * out, so a secondary click dips too instead of leaving vanilla to swap the brush with the stack.
     */
    @Override
    public boolean overrideStackedOnOther(@NonNull ItemStack self, @NonNull Slot slot, @NonNull ClickAction clickAction, @NonNull Player player) {
        if (!dips(clickAction) || !BrushPigmentService.isPigment(slot.getItem())) return false;
        // Both sides claim the click - or vanilla would move the stack - but only the server dips.
        if (player.level().isClientSide()) return true;
        dip(player, BrushPigmentService.dipFromSlot(self, slot, player));
        return true;
    }

    /**
     * A brush sitting in a slot, clicked with pigment on the cursor; the same gesture from the other side.
     */
    @Override
    public boolean overrideOtherStackedOnMe(@NonNull ItemStack self, @NonNull ItemStack other, @NonNull Slot slot, @NonNull ClickAction clickAction,
                                            @NonNull Player player, @NonNull SlotAccess carriedItem) {
        if (!dips(clickAction) || !BrushPigmentService.isPigment(other)) return false;
        if (!slot.allowModification(player)) return false;
        if (player.level().isClientSide()) return true;
        int added = BrushPigmentService.dipFromCarried(self, other);
        // The brush is the slot's own stack here, so the container is told about the write.
        if (added > 0) slot.setChanged();
        dip(player, added);
        return true;
    }

    private static boolean dips(ClickAction clickAction) {
        return clickAction == ClickAction.PRIMARY || clickAction == ClickAction.SECONDARY;
    }

    /**
     * The bundle's own feedback: a brush that could not take anything answers with the failure sound.
     */
    private static void dip(Player player, int added) {
        if (added > 0) player.playSound(SoundEvents.BUNDLE_INSERT, 0.8F, 0.8F + player.getRandom().nextFloat() * 0.4F);
        else player.playSound(SoundEvents.BUNDLE_INSERT_FAIL, 1.0F, 1.0F);
    }

    @Override
    public boolean isBarVisible(@NonNull ItemStack stack) {
        return BrushPigmentService.pigment(stack) > 0;
    }

    @Override
    public int getBarWidth(@NonNull ItemStack stack) {
        int capacity = BrushPigmentService.capacity();
        if (capacity <= 0) return 0;
        return Mth.clamp(Math.round(13.0F * BrushPigmentService.pigment(stack) / capacity), 0, 13);
    }

    @Override
    public int getBarColor(@NonNull ItemStack stack) {
        return 0xC0392B;
    }
}
