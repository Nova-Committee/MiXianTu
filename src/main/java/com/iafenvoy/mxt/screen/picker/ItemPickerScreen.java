package com.iafenvoy.mxt.screen.picker;

import com.iafenvoy.mxt.screen.picker.ItemPickerManager.PickerItem;
import com.iafenvoy.mxt.screen.picker.ItemPickerScreen.PickerMenu;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeInventoryListener;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.PreeditEvent;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * The creative tab's search page, over this mod's own contents: a title, a search well, a five by nine
 * scrollable grid and the player's hotbar underneath it.
 *
 * <p>Only the contents are this mod's. Everything else - the panel, the field, the scroller, the grid, the
 * hotbar row, and every way an item can be moved - is the tab's own machinery, so the picker stays in step
 * with the tab instead of being a lookalike that has to be maintained against it.</p>
 *
 * <h2>How an item leaves this screen</h2>
 *
 * <p>Nowhere does this screen hand out an item itself. Clicking the grid only moves the carried stack, which
 * is an illusion held on the client, exactly as it is in the tab; the carried stack belongs to the player's
 * real {@code InventoryMenu} rather than to this menu, which never exists on the server at all.</p>
 *
 * <p>An item becomes real one of two ways, and both are the vanilla creative channel:</p>
 * <ol>
 *   <li>It lands in a hotbar slot, either by being carried there or by the 1-9 swap keys. The inventory
 *       listener this screen keeps on the player's {@code InventoryMenu} notices the slot change and ships it
 *       as a {@code ServerboundSetCreativeModeSlotPacket}; and</li>
 *   <li>it is thrown out of the panel, which sends the same packet with slot {@code -1}.</li>
 * </ol>
 *
 * <p>That is what keeps the picker from being a way around creative mode. The packet is registered with a
 * codec modifier that refuses to decode it unless the <em>server's</em> copy of the player says creative, and
 * the handler behind it checks the same thing again, so a client that is not creative has nothing to send and
 * nothing to gain from sending it.</p>
 */
public final class ItemPickerScreen extends AbstractContainerScreen<PickerMenu> {
    /**
     * The grid the vanilla tab uses: five rows of nine 18px slots starting at (9, 18) inside a 195x136 panel
     * whose lower 112 pixels are the scrollable band.
     */
    private static final int COLUMNS = 9;
    private static final int ROWS = 5;
    private static final int SLOT_COUNT = COLUMNS * ROWS;
    private static final int SLOT_SIZE = 18;
    private static final int GRID_LEFT = 9;
    private static final int GRID_TOP = 18;
    private static final int GRID_HEIGHT = 112;
    private static final int PANEL_WIDTH = 195;
    private static final int PANEL_HEIGHT = 136;
    /**
     * The tab's hotbar row, which its texture already draws the frames for: nine slots at (9, 112), over the
     * player's real inventory rather than over the result list, so what is dropped there is a real item.
     */
    private static final int HOTBAR_LEFT = 9;
    private static final int HOTBAR_TOP = 112;
    private static final int HOTBAR_SLOTS = 9;
    /** The tab's field: 89 wide starting at (82, 6), one pixel inside the well's inner area. */
    private static final int SEARCH_LEFT = 82;
    private static final int SEARCH_TOP = 6;
    private static final int SEARCH_WIDTH = 89;
    private static final int SEARCH_HEIGHT = 9;
    /** The tab's scrollbar strip, and the area that grabs it - wider than the six pixel track it draws in. */
    private static final int SCROLLER_LEFT = 175;
    private static final int SCROLLER_WIDTH = 12;
    private static final int SCROLLER_HEIGHT = 15;
    private static final int SCROLLBAR_HIT_WIDTH = 14;
    /** The tab's label colour, so the title reads the same over the same texture. */
    private static final int LABEL_COLOR = -12566464;
    private static final int TEXT_COLOR = -1;

