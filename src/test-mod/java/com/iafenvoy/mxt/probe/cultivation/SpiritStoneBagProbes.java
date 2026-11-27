package com.iafenvoy.mxt.probe.cultivation;

import com.iafenvoy.mxt.attachment.FloatHoldingItemAttachment;
import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.aura.ItemAuraComponent;
import com.iafenvoy.mxt.data.aura.SpiritStorageComponent;
import com.iafenvoy.mxt.item.SpiritStoneBagItem;
import com.iafenvoy.mxt.item.SpiritStoneItem;
import com.iafenvoy.mxt.probe.Probe;
import com.iafenvoy.mxt.probe.ProbeContext;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtDataComponents;
import com.iafenvoy.mxt.registry.MxtItems;
import com.iafenvoy.mxt.runtime.cultivation.ItemAuraService;
import com.iafenvoy.saturn.attachment.ResourceHolderAttachment;
import com.iafenvoy.saturn.registry.SaturnAttachments;
import com.iafenvoy.saturn.util.formula.FormulaContext;
import io.netty.buffer.Unpooled;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BundleContents;
import net.neoforged.neoforge.network.connection.ConnectionType;

import java.util.List;

public final class SpiritStoneBagProbes {
    private SpiritStoneBagProbes() {
    }

    public static List<Probe> probes() {
        return List.of(Probe.of("cultivation/spirit_stone_bag/storage", SpiritStoneBagProbes::storage),
                Probe.of("cultivation/spirit_stone_bag/fuel", SpiritStoneBagProbes::fuel),
                Probe.of("cultivation/spirit_stone_bag/aura_conservation", SpiritStoneBagProbes::auraConservation));
    }

    private static void auraConservation(ProbeContext context) {
        ItemStack original = MxtItems.SPIRIT_STONE.toStack(4);
        Holder<Aura> aura = ItemAuraService.type(context.registries(), original).orElseThrow();
        SpiritStoneItem.setStoredPerItem(original, aura, 2.0D);
        RegistryOps<Tag> ops = context.registries().createSerializationContext(NbtOps.INSTANCE);
        ItemStackTemplate template = ItemStackTemplate.fromNonEmptyStack(original);
        ItemStackTemplate loaded = ItemStackTemplate.CODEC.parse(ops, ItemStackTemplate.CODEC.encodeStart(ops, template).getOrThrow()).getOrThrow();
        ItemStack stack = loaded.create();
        ItemStack part = stack.split(1);
        check(context, "saved per-item aura is conserved when splitting a stack",
                close(SpiritStoneItem.storedPerItem(stack, aura, 0.0D) * stack.getCount()
                        + SpiritStoneItem.storedPerItem(part, aura, 0.0D) * part.getCount(), 8.0D));
        SpiritStoneItem stone = MxtItems.SPIRIT_STONE.get();
        double remainder = stone.extract(null, context.registries(), stack, aura, 2.0D, true, false);
        check(context, "external extraction updates the same stored aura as fuel",
                close(remainder, 0.0D) && !stack.has(MxtDataComponents.ITEM_AURA)
                        && close(SpiritStoneItem.storedPerItem(stack, aura, 0.0D) * stack.getCount(), 4.0D));

        ItemStack oldStone = MxtItems.SPIRIT_STONE.toStack(4);
        oldStone.set(MxtDataComponents.SPIRIT_STORAGE, SpiritStorageComponent.EMPTY.with(aura, 12.0D));
        check(context, "a stack-wide amount cannot be spent as per-stone aura",
                close(stone.extract(null, context.registries(), oldStone, aura, 1.0D, true, false), 1.0D));
        oldStone.remove(MxtDataComponents.SPIRIT_STORAGE);
        oldStone.set(MxtDataComponents.ITEM_AURA, new ItemAuraComponent(2.0D));
        check(context, "ordinary fuel data does not restore a stone to full",
                close(SpiritStoneItem.storedPerItem(oldStone, aura, 100.0D), 0.0D));
        check(context, "ordinary fuel rejects a stack-wide scalar",
                ItemAuraComponent.CODEC.parse(NbtOps.INSTANCE, DoubleTag.valueOf(9.0D)).result().isEmpty());
        ItemStack fuel = new ItemStack(Items.COAL, 3);
        fuel.set(MxtDataComponents.ITEM_AURA, new ItemAuraComponent(3.0D));
        ItemStack restored = ItemStack.CODEC.parse(ops, ItemStack.CODEC.encodeStart(ops, fuel).getOrThrow()).getOrThrow();
        ItemStack split = restored.split(2);
        check(context, "ordinary fuel preserves per-item amount on a save round trip",
                close(restored.get(MxtDataComponents.ITEM_AURA).remain(), 3.0D)
                        && close(split.get(MxtDataComponents.ITEM_AURA).remain(), 3.0D));
    }

