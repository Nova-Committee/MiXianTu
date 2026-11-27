package com.iafenvoy.mxt.item;

import com.iafenvoy.mxt.data.aura.SpiritStorageComponent;
import com.iafenvoy.mxt.registry.MxtDataComponents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.BundleItem;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.TooltipDisplay;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class SpiritStoneBagItem extends BundleItem {
    public static final int CAPACITY = 1024;

    public SpiritStoneBagItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean overrideStackedOnOther(@NonNull ItemStack self, @NonNull Slot slot, @NonNull ClickAction action, @NonNull Player player) {
        BundleContents contents = self.get(DataComponents.BUNDLE_CONTENTS);
        if (contents == null || self.getCount() != 1) return false;
        ItemStack other = slot.getItem();
        if (action == ClickAction.PRIMARY && !other.isEmpty()) {
            int amount = insertableCount(contents, other);
            ItemStack taken = amount > 0 ? slot.safeTake(amount, amount, player) : ItemStack.EMPTY;
            insert(self, contents, taken, player);
            return true;
        }
        if (action == ClickAction.SECONDARY && other.isEmpty()) {
            takeToSlot(self, contents, slot, player);
            return true;
        }
        return false;
    }

    @Override
    public boolean overrideOtherStackedOnMe(@NonNull ItemStack self, @NonNull ItemStack other, @NonNull Slot slot,
                                            @NonNull ClickAction action, @NonNull Player player, @NonNull SlotAccess carried) {
        BundleContents contents = self.get(DataComponents.BUNDLE_CONTENTS);
        if (contents != null && self.getCount() == 1 && action == ClickAction.PRIMARY && !other.isEmpty()) {
            int amount = slot.allowModification(player) ? insertableCount(contents, other) : 0;
            insert(self, contents, amount > 0 ? other.split(amount) : ItemStack.EMPTY, player);
            return true;
        }
        return super.overrideOtherStackedOnMe(self, other, slot, action, player, carried);
    }

    @Override
    public boolean isBarVisible(@NonNull ItemStack stack) {
        return !stack.getOrDefault(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY).isEmpty();
    }

    @Override
    public int getBarWidth(@NonNull ItemStack stack) {
        long count = stoneCount(stack.getOrDefault(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY));
        return 1 + (int) (Math.min(count, CAPACITY) * 12 / CAPACITY);
    }

    @Override
    public int getBarColor(@NonNull ItemStack stack) {
        return stoneCount(stack.getOrDefault(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY)) >= CAPACITY ? 0xFFFF5555 : 0xFF707FFF;
    }

    @Override
    public @NonNull Optional<TooltipComponent> getTooltipImage(@NonNull ItemStack stack) {
        if (!stack.getOrDefault(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT).shows(DataComponents.BUNDLE_CONTENTS))
            return Optional.empty();
        return Optional.ofNullable(stack.get(DataComponents.BUNDLE_CONTENTS)).map(BagTooltip::new);
    }

    public static long stoneCount(BundleContents contents) {
        return contents.items().stream().mapToLong(ItemStackTemplate::count).sum();
    }

    private static int insertableCount(BundleContents contents, ItemStack input) {
        if (!(input.getItem() instanceof SpiritStoneItem) || !BundleContents.canItemBeInBundle(input)) return 0;
        long remaining = CAPACITY - stoneCount(contents);
        int amount = (int) Math.max(0, Math.min(remaining, Math.min(input.getCount(), input.getMaxStackSize())));
        return amount < input.getCount() && hasStoredAura(input) ? 0 : amount;
    }

    private static boolean hasStoredAura(ItemInstance stack) {
        SpiritStorageComponent storage = stack.get(MxtDataComponents.SPIRIT_STORAGE.get());
        return storage != null && storage.perItem() && !storage.isEmpty();
    }

    private static void insert(ItemStack bag, BundleContents contents, ItemStack input, Player player) {
        if (input.isEmpty()) {
            player.playSound(SoundEvents.BUNDLE_INSERT_FAIL, 1.0F, 1.0F);
            return;
        }

        List<ItemStackTemplate> items = new ArrayList<>(contents.items());
        ItemStackTemplate stone = ItemStackTemplate.fromNonEmptyStack(input);
        int maxStackSize = input.getMaxStackSize();
        if (stone.count() < maxStackSize && !hasStoredAura(input)) {
            for (int i = 0; i < items.size(); i++) {
                ItemStackTemplate entry = items.get(i);
                if (entry.count() >= maxStackSize || !entry.item().equals(stone.item()) || !entry.components().equals(stone.components()))
                    continue;
                int merged = Math.min(stone.count(), maxStackSize - entry.count());
                items.remove(i);
                items.addFirst(entry.withCount(entry.count() + merged));
                input.shrink(merged);
                break;
            }
        }
        if (!input.isEmpty()) items.addFirst(stone.withCount(input.getCount()));
        store(bag, items, player);
        player.playSound(SoundEvents.BUNDLE_INSERT, 0.8F, 1.0F);
    }

    private static void takeToSlot(ItemStack bag, BundleContents contents, Slot slot, Player player) {
        if (contents.isEmpty()) return;
        int index = Math.max(0, contents.getSelectedItemIndex());
        ItemStackTemplate stone = contents.items().get(index);
        ItemStack output = stone.create();
        if (output.isEmpty() || (hasStoredAura(stone) && slot.getMaxStackSize(output) < output.getCount())) return;
        int count = output.getCount();
        int remaining = slot.safeInsert(output).getCount();
        if (remaining == count) return;

        List<ItemStackTemplate> items = new ArrayList<>(contents.items());
        if (remaining == 0) items.remove(index);
        else items.set(index, stone.withCount(remaining));
        store(bag, items, player);
        player.playSound(SoundEvents.BUNDLE_REMOVE_ONE, 0.8F, 1.0F);
    }

    private static void store(ItemStack bag, List<ItemStackTemplate> items, Player player) {
        bag.set(DataComponents.BUNDLE_CONTENTS, items.isEmpty() ? BundleContents.EMPTY : new BundleContents(List.copyOf(items)));
        player.containerMenu.slotsChanged(player.getInventory());
    }

    public record BagTooltip(BundleContents contents) implements TooltipComponent {
    }
}