    private static final Identifier BACKGROUND = Identifier.withDefaultNamespace("textures/gui/container/creative_inventory/tab_item_search.png");
    /**
     * The tab's scroller. Both sprites are a six by thirty-two nine-slice, which is why the tab asks for them
     * twelve wide: the slicer stretches the middle to the requested size.
     */
    private static final Identifier SCROLLER = Identifier.withDefaultNamespace("container/creative_inventory/scroller");
    private static final Identifier SCROLLER_DISABLED = Identifier.withDefaultNamespace("container/creative_inventory/scroller_disabled");

    private static final Component SEARCH_NARRATION = Component.translatable("screen.mxt.item_picker.search");

    private final List<Candidate> entries;

    private List<Candidate> matched;
    private EditBox searchBox;
    private float scrollOffs;
    private boolean scrolling;
    /** Whether the last position the mouse was checked at was outside the panel, as the tab tracks it. */
    private boolean clickedOutside;
    /**
     * The tab's way of noticing that the player's own inventory changed: every slot the client edits is
     * shipped as a creative slot packet. Rebuilt with the screen, so it goes on in {@link #init}.
     */
    private @Nullable CreativeInventoryListener listener;

    /**
     * A picker over a caller-supplied list of stacks.
     *
     * <p>A factory rather than a constructor only because the category path needs the same shape over its own
     * entry type, and two constructors cannot differ by the element type of a {@code List}.</p>
     *
     * @param title the screen title, drawn in the panel header
     * @param items every stack the picker may offer, in display order
     */
    public static ItemPickerScreen over(Component title, List<ItemStack> items) {
        return new ItemPickerScreen(title, toCandidates(items));
    }

    private ItemPickerScreen(Component title, List<Candidate> entries) {
        super(new PickerMenu(player()), player().getInventory(), title, PANEL_WIDTH, PANEL_HEIGHT);
        this.entries = entries;
        this.matched = entries;
    }

    /**
     * A picker over the categories a server named, or null when there is no world to build it in.
     *
     * <p>The grid is built here, from the registries this client already has synced. Building the screen does
     * not open it; the caller decides when to show it, which is what lets it run inside the client packet
     * handler.</p>
     */
    public static @Nullable ItemPickerScreen opening(Component title, List<Identifier> categories) {
        Minecraft minecraft = Minecraft.getInstance();
        RegistryAccess access = minecraft.level == null ? null : minecraft.level.registryAccess();
        if (access == null || minecraft.player == null) return null;
        List<PickerItem> picked = new ArrayList<>();
        for (Identifier category : categories)
            ItemPickerManager.category(category).ifPresent(key -> picked.addAll(ItemPickerManager.itemsOf(access, key)));
        return new ItemPickerScreen(title, candidates(picked));
    }

