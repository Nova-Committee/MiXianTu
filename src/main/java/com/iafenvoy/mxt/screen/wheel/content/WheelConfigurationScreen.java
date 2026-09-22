package com.iafenvoy.mxt.screen.wheel.content;

import com.iafenvoy.mxt.api.WheelMenuEntry;
import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.registry.MxtKeyMappings;
import com.iafenvoy.mxt.render.IconRenderer;
import com.iafenvoy.mxt.runtime.wheel.WheelEntryKind;
import com.iafenvoy.mxt.runtime.wheel.WheelLayout;
import com.iafenvoy.mxt.runtime.wheel.WheelSlot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import java.util.List;

/**
 * The wheel editor: two scrollable pools - auras on the left, everything else on the right, sharing one row so
 * any kind fits any sector - over the twelve sectors of the first page. It edits a draft and sends the whole
 * layout on close, {@code Escape} included, so the two sides cannot disagree about a sector.
 */
public final class WheelConfigurationScreen extends Screen {
    private static final int SLOT_SIZE = 22;
    private static final int SLOT_GAP = 2;
    private static final int GRID_STEP = SLOT_SIZE + SLOT_GAP;
    private static final int POOL_COLUMNS = 6;
    private static final int POOL_WIDTH = POOL_COLUMNS * GRID_STEP - SLOT_GAP;
    private static final int POOL_GAP = 16;
    private static final int CONTENT_WIDTH = POOL_WIDTH * 2 + POOL_GAP;
    private static final int SLOT_ROW_WIDTH = WheelLayout.SLOTS * GRID_STEP - SLOT_GAP;
    private static final int PANEL_MARGIN = 12;
    private static final int HEADER_HEIGHT = 30;
    private static final int POOL_HEADING_HEIGHT = 12;
    // Divider, heading, hint, numbers and cells each get a line: the heading and hint would overlap.
    private static final int SLOT_ROW_HEIGHT = 67;
    private static final int SLOT_HEADING_OFFSET = 7;
    private static final int SLOT_HINT_OFFSET = 18;
    private static final int SLOT_NUMBER_OFFSET = 29;
    private static final int SLOT_TOP_OFFSET = 41;
    private static final int PANEL_HEIGHT = 268;
    private static final int SCROLL_BAR_WIDTH = 3;
    private static final int TITLE_COLOR = 0xFF404040;
    private static final int HINT_COLOR = 0xFF6A6A6A;
    private static final int DIVIDER_DARK = 0xFF555555;
    private static final int DIVIDER_LIGHT = 0xFFFFFFFF;
    private static final int SCROLL_TRACK = 0xFF555555;
    private static final int SCROLL_THUMB = 0xFF8B8B8B;
    private static final Identifier BACKGROUND = Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "textures/gui/classic/wheel_configuration.png");
    private static final Identifier SLOT = Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "textures/gui/classic/slot_22.png");
    private static final Identifier SELECTED_SLOT = Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "textures/gui/classic/slot_22_selected.png");
    // The panel texture is drawn as nine slices so its 3px border and title band keep their authored size.
    private static final int BACKGROUND_WIDTH = 232;
    private static final int BACKGROUND_HEIGHT = 260;
    private static final int BACKGROUND_BORDER = 3;
    private static final int BACKGROUND_HEADER = 26;
    private static final int AURA_POOL = 0;
    private static final int ABILITY_POOL = 1;

    private final List<WheelMenuEntry> auras;
    // The right-hand pool: the skills the player holds and the artifact capabilities they carry, in one grid
    // because a sector holds either.
    private final List<WheelMenuEntry> options;
    private final double[] scroll = new double[2];
    private final int[] poolLeft = new int[2];
    private WheelLayout draft;
    private int selectedPool = -1;
    private int selectedOption = -1;
    private int panelLeft;
    private int panelTop;
    private int panelWidth;
    private int panelHeight;
    private int poolsTop;
    private int poolsBottom;
    private int slotRowLeft;
    private int slotRowTop;

    private WheelConfigurationScreen(Player player) {
        super(Component.translatable("screen.mxt.wheel_configuration"));
        this.auras = WheelContent.auras(player);
        this.options = WheelContent.pool(player);
        this.draft = WheelContent.layoutFor(player);
    }

    public static boolean open() {
        Player player = Minecraft.getInstance().player;
        if (player == null) return false;
        Minecraft.getInstance().setScreen(new WheelConfigurationScreen(player));
        return true;
    }

    @Override
    protected void init() {
        this.layout();
    }

    @Override
    protected void repositionElements() {
        this.layout();
    }

    private void layout() {
        this.panelWidth = Math.max(1, Math.min(this.width - PANEL_MARGIN * 2, CONTENT_WIDTH + 18));
        this.panelHeight = Math.max(1, Math.min(this.height - PANEL_MARGIN * 2, PANEL_HEIGHT));
        this.panelLeft = (this.width - this.panelWidth) / 2;
        this.panelTop = (this.height - this.panelHeight) / 2;
        // Pools are centred as one block, so a narrow panel squeezes both rather than clipping the right one.
        int contentLeft = this.panelLeft + Math.max(6, (this.panelWidth - CONTENT_WIDTH) / 2);
        this.poolLeft[AURA_POOL] = contentLeft;
        this.poolLeft[ABILITY_POOL] = contentLeft + POOL_WIDTH + POOL_GAP;
        this.poolsTop = this.panelTop + HEADER_HEIGHT + POOL_HEADING_HEIGHT;
        this.poolsBottom = this.panelTop + this.panelHeight - SLOT_ROW_HEIGHT;
        this.slotRowLeft = this.panelLeft + Math.max(6, (this.panelWidth - SLOT_ROW_WIDTH) / 2);
        this.slotRowTop = this.poolsBottom + SLOT_TOP_OFFSET;
        this.clampScroll();
    }

    @Override
    public void extractBackground(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        this.extractTransparentBackground(graphics);
        this.extractPanel(graphics);
        graphics.text(this.font, this.title, this.panelLeft + 10, this.panelTop + 10, TITLE_COLOR, false);
        Component save = Component.translatable("wheel.mxt.config.save");
        graphics.text(this.font, save, this.panelLeft + this.panelWidth - 10 - this.font.width(save),
                this.panelTop + 10, HINT_COLOR, false);
        // The header line that says why the pages behind the first are not editable here.
        Component derived = Component.translatable("wheel.mxt.config.derived",
                MxtKeyMappings.WHEEL_PREVIOUS.get().getTranslatedKeyMessage(),
                MxtKeyMappings.WHEEL_NEXT.get().getTranslatedKeyMessage());
        graphics.text(this.font, derived, this.panelLeft + this.panelWidth - 10 - this.font.width(derived),
                this.panelTop + 20, HINT_COLOR, false);
        graphics.text(this.font, Component.translatable("wheel.mxt.pool.aura"),
                this.poolLeft[AURA_POOL], this.panelTop + HEADER_HEIGHT, TITLE_COLOR, false);
        graphics.text(this.font, Component.translatable("wheel.mxt.pool.ability"),
                this.poolLeft[ABILITY_POOL], this.panelTop + HEADER_HEIGHT, TITLE_COLOR, false);
        for (int pool = 0; pool < 2; pool++) this.extractScrollBar(graphics, pool);

        int dividerY = this.poolsBottom + 1;
        graphics.fill(this.panelLeft + 6, dividerY, this.panelLeft + this.panelWidth - 6, dividerY + 1, DIVIDER_DARK);
        graphics.fill(this.panelLeft + 6, dividerY + 1, this.panelLeft + this.panelWidth - 6, dividerY + 2, DIVIDER_LIGHT);
        graphics.text(this.font, Component.translatable("wheel.mxt.config.sectors"),
                this.slotRowLeft, this.poolsBottom + SLOT_HEADING_OFFSET, TITLE_COLOR, false);
        graphics.text(this.font, Component.translatable("wheel.mxt.config.hint"),
                this.slotRowLeft, this.poolsBottom + SLOT_HINT_OFFSET, HINT_COLOR, false);
        for (int sector = 0; sector < WheelLayout.SLOTS; sector++) {
            int x = this.slotRowLeft + sector * GRID_STEP;
            graphics.blit(RenderPipelines.GUI_TEXTURED, SLOT, x, this.slotRowTop, 0.0F, 0.0F,
                    SLOT_SIZE, SLOT_SIZE, SLOT_SIZE, SLOT_SIZE);
            String number = Integer.toString(sector + 1);
            graphics.text(this.font, number, x + (SLOT_SIZE - this.font.width(number)) / 2,
                    this.poolsBottom + SLOT_NUMBER_OFFSET, HINT_COLOR, false);
        }
    }

    // Nine slices, so the border and title band keep their authored size; one scaled blit would also let the
    // sampler repeat the texture and wrap a second border into the middle.
    private void extractPanel(GuiGraphicsExtractor graphics) {
        int right = this.panelLeft + this.panelWidth;
        int bottom = this.panelTop + this.panelHeight;
        int[] xs = {this.panelLeft, this.panelLeft + BACKGROUND_BORDER,
                Math.max(this.panelLeft + BACKGROUND_BORDER, right - BACKGROUND_BORDER), right};
        int[] ys = {this.panelTop, this.panelTop + BACKGROUND_HEADER,
                Math.max(this.panelTop + BACKGROUND_HEADER, bottom - BACKGROUND_BORDER), bottom};
        float[] us = {0.0F, (float) BACKGROUND_BORDER / BACKGROUND_WIDTH,
                (float) (BACKGROUND_WIDTH - BACKGROUND_BORDER) / BACKGROUND_WIDTH, 1.0F};
        float[] vs = {0.0F, (float) BACKGROUND_HEADER / BACKGROUND_HEIGHT,
                (float) (BACKGROUND_HEIGHT - BACKGROUND_BORDER) / BACKGROUND_HEIGHT, 1.0F};
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                if (xs[col] >= xs[col + 1] || ys[row] >= ys[row + 1]) continue;
                graphics.blit(BACKGROUND, xs[col], ys[row], xs[col + 1], ys[row + 1],
                        us[col], us[col + 1], vs[row], vs[row + 1]);
            }
        }
    }

    private void extractScrollBar(GuiGraphicsExtractor graphics, int pool) {
        int contentHeight = Math.max(1, this.poolsBottom - this.poolsTop);
        int rows = rows(this.pool(pool).size());
        int maxScroll = Math.max(0, rows * GRID_STEP - contentHeight);
        if (maxScroll == 0) return;
        int barX = this.poolLeft[pool] + POOL_WIDTH + 2;
        int thumbHeight = Math.max(12, (int) ((double) contentHeight * contentHeight / (rows * GRID_STEP)));
        int thumbY = this.poolsTop + (int) ((contentHeight - thumbHeight) * (this.scroll[pool] / maxScroll));
        graphics.fill(barX, this.poolsTop, barX + SCROLL_BAR_WIDTH, this.poolsBottom, SCROLL_TRACK);
        graphics.fill(barX, thumbY, barX + SCROLL_BAR_WIDTH, thumbY + thumbHeight, SCROLL_THUMB);
    }

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        Player player = Minecraft.getInstance().player;
        for (int pool = 0; pool < 2; pool++) {
            List<WheelMenuEntry> options = this.pool(pool);
            if (options.isEmpty()) {
                Component empty = Component.translatable("wheel.mxt.pool.empty");
                graphics.text(this.font, empty, this.poolLeft[pool], this.poolsTop, HINT_COLOR, false);
                continue;
            }
            int firstRow = Math.max(0, (int) Math.floor(this.scroll[pool] / GRID_STEP));
            int lastRow = Math.min(rows(options.size()),
                    (int) Math.ceil((this.scroll[pool] + Math.max(0, this.poolsBottom - this.poolsTop)) / GRID_STEP) + 1);
            // The last row is drawn to its end, so a pool reads as one grid instead of scattered frames.
            for (int index = firstRow * POOL_COLUMNS; index < lastRow * POOL_COLUMNS; index++) {
                int x = this.poolLeft[pool] + (index % POOL_COLUMNS) * GRID_STEP;
                int y = this.poolsTop + (index / POOL_COLUMNS) * GRID_STEP - (int) this.scroll[pool];
                if (y < this.poolsTop || y + SLOT_SIZE > this.poolsBottom) continue;
                WheelMenuEntry option = index < options.size() ? options.get(index) : null;
                boolean selected = option != null && pool == this.selectedPool && index == this.selectedOption;
                this.extractSlot(graphics, x, y, option, selected,
                        option != null && inside(mouseX, mouseY, x, y));
            }
        }
        for (int sector = 0; sector < WheelLayout.SLOTS; sector++) {
            int x = this.slotRowLeft + sector * GRID_STEP;
            WheelSlot slot = this.draft.slot(sector);
            WheelMenuEntry entry = this.entryOf(slot);
            this.extractSlot(graphics, x, this.slotRowTop, entry, false,
                    inside(mouseX, mouseY, x, this.slotRowTop));
            // A sector whose id no longer resolves keeps its place but is marked, so it can still be cleared.
            if (entry == null && !slot.isEmpty()) {
                graphics.text(this.font, "?", x + (SLOT_SIZE - this.font.width("?")) / 2,
                        this.slotRowTop + (SLOT_SIZE - this.font.lineHeight) / 2, 0xFFB03030, false);
            }
        }
        this.extractTooltip(graphics, mouseX, mouseY, player);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private void extractSlot(GuiGraphicsExtractor graphics, int x, int y,
                             @Nullable WheelMenuEntry entry, boolean selected, boolean hovered) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, selected ? SELECTED_SLOT : SLOT, x, y, 0.0F, 0.0F,
                SLOT_SIZE, SLOT_SIZE, SLOT_SIZE, SLOT_SIZE);
        if (hovered && !selected) graphics.outline(x, y, SLOT_SIZE, SLOT_SIZE, 0xFFFFFFFF);
        if (entry == null) return;
        IconRenderer.renderOrName(graphics, this.font, entry.icon(), entry.title(), x, y, SLOT_SIZE);
        graphics.fill(x + 1, y + SLOT_SIZE - 3, x + SLOT_SIZE - 1, y + SLOT_SIZE - 1, entry.accentColor());
    }

    private void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY, @Nullable Player player) {
        WheelMenuEntry entry = null;
        boolean stale;
        int[] option = this.optionAt(mouseX, mouseY);
        if (option != null) {
            entry = this.pool(option[0]).get(option[1]);
        } else {
            int sector = this.slotAt(mouseX, mouseY);
            if (sector >= 0) {
                WheelSlot slot = this.draft.slot(sector);
                entry = this.entryOf(slot);
                stale = entry == null && !slot.isEmpty();
                if (stale) {
                    graphics.setComponentTooltipForNextFrame(this.font,
                            List.of(Component.translatable("wheel.mxt.tooltip.stale", slot.id().toString())), mouseX, mouseY);
                    return;
                }
            }
        }
        if (entry == null) return;
        List<Component> lines = entry.tooltip(player);
        if (!lines.isEmpty()) graphics.setComponentTooltipForNextFrame(this.font, lines, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
        int[] option = this.optionAt(event.x(), event.y());
        if (option != null) {
            boolean same = this.selectedPool == option[0] && this.selectedOption == option[1];
            this.selectedPool = same ? -1 : option[0];
            this.selectedOption = same ? -1 : option[1];
            return true;
        }
        int sector = this.slotAt(event.x(), event.y());
        if (sector >= 0) {
            WheelMenuEntry selected = this.selectedEntry();
            this.draft = this.draft.with(sector, selected == null
                    ? WheelSlot.EMPTY : WheelSlot.of(selected.kind(), selected.id()));
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseY < this.poolsTop || mouseY >= this.poolsBottom) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        int pool = mouseX >= this.poolLeft[ABILITY_POOL] ? ABILITY_POOL : AURA_POOL;
        if (pool == AURA_POOL && mouseX >= this.poolLeft[AURA_POOL] + POOL_WIDTH)
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        this.scroll[pool] -= scrollY * GRID_STEP * 2.0D;
        this.clampScroll();
        return true;
    }

    @Override
    public void onClose() {
        WheelContent.save(this.draft);
        super.onClose();
    }

    private List<WheelMenuEntry> pool(int pool) {
        return pool == ABILITY_POOL ? this.options : this.auras;
    }

    private @Nullable WheelMenuEntry selectedEntry() {
        if (this.selectedPool < 0 || this.selectedOption < 0) return null;
        List<WheelMenuEntry> options = this.pool(this.selectedPool);
        return this.selectedOption < options.size() ? options.get(this.selectedOption) : null;
    }

    // Resolved the same way the wheel does, so a cell shows what that sector will actually draw.
    private @Nullable WheelMenuEntry entryOf(WheelSlot slot) {
        if (slot.isEmpty()) return null;
        for (WheelMenuEntry entry : this.pool(slot.kind() == WheelEntryKind.AURA ? AURA_POOL : ABILITY_POOL))
            if (entry.id().equals(slot.id())) return entry;
        return null;
    }

    private int @Nullable [] optionAt(double mouseX, double mouseY) {
        if (mouseY < this.poolsTop || mouseY >= this.poolsBottom) return null;
        for (int pool = 0; pool < 2; pool++) {
            int col = (int) ((mouseX - this.poolLeft[pool]) / GRID_STEP);
            int row = (int) ((mouseY - this.poolsTop + this.scroll[pool]) / GRID_STEP);
            if (col < 0 || col >= POOL_COLUMNS || row < 0) continue;
            int x = this.poolLeft[pool] + col * GRID_STEP;
            int y = this.poolsTop + row * GRID_STEP - (int) this.scroll[pool];
            if (y < this.poolsTop || y + SLOT_SIZE > this.poolsBottom) continue;
            if (!inside(mouseX, mouseY, x, y)) continue;
            int index = row * POOL_COLUMNS + col;
            if (index < this.pool(pool).size()) return new int[]{pool, index};
        }
        return null;
    }

    private int slotAt(double mouseX, double mouseY) {
        int sector = (int) ((mouseX - this.slotRowLeft) / GRID_STEP);
        if (sector < 0 || sector >= WheelLayout.SLOTS) return -1;
        return inside(mouseX, mouseY, this.slotRowLeft + sector * GRID_STEP, this.slotRowTop) ? sector : -1;
    }

    private void clampScroll() {
        int contentHeight = Math.max(0, this.poolsBottom - this.poolsTop);
        for (int pool = 0; pool < 2; pool++) {
            double max = Math.max(0, rows(this.pool(pool).size()) * GRID_STEP - contentHeight);
            this.scroll[pool] = Math.max(0, Math.min(max, this.scroll[pool]));
        }
    }

    private static int rows(int size) {
        return size / POOL_COLUMNS + (size % POOL_COLUMNS == 0 ? 0 : 1);
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y) {
        return mouseX >= x && mouseX < x + SLOT_SIZE && mouseY >= y && mouseY < y + SLOT_SIZE;
    }
}