    private static void storage(ProbeContext context) {
        ServerPlayer player = context.player();
        SpiritStoneBagItem bagItem = MxtItems.SPIRIT_STONE_BAG.get();
        ItemStack bag = MxtItems.SPIRIT_STONE_BAG.toStack();
        SimpleContainer items = new SimpleContainer(2);
        Slot slot = new Slot(items, 0, 0, 0);
        SlotAccess cursor = SlotAccess.of(() -> items.getItem(1), stack -> items.setItem(1, stack));

        slot.set(Items.DIAMOND.getDefaultInstance());
        bagItem.overrideStackedOnOther(bag, slot, ClickAction.PRIMARY, player);
        bagItem.overrideOtherStackedOnMe(bag, slot.getItem(), slot, ClickAction.PRIMARY, player, cursor);
        check(context, "rejects non-stones from either click direction", slot.getItem().is(Items.DIAMOND) && contents(bag).isEmpty());

        slot.set(MxtItems.SPIRIT_STONE.toStack(64));
        bagItem.overrideStackedOnOther(bag, slot, ClickAction.PRIMARY, player);
        check(context, "one stack fits with room remaining", slot.getItem().isEmpty() && stoneCount(bag) == 64
                && bagItem.isBarVisible(bag) && bagItem.getBarWidth(bag) < 13);
        int partialBarColor = bagItem.getBarColor(bag);

        for (int i = 0; i < 13; i++) {
            slot.set(MxtItems.SPIRIT_STONE.toStack(64));
            bagItem.overrideStackedOnOther(bag, slot, ClickAction.PRIMARY, player);
        }
        for (int i = 0; i < 2; i++)
            bagItem.overrideOtherStackedOnMe(bag, MxtItems.SPIRIT_STONE.toStack(32), slot, ClickAction.PRIMARY, player, cursor);
        check(context, "matching partial stacks merge to a legal stack", stoneCount(bag) == 960
                && contents(bag).size() == 15 && contents(bag).items().getFirst().count() == 64);

        slot.set(MxtItems.SPIRIT_STONE.toStack(63));
        bagItem.overrideStackedOnOther(bag, slot, ClickAction.PRIMARY, player);
        ItemStack medium = MxtItems.MEDIUM_SPIRIT_STONE.toStack(2);
        Holder<Aura> aura = ItemAuraService.type(context.registries(), medium).orElseThrow();
        SpiritStoneItem.setStoredPerItem(medium, aura, 2.0D);
        bagItem.overrideOtherStackedOnMe(bag, medium, slot, ClickAction.PRIMARY, player, cursor);
        check(context, "one remaining space does not split a stack with stored aura", medium.getCount() == 2 && stoneCount(bag) == 1023);
        SpiritStoneItem.setStoredPerItem(medium, aura, 0.0D);
        bagItem.overrideOtherStackedOnMe(bag, medium, slot, ClickAction.PRIMARY, player, cursor);
        check(context, "capacity is 1024 mixed stones", slot.getItem().isEmpty() && medium.getCount() == 1
                && stoneCount(bag) == 1024 && bagItem.getBarWidth(bag) == 13 && bagItem.getBarColor(bag) != partialBarColor
                && contents(bag).items().stream().noneMatch(stone -> stone.create().isEmpty()));
        slot.set(MxtItems.HIGH_SPIRIT_STONE.toStack());
        bagItem.overrideStackedOnOther(bag, slot, ClickAction.PRIMARY, player);
        bagItem.overrideOtherStackedOnMe(bag, medium, slot, ClickAction.PRIMARY, player, cursor);
        check(context, "full bag leaves both input locations intact", slot.getItem().is(MxtItems.HIGH_SPIRIT_STONE.get())
                && medium.getCount() == 1 && stoneCount(bag) == 1024);

        RegistryOps<Tag> ops = context.registries().createSerializationContext(NbtOps.INSTANCE);
        ItemStack saved = ItemStack.CODEC.parse(ops, ItemStack.CODEC.encodeStart(ops, bag).getOrThrow()).getOrThrow();
        check(context, "full bag survives save round trip", contents(saved).equals(contents(bag)));
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), context.registries(), ConnectionType.NEOFORGE);
        try {
            ItemStack.STREAM_CODEC.encode(buffer, bag);
            check(context, "full bag survives network round trip", contents(ItemStack.STREAM_CODEC.decode(buffer)).equals(contents(bag))
                    && buffer.readableBytes() == 0);
        } finally {
            buffer.release();
        }

        bagItem.overrideOtherStackedOnMe(bag, ItemStack.EMPTY, slot, ClickAction.SECONDARY, player, cursor);
        check(context, "right click takes out a stone stack", cursor.get().is(MxtItems.MEDIUM_SPIRIT_STONE.get())
                && cursor.get().getCount() == 1 && cursor.get().has(MxtDataComponents.SPIRIT_STORAGE)
                && stoneCount(bag) == 1023 && contents(bag).items().getFirst().count() == 63);

        Slot limited = new Slot(new SimpleContainer(1), 0, 0, 0) {
            @Override
            public int getMaxStackSize() {
                return 5;
            }
        };
        bagItem.overrideStackedOnOther(bag, limited, ClickAction.SECONDARY, player);
        check(context, "partial withdrawal keeps the rest in the bag", limited.getItem().getCount() == 5
                && contents(bag).items().getFirst().count() == 58 && stoneCount(bag) == 1018);
    }

    private static void fuel(ProbeContext context) {
        ServerPlayer player = context.player();
        ItemStack main = MxtItems.SPIRIT_STONE.toStack();
        ItemStack drained = MxtItems.HIGH_SPIRIT_STONE.toStack();
        Holder<Aura> aura = ItemAuraService.type(context.registries(), drained).orElseThrow();
        SpiritStoneItem.setStoredPerItem(drained, aura, 0.0D);
        ItemStack partial = MxtItems.MEDIUM_SPIRIT_STONE.toStack();
        SpiritStoneItem.setStoredPerItem(partial, aura, 2.0D);
        ItemStack bag = MxtItems.SPIRIT_STONE_BAG.toStack();
        bag.set(DataComponents.BUNDLE_CONTENTS, new BundleContents(List.of(
                ItemStackTemplate.fromNonEmptyStack(drained), ItemStackTemplate.fromNonEmptyStack(partial))));
        player.setItemInHand(InteractionHand.MAIN_HAND, main);
        player.setItemInHand(InteractionHand.OFF_HAND, bag);

        ResourceHolderAttachment resources = player.getData(SaturnAttachments.RESOURCE_HOLDER);
        FormulaContext formula = FormulaContext.of(player);
        ItemAuraService.TickResult first = ItemAuraService.tick(player, resources, formula);
        FloatHoldingItemAttachment holding = player.getData(MxtAttachments.FLOAT_HOLDING_ITEM);
        check(context, "off-hand bag supplies fuel before loose stone", player.getMainHandItem() == main
                && player.getOffhandItem() == bag && holding.item().is(MxtItems.MEDIUM_SPIRIT_STONE.get())
                && contents(bag).size() == 1 && contents(bag).items().getFirst().create().is(MxtItems.HIGH_SPIRIT_STONE.get())
                && close(first.consumed(), 1.0D) && close(first.released(), 2.0D));

        ItemAuraService.TickResult second = ItemAuraService.tick(player, resources, formula);
        check(context, "exhausted stone returned to inventory", second.exhausted() && holding.item().isEmpty()
                && player.getInventory().contains(stack -> stack.is(MxtItems.MEDIUM_SPIRIT_STONE.get())
                && stack.getOrDefault(MxtDataComponents.SPIRIT_STORAGE, SpiritStorageComponent.EMPTY).isEmpty()
                && stack.has(MxtDataComponents.SPIRIT_STORAGE)));

        ItemAuraService.TickResult fallback = ItemAuraService.tick(player, resources, formula);
        check(context, "bag containing only drained stones falls back", fallback.active() && close(fallback.consumed(), 1.0D)
                && holding.item().is(MxtItems.SPIRIT_STONE.get()) && contents(bag).size() == 1);
        ItemAuraService.returnFloatingItem(player);

        ItemStack mainBag = MxtItems.SPIRIT_STONE_BAG.toStack();
        mainBag.set(DataComponents.BUNDLE_CONTENTS, new BundleContents(List.of(
                ItemStackTemplate.fromNonEmptyStack(MxtItems.SUPREME_SPIRIT_STONE.toStack(2)))));
        player.setItemInHand(InteractionHand.MAIN_HAND, mainBag);
        ItemAuraService.TickResult fresh = ItemAuraService.tick(player, resources, formula);
        check(context, "main-hand bag takes the whole fresh stack", holding.item().is(MxtItems.SUPREME_SPIRIT_STONE.get())
                && holding.item().getCount() == 2 && contents(mainBag).isEmpty() && close(fresh.consumed(), 2.0D));
        ItemAuraService.returnFloatingItem(player);
        check(context, "interrupted fuel is returned once", holding.item().isEmpty()
                && player.getInventory().contains(stack -> stack.is(MxtItems.SUPREME_SPIRIT_STONE.get())
                && stack.getCount() == 2 && !stack.has(MxtDataComponents.ITEM_AURA)
                && stack.getOrDefault(MxtDataComponents.SPIRIT_STORAGE, SpiritStorageComponent.EMPTY).perItem()));
    }

    private static BundleContents contents(ItemStack bag) {
        return bag.getOrDefault(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY);
    }

    private static int stoneCount(ItemStack bag) {
        return contents(bag).items().stream().mapToInt(ItemStackTemplate::count).sum();
    }

    private static boolean close(double actual, double expected) {
        return Math.abs(actual - expected) < 1.0E-6D;
    }

    private static void check(ProbeContext context, String name, boolean ok) {
        context.note("spirit stone bag: " + name + (ok ? " OK" : " MISMATCH"));
        context.check(name, ok);
    }
}