    /**
     * The menu needs the inventory to point its hotbar slots at, and the container screen's superclass wants
     * the same inventory to name under the grid; neither exists without a player.
     */
    private static LocalPlayer player() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) throw new IllegalStateException("The item picker can only be built while a player is in a world");
        return player;
    }

    /**
     * Turns the catalogue's rows into searchable ones. A row is found by exactly the names the catalogue gave
     * it, so what a row answers to is decided where the row is described rather than guessed at here.
     */
    private static List<Candidate> candidates(List<PickerItem> items) {
        List<Candidate> candidates = new ArrayList<>(items.size());
        for (PickerItem item : items) candidates.add(candidate(item.stack(), item.names()));
        return List.copyOf(candidates);
    }

    /**
     * A caller hands over bare stacks, so the names it has nothing to say about are the two obvious ones: what
     * the stack is called, and the id it is registered under.
     */
    private static List<Candidate> toCandidates(List<ItemStack> items) {
        List<ItemStack> source = List.copyOf(items);
        List<Candidate> candidates = new ArrayList<>(source.size());
        for (ItemStack stack : source) {
            if (stack == null || stack.isEmpty()) continue;
            candidates.add(candidate(stack, List.of(stack.getHoverName(),
                    Component.literal(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()))));
        }
        return List.copyOf(candidates);
    }

    /**
     * One row, with its search index built once out of the names it answers to.
     *
     * <p>A row that declared no name at all still answers to what it looks like, which is what keeps "what you
     * can see, you can type" true for every row rather than only for the ones a provider remembered to
     * describe.</p>
     */
    private static Candidate candidate(ItemStack stack, List<Component> names) {
        StringBuilder index = new StringBuilder();
        for (Component name : names) {
            if (!index.isEmpty()) index.append(' ');
            index.append(name.getString());
        }
        if (index.isEmpty()) index.append(stack.getHoverName().getString());
        return new Candidate(stack, index.toString().toLowerCase(Locale.ROOT));
    }

    /**
     * Builds the tab's field, hands it the keyboard, and starts listening to the player's inventory.
     *
     * <p>Like the tab's, the field never gives focus up, and it is never drawn by the widget pass -
     * {@link #extractBackground} paints it inside the well instead, which is what keeps it in the background
     * stratum under the items.</p>
     */
    @Override
    protected void init() {
        super.init();
        this.searchBox = new EditBox(this.font, this.leftPos + SEARCH_LEFT, this.topPos + SEARCH_TOP,
                SEARCH_WIDTH, SEARCH_HEIGHT, SEARCH_NARRATION);
        this.searchBox.setMaxLength(50);
        // The well in the tab texture is the frame and the background, so the field draws neither.
        this.searchBox.setBordered(false);
        this.searchBox.setTextColor(TEXT_COLOR);
        this.searchBox.setInvertHighlightedTextColor(false);
        this.searchBox.setCanLoseFocus(false);
        this.addWidget(this.searchBox);
        this.setFocused(this.searchBox);
        this.searchBox.setFocused(true);
        this.refreshResults();
        this.listenToInventory();
    }

    /**
     * Follows the player's inventory, so that everything this screen writes into it - a hotbar slot filled from
     * the grid, a hotbar key copying a stack across - is sent on as a creative slot packet without every one
     * of those actions having to remember to send it.
     *
     * <p>The listener is a fresh one each time the widgets are rebuilt, and only ever one of them is attached,
     * which is why the previous one is dropped first.</p>
     */
    private void listenToInventory() {
        LocalPlayer player = this.minecraft.player;
        if (player == null) return;
        if (this.listener != null) player.inventoryMenu.removeSlotListener(this.listener);
        this.listener = new CreativeInventoryListener(this.minecraft);
        player.inventoryMenu.addSlotListener(this.listener);
    }

    @Override
    public void removed() {
        super.removed();
        LocalPlayer player = this.minecraft.player;
        if (player != null && this.listener != null) player.inventoryMenu.removeSlotListener(this.listener);
    }

    /**
     * The tab closes itself the moment creative mode does, so an operator who is dropped back into survival
     * with the picker still open does not keep a panel whose every action the server would now refuse.
     */
    @Override
    protected void containerTick() {
        super.containerTick();
        LocalPlayer player = this.minecraft.player;
        if (player != null && !player.hasInfiniteMaterials()) this.minecraft.setScreen(new InventoryScreen(player));
    }

    /** Keeps the search text and the scrolled row across a window resize, which rebuilds every widget. */
    @Override
    public void resize(int width, int height) {
        int oldRow = this.menu.getRowIndexForScroll(this.scrollOffs);
        String oldValue = this.searchBox == null ? "" : this.searchBox.getValue();
        super.resize(width, height);
        this.searchBox.setValue(oldValue);
        this.refreshResults();
        this.scrollOffs = this.menu.getScrollForRowIndex(oldRow);
        this.menu.scrollTo(this.scrollOffs);
    }

    /**
     * Filters the caller's list down to the entries matching every whitespace-separated token: a token
     * starting with {@code @} only matches the item's namespace, everything else is a substring of the row's
     * search index - its display name, its item id, and whatever text the catalogue added.
     *
     * <p>Because the grid is a window onto this list rather than the list itself, this is also what moves the
     * window: the menu is told to re-copy the first page of the new match set into its slots.</p>
     */
    private void refreshResults() {
        String search = this.searchBox == null ? "" : this.searchBox.getValue();
        List<Candidate> matched = new ArrayList<>();
        for (Candidate entry : this.entries)
            if (matches(entry, search)) matched.add(entry);
        this.matched = List.copyOf(matched);
        this.menu.items.clear();
        for (Candidate entry : this.matched) this.menu.items.add(entry.stack());
        this.scrollOffs = 0.0F;
        this.menu.scrollTo(this.scrollOffs);
    }

    private static boolean matches(Candidate entry, String search) {
        String query = search.trim().toLowerCase(Locale.ROOT);
        if (query.isEmpty()) return true;
        String namespace = BuiltInRegistries.ITEM.getKey(entry.stack().getItem()).getNamespace().toLowerCase(Locale.ROOT);
        for (String token : query.split("\\s+")) {
            if (token.isEmpty()) continue;
            if (token.charAt(0) == '@') {
                if (!namespace.contains(token.substring(1))) return false;
            } else if (!entry.haystack().contains(token)) return false;
        }
        return true;
    }

    /**
     * The tab's click handling with the tab machinery taken out. A click on the grid never asks the server for
     * anything - it only moves this side's carried stack - while a click on a hotbar slot runs against the
     * real inventory and is then shipped by the inventory listener.
     */
    @Override
    protected void slotClicked(@Nullable Slot slot, int slotId, int buttonNum, @NonNull ContainerInput containerInput) {
        LocalPlayer player = this.minecraft.player;
        if (player == null) return;
        if (this.isGridSlot(slot)) {
            this.searchBox.moveCursorToEnd(false);
            this.searchBox.setHighlightPos(0);
        }
        boolean quickKey = containerInput == ContainerInput.QUICK_MOVE;
        // A press that started outside the panel and ended on no slot is the tab's throw gesture.
        ContainerInput input = slotId == -999 && containerInput == ContainerInput.PICKUP ? ContainerInput.THROW : containerInput;
        if (input == ContainerInput.THROW && !player.canDropItems()) return;

        if (slot == null) {
            if (input != ContainerInput.QUICK_CRAFT) this.throwCarriedOutside(buttonNum);
            return;
        }
        // Nothing the grid will not give up - a locked or feature-disabled item - may be moved at all.
        if (!slot.mayPickup(player)) return;

        if (this.isGridSlot(slot) && input != ContainerInput.QUICK_CRAFT) {
            this.clickGridSlot(slot, buttonNum, quickKey, input);
        } else {
            this.menu.clicked(slot.index, buttonNum, input, player);
            player.inventoryMenu.broadcastChanges();
        }
    }

    /**
     * The tab's handling of a click on a creative grid slot, which is entirely local: the grid is a view over
     * the result list, so nothing is ever taken out of it, and the only thing that changes is the carried
     * stack. The item itself only becomes real once it is carried somewhere real or thrown.
     */
    private void clickGridSlot(Slot slot, int buttonNum, boolean quickKey, ContainerInput input) {
        LocalPlayer player = this.minecraft.player;
        if (player == null) return;
        ItemStack carried = this.menu.getCarried();
        ItemStack clicked = slot.getItem();
        switch (input) {
            // A hotbar key: a whole stack straight into that slot, or into the offhand for button 40.
            case SWAP -> {
                if (!clicked.isEmpty()) {
                    player.getInventory().setItem(buttonNum, clicked.copyWithCount(clicked.getMaxStackSize()));
                    player.inventoryMenu.broadcastChanges();
                }
            }
            // Middle click: fill the carried stack, leaving the grid alone.
            case CLONE -> {
                if (carried.isEmpty() && !clicked.isEmpty())
                    this.menu.setCarried(clicked.copyWithCount(clicked.getMaxStackSize()));
            }
            // Either drop key: one, or the whole stack, without touching the carried stack.
            case THROW -> {
                if (!clicked.isEmpty())
                    this.throwCreative(clicked.copyWithCount(buttonNum == 0 ? 1 : clicked.getMaxStackSize()));
            }
            // Pick up, top up a carried stack, or put the carried stack back.
            default -> {
                if (!carried.isEmpty() && !clicked.isEmpty() && ItemStack.isSameItemSameComponents(carried, clicked)) {
                    if (buttonNum == 0) {
                        if (quickKey) carried.setCount(carried.getMaxStackSize());
                        else if (carried.getCount() < carried.getMaxStackSize()) carried.grow(1);
                    } else {
                        carried.shrink(1);
                    }
                } else if (!clicked.isEmpty() && carried.isEmpty()) {
                    this.menu.setCarried(clicked.copyWithCount(quickKey ? clicked.getMaxStackSize() : clicked.getCount()));
                } else if (buttonNum == 0) {
                    this.menu.setCarried(ItemStack.EMPTY);
                } else if (!carried.isEmpty()) {
                    carried.shrink(1);
                }
            }
        }
    }

    /** Dropping the carried stack by clicking past the panel, which throws all of it or one of it. */
    private void throwCarriedOutside(int buttonNum) {
        if (this.menu.getCarried().isEmpty() || !this.clickedOutside) return;
        if (buttonNum == 0) {
            this.throwCreative(this.menu.getCarried());
            this.menu.setCarried(ItemStack.EMPTY);
        } else if (buttonNum == 1) {
            this.throwCreative(this.menu.getCarried().split(1));
        }
    }

    /**
     * Throws a stack out of the panel as a creative drop.
     *
     * <p>The tab routes this through {@code gameMode.handleCreativeModeItemDrop}, which refuses to send while
     * any container screen other than the tab is open - this screen is one of those, so the packet is built
     * here instead. The local throw and the guards are the ones that method applies.</p>
     */
    private void throwCreative(ItemStack stack) {
        LocalPlayer player = this.minecraft.player;
        ClientPacketListener connection = this.minecraft.getConnection();
        if (player == null || connection == null || stack.isEmpty()) return;
        if (!player.hasInfiniteMaterials()) return;
        if (!connection.isFeatureEnabled(stack.getItem().requiredFeatures())) return;
        player.drop(stack, true);
        connection.send(new ServerboundSetCreativeModeSlotPacket(-1, stack));
    }

    private boolean isGridSlot(@Nullable Slot slot) {
        return slot != null && slot.container == this.menu.grid;
    }

    /**
     * The tab's background, its field and its scroller - and nothing else. The panel border, the well, the
     * slot frames of both the grid and the hotbar row, and the scrollbar track are all part of the texture.
     */
    @Override
    public void extractBackground(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, this.leftPos, this.topPos, 0.0F, 0.0F,
                this.imageWidth, this.imageHeight, 256, 256);
        if (this.insideScrollbar(mouseX, mouseY)) {
            if (this.canScroll()) graphics.requestCursor(this.scrolling ? CursorTypes.RESIZE_NS : CursorTypes.POINTING_HAND);
            else graphics.requestCursor(CursorTypes.NOT_ALLOWED);
        }
        this.searchBox.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, this.canScroll() ? SCROLLER : SCROLLER_DISABLED,
                this.leftPos + SCROLLER_LEFT, this.topPos + GRID_TOP + this.scrollerOffset(),
                SCROLLER_WIDTH, SCROLLER_HEIGHT);
    }

    /**
     * Where the scroller sits, in the tab's own terms: it travels the band less its own height and the two
     * pixels the texture leaves under it.
     */
    private int scrollerOffset() {
        return (int) ((GRID_HEIGHT - SCROLLER_HEIGHT - 2) * this.scrollOffs);
    }

    /**
     * Labels are drawn after the widgets so the title can share the top line with the search well: the tab
     * leaves no room above the well for one. Vanilla draws its title at (8, 6) for the same reason.
     */
    @Override
    protected void extractLabels(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(this.font, this.title, 8, 6, LABEL_COLOR, false);
    }

    /**
     * Clicking the scrollbar strip grabs it. A scrollbar with nothing to scroll still swallows the click,
     * exactly as the tab's does.
     */
    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0 && this.insideScrollbar(event.x(), event.y())) {
            this.scrolling = this.canScroll();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseReleased(@NonNull MouseButtonEvent event) {
        if (event.button() == 0) this.scrolling = false;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(@NonNull MouseButtonEvent event, double dx, double dy) {
        if (this.scrolling) {
            int trackTop = this.topPos + GRID_TOP;
            int trackBottom = trackTop + GRID_HEIGHT;
            this.scrollOffs = Mth.clamp(
                    ((float) event.y() - trackTop - 7.5F) / (trackBottom - trackTop - (float) SCROLLER_HEIGHT), 0.0F, 1.0F);
            this.menu.scrollTo(this.scrollOffs);
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) return true;
        if (!this.canScroll()) return false;
        this.scrollOffs = this.menu.subtractInputFromScroll(this.scrollOffs, scrollY);
        this.menu.scrollTo(this.scrollOffs);
        return true;
    }

    /** The grab area, which is wider than the six pixel track the texture draws: the tab uses fourteen. */
    private boolean insideScrollbar(double mouseX, double mouseY) {
        int left = this.leftPos + SCROLLER_LEFT;
        int top = this.topPos + GRID_TOP;
        return mouseX >= left && mouseY >= top
                && mouseX < left + SCROLLBAR_HIT_WIDTH && mouseY < top + GRID_HEIGHT;
    }

    private boolean canScroll() {
        return this.menu.canScroll();
    }

    /**
     * Remembers where the last click landed, because the throw-on-click-outside gesture needs to know whether
     * the press started past the panel. The tab tracks this the same way.
     */
    @Override
    protected boolean hasClickedOutside(double mouseX, double mouseY, int left, int top) {
        return this.clickedOutside = super.hasClickedOutside(mouseX, mouseY, left, top);
    }

    /**
     * The field gets the keyboard first, so typing a search term never reaches the screen's own shortcuts. The
     * only key that falls through is escape, which closes the screen.
     */
    @Override
    public boolean keyPressed(@NonNull KeyEvent event) {
        if (this.searchBox == null) return super.keyPressed(event);
        String before = this.searchBox.getValue();
        if (this.searchBox.keyPressed(event)) {
            if (!Objects.equals(before, this.searchBox.getValue())) this.refreshResults();
            return true;
        }
        return this.searchBox.isFocused() && this.searchBox.isVisible() && !event.isEscape() || super.keyPressed(event);
    }

    @Override
    public boolean charTyped(@NonNull CharacterEvent event) {
        if (this.searchBox == null) return super.charTyped(event);
        String before = this.searchBox.getValue();
        if (this.searchBox.charTyped(event)) {
            if (!Objects.equals(before, this.searchBox.getValue())) this.refreshResults();
            return true;
        }
        return super.charTyped(event);
    }

    /** Input method pre-edits belong to the field as well, or composing a search term would be dropped. */
    @Override
    public boolean preeditUpdated(@Nullable PreeditEvent event) {
        return this.searchBox != null && this.searchBox.preeditUpdated(event);
    }

    /**
     * The tab's item menu: five rows of nine slots whose contents are a window onto the filtered list, plus
     * the tab's nine hotbar slots over the player's real inventory.
     *
     * <p>Everything {@link AbstractContainerScreen} draws - the item, its count, the hover highlight, the
     * tooltip - is read straight out of these slots, and the carried stack is shared with the player's
     * inventory menu rather than kept here, because that menu is the one the server knows about.</p>
     */
    public static final class PickerMenu extends AbstractContainerMenu {
        private final NonNullList<ItemStack> items = NonNullList.create();
        private final AbstractContainerMenu inventoryMenu;
        private final SimpleContainer grid = new SimpleContainer(SLOT_COUNT);

        private PickerMenu(Player player) {
            super(null, 0);
            this.inventoryMenu = player.inventoryMenu;
            for (int row = 0; row < ROWS; row++)
                for (int column = 0; column < COLUMNS; column++)
                    this.addSlot(new GridSlot(this.grid, row * COLUMNS + column,
                            GRID_LEFT + column * SLOT_SIZE, GRID_TOP + row * SLOT_SIZE));
            this.addInventoryHotbarSlots(player.getInventory(), HOTBAR_LEFT, HOTBAR_TOP);
            this.scrollTo(0.0F);
        }

        @Override
        public boolean stillValid(@NonNull Player player) {
            return true;
        }

        @Override
        public @NonNull ItemStack getCarried() {
            return this.inventoryMenu.getCarried();
        }

        @Override
        public void setCarried(@NonNull ItemStack carried) {
            this.inventoryMenu.setCarried(carried);
        }

        /**
         * Only the hotbar reaches this, and emptying the slot is what the tab does with it: the grid is a view
         * over the result list, so shifting one of its slots has nothing to move.
         */
        @Override
        public @NonNull ItemStack quickMoveStack(@NonNull Player player, int slotIndex) {
            if (slotIndex >= this.slots.size() - HOTBAR_SLOTS && slotIndex < this.slots.size()) {
                Slot slot = this.slots.get(slotIndex);
                if (slot.hasItem()) slot.setByPlayer(ItemStack.EMPTY);
            }
            return ItemStack.EMPTY;
        }

        @Override
        public boolean canTakeItemForPickAll(@NonNull ItemStack carried, @NonNull Slot target) {
            return target.container != this.grid;
        }

        @Override
        public boolean canDragTo(@NonNull Slot slot) {
            return slot.container != this.grid;
        }

        /**
         * Copies one page of the list into the slots. This is the whole of the scrolling: the slots never
         * move, only what is in them.
         */
        private void scrollTo(float scrollOffs) {
            int rowToScrollTo = this.getRowIndexForScroll(scrollOffs);
            for (int row = 0; row < ROWS; row++) {
                for (int column = 0; column < COLUMNS; column++) {
                    int source = column + (row + rowToScrollTo) * COLUMNS;
                    this.grid.setItem(column + row * COLUMNS,
                            source >= 0 && source < this.items.size() ? this.items.get(source) : ItemStack.EMPTY);
                }
            }
        }

        private boolean canScroll() {
            return this.items.size() > SLOT_COUNT;
        }

        private int calculateRowCount() {
            return Mth.positiveCeilDiv(this.items.size(), COLUMNS) - ROWS;
        }

        private int getRowIndexForScroll(float scrollOffs) {
            return Math.max((int) (scrollOffs * this.calculateRowCount() + 0.5), 0);
        }

        private float getScrollForRowIndex(int rowIndex) {
            return Mth.clamp((float) rowIndex / Math.max(1, this.calculateRowCount()), 0.0F, 1.0F);
        }

        private float subtractInputFromScroll(float scrollOffs, double input) {
            return Mth.clamp(scrollOffs - (float) (input / Math.max(1, this.calculateRowCount())), 0.0F, 1.0F);
        }
    }

    /**
     * A grid slot, which refuses to give up what the client could not use anyway: an item that is behind a
     * disabled feature flag, or one the tab marks as not takeable.
     */
    private static final class GridSlot extends Slot {
        private GridSlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPickup(@NonNull Player player) {
            ItemStack item = this.getItem();
            if (item.isEmpty()) return true;
            return item.isItemEnabled(player.level().enabledFeatures()) && !item.has(DataComponents.CREATIVE_SLOT_LOCK);
        }
    }

    /** One row of the grid: the stack to draw, and the pre-lowered text a search term is matched against. */
    private record Candidate(ItemStack stack, String haystack) {
    }
}
