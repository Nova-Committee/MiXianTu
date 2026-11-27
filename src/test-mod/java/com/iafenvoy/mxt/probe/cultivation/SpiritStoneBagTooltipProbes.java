package com.iafenvoy.mxt.probe.cultivation;

import com.iafenvoy.mxt.probe.Probe;
import com.iafenvoy.mxt.probe.ProbeContext;
import com.iafenvoy.mxt.registry.MxtItems;
import com.iafenvoy.mxt.screen.item.SpiritStoneBagMouseActions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.BundleMouseActions;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.ItemSlotMouseAction;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.item.BundleSelectedItemSpecialRenderer;
import net.minecraft.client.renderer.item.TrackingItemStackRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BundleItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BundleContents;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class SpiritStoneBagTooltipProbes {
    private SpiritStoneBagTooltipProbes() {
    }

    public static List<Probe> probes() {
        return List.of(Probe.of("cultivation/spirit_stone_bag/client_tooltip", SpiritStoneBagTooltipProbes::layout),
                Probe.of("cultivation/spirit_stone_bag/client_model", SpiritStoneBagTooltipProbes::model));
    }

    private static void layout(ProbeContext context) {
        List<ItemStackTemplate> stones = List.of(
                new ItemStackTemplate(MxtItems.SPIRIT_STONE, 64),
                new ItemStackTemplate(MxtItems.MEDIUM_SPIRIT_STONE, 64),
                new ItemStackTemplate(MxtItems.HIGH_SPIRIT_STONE, 64),
                new ItemStackTemplate(MxtItems.SUPREME_SPIRIT_STONE, 64),
                new ItemStackTemplate(MxtItems.SPIRIT_STONE, 1));

        RecordingGraphics two = extract(stones.subList(0, 2));
        check(context, "two stacks align with the right edge", two.icons.equals(List.of(
                new Icon(MxtItems.MEDIUM_SPIRIT_STONE.get(), 76, 4), new Icon(MxtItems.SPIRIT_STONE.get(), 52, 4))));
        check(context, "tooltip uses the bag's 1024-stone capacity", two.capacity.equals(List.of(128L, 1024)));

        RecordingGraphics five = extract(stones);
        check(context, "the partial row sits at the top right", five.icons.size() == 5
                && five.icons.getLast().equals(new Icon(MxtItems.SPIRIT_STONE.get(), 76, 4)));

        RecordingGraphics full = extract(Collections.nCopies(16, stones.getFirst()));
        check(context, "overflow is shown in the bottom-right cell", full.icons.size() == 11
                && "+320".equals(full.overflow) && full.overflowX == 84 && full.overflowY == 58);

        check(context, "an empty bag shows zero capacity used", extract(List.of()).capacity.equals(List.of(0L, 1024)));
    }

    private static void model(ProbeContext context) {
        Minecraft minecraft = Minecraft.getInstance();
        ItemStack bag = MxtItems.SPIRIT_STONE_BAG.toStack();
        bag.set(DataComponents.BUNDLE_CONTENTS, new BundleContents(List.of(new ItemStackTemplate(MxtItems.SPIRIT_STONE, 4))));
        Slot slot = new Slot(new SimpleContainer(1), 0, 0, 0);
        slot.set(bag);
        List<ItemSlotMouseAction> registered = new ArrayList<>();
        Inventory inventory = context.player().getInventory();
        ContainerScreen screen = new ContainerScreen(ChestMenu.oneRow(0, inventory), inventory, Component.empty()) {
            @Override
            public void addItemSlotMouseAction(@NonNull ItemSlotMouseAction action) {
                super.addItemSlotMouseAction(action);
                registered.add(action);
            }
        };
        screen.init(320, 240);
        check(context, "the screen registers one spirit stone bag action",
                registered.stream().filter(SpiritStoneBagMouseActions.class::isInstance).count() == 1);
        ItemSlotMouseAction actions = registered.stream().filter(SpiritStoneBagMouseActions.class::isInstance).findFirst().orElseThrow();
        BundleMouseActions vanilla = new BundleMouseActions(minecraft);
        check(context, "the bag action matches the bag outside the dye recipe tag",
                actions.matches(slot) && !vanilla.matches(slot) && !bag.is(ItemTags.BUNDLES));
        slot.set(Items.BUNDLE.getDefaultInstance());
        check(context, "vanilla bundles keep their own scroll action", vanilla.matches(slot) && !actions.matches(slot));
        slot.set(Items.STONE.getDefaultInstance());
        check(context, "other items do not match the bag action", !actions.matches(slot));
        registered.clear();
        screen.resize(360, 240);
        check(context, "the resize reinstalls the bag action",
                registered.stream().filter(SpiritStoneBagMouseActions.class::isInstance).count() == 1);

        check(context, "the bag starts closed in the inventory", !hasSelectedItemLayer(minecraft, bag, ItemDisplayContext.GUI));
        BundleItem.toggleSelectedItem(bag, 0);
        check(context, "the GUI model displays the selected stone", hasSelectedItemLayer(minecraft, bag, ItemDisplayContext.GUI));
        check(context, "the held model stays closed", !hasSelectedItemLayer(minecraft, bag, ItemDisplayContext.FIRST_PERSON_RIGHT_HAND));
    }

    private static boolean hasSelectedItemLayer(Minecraft minecraft, ItemStack bag, ItemDisplayContext displayContext) {
        TrackingItemStackRenderState state = new TrackingItemStackRenderState();
        minecraft.getItemModelResolver().updateForTopItem(state, bag, displayContext, null, null, 0);
        return state.getModelIdentity() instanceof List<?> layers
                && layers.stream().anyMatch(BundleSelectedItemSpecialRenderer.class::isInstance);
    }

    private static RecordingGraphics extract(List<ItemStackTemplate> stones) {
        ItemStack bag = MxtItems.SPIRIT_STONE_BAG.toStack();
        bag.set(DataComponents.BUNDLE_CONTENTS, new BundleContents(stones));
        ClientTooltipComponent tooltip = ClientTooltipComponent.create(bag.getTooltipImage().orElseThrow());
        Minecraft minecraft = Minecraft.getInstance();
        RecordingGraphics graphics = new RecordingGraphics(minecraft);
        Font font = minecraft.font;
        tooltip.extractImage(font, 0, 0, tooltip.getWidth(font), tooltip.getHeight(font), graphics);
        return graphics;
    }

    private static void check(ProbeContext context, String name, boolean success) {
        context.note("spirit stone bag tooltip: " + name + (success ? " OK" : " MISMATCH"));
        context.check(name, success);
    }

    private record Icon(Item item, int x, int y) {
    }

    private static final class RecordingGraphics extends GuiGraphicsExtractor {
        private final List<Icon> icons = new ArrayList<>();
        private List<Object> capacity = List.of();
        private String overflow = "";
        private int overflowX;
        private int overflowY;

        private RecordingGraphics(Minecraft minecraft) {
            super(minecraft, new GuiRenderState(), 0, 0);
        }

        @Override
        public void item(ItemStack stack, int x, int y, int seed) {
            this.icons.add(new Icon(stack.getItem(), x, y));
        }

        @Override
        public void centeredText(@NonNull Font font, Component text, int x, int y, int color) {
            if (text.getContents() instanceof TranslatableContents contents
                    && contents.getKey().equals("item.mxt.spirit_stone_bag.capacity"))
                this.capacity = List.of(contents.getArgs());
        }

        @Override
        public void centeredText(@NonNull Font font, @NonNull String text, int x, int y, int color) {
            this.overflow = text;
            this.overflowX = x;
            this.overflowY = y;
        }
    }
}
