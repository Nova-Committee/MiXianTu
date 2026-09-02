package com.iafenvoy.mxt.screen.hotbar;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.render.overlay.hotbar.HotbarEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Generic nine-column hotbar editor. The screen only edits a local draft;
 * {@link HotbarAccess#write(List)} is called once when the screen closes.
 */
public final class HotbarConfigurationScreen extends Screen {
    private static final int SLOT_SIZE = 22;
    private static final int SLOT_GAP = 2;
    private static final int COLUMNS = 9;
    private static final int MAX_SLOTS = 9;
    private static final int GRID_STEP = SLOT_SIZE + SLOT_GAP;
    private static final int HOTBAR_HEIGHT = SLOT_SIZE + 16;
    private static final int KEY_LABEL_HEIGHT = 9;
    private static final int KEY_LABEL_GAP = 3;
    private static final int DIVIDER_GAP = 4;
    private static final int PANEL_MARGIN = 12;
    private static final Identifier BACKGROUND = Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "textures/gui/classic/hotbar_configuration.png");
    private static final Identifier SLOT = Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "textures/gui/classic/slot_22.png");
    private static final Identifier SELECTED_SLOT = Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "textures/gui/classic/slot_22_selected.png");

    private final List<Option> options;
    private final HotbarAccess access;
    private final List<@Nullable Identifier> slots = new ArrayList<>(MAX_SLOTS);
    private int panelLeft, panelTop, panelWidth, panelHeight;
    private int optionsLeft, optionsTop, optionsBottom;
    private int selectedOption = -1;
    private double scroll;

    public HotbarConfigurationScreen(Component title, List<? extends Option> options, HotbarAccess access) {
        super(title);
        this.options = List.copyOf(options);
        this.access = access;
        List<Identifier> saved = access.read();
        for (int i = 0; i < MAX_SLOTS; i++) this.slots.add(i < saved.size() ? saved.get(i) : null);
    }

    @Override
    protected void init() {
        this.layout();
    }

    private void layout() {
        this.panelWidth = Math.min(this.width - PANEL_MARGIN * 2, COLUMNS * GRID_STEP - SLOT_GAP + 18);
        this.panelWidth = Math.max(1, this.panelWidth);
        // Keep the editor compact; the option grid itself remains scrollable.
        this.panelHeight = Math.min(this.height - PANEL_MARGIN * 2, 260);
        this.panelHeight = Math.max(1, this.panelHeight);
        this.panelLeft = (this.width - this.panelWidth) / 2;
        this.panelTop = (this.height - this.panelHeight) / 2;
        this.optionsLeft = this.panelLeft + Math.max(6, (this.panelWidth - (COLUMNS * GRID_STEP - SLOT_GAP)) / 2);
        this.optionsTop = this.panelTop + 30;
        this.optionsBottom = this.panelTop + this.panelHeight - HOTBAR_HEIGHT - 12;
        this.clampScroll();
    }

    @Override
    protected void repositionElements() {
        this.layout();
    }

    @Override
    public void extractBackground(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        this.extractTransparentBackground(graphics);
        graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, this.panelLeft, this.panelTop, 0.0F, 0.0F,
                this.panelWidth, this.panelHeight, 232, 260);
        graphics.text(this.font, this.title, this.panelLeft + 10, this.panelTop + 10, 0xFF404040, false);
        int contentHeight = Math.max(1, this.optionsBottom - this.optionsTop);
        int rows = this.options.size() / COLUMNS + (this.options.size() % COLUMNS == 0 ? 0 : 1);
        int maxScroll = Math.max(0, rows * GRID_STEP - contentHeight);
        if (maxScroll > 0) {
            int barX = this.optionsLeft + COLUMNS * GRID_STEP + 2;
            int thumbHeight = Math.max(12, (int) ((double) contentHeight * contentHeight / (rows * GRID_STEP)));
            int thumbY = this.optionsTop + (int) ((contentHeight - thumbHeight) * (this.scroll / maxScroll));
            graphics.fill(barX, this.optionsTop, barX + 3, this.optionsBottom, 0xFF555555);
            graphics.fill(barX, thumbY, barX + 3, thumbY + thumbHeight, 0xFF8B8B8B);
        }
        int hotbarTop = this.hotbarTop();
        int keyLabelTop = hotbarTop - KEY_LABEL_GAP - KEY_LABEL_HEIGHT;
        int dividerY = keyLabelTop - DIVIDER_GAP;
        graphics.fill(this.optionsLeft - 4, dividerY, this.optionsLeft + COLUMNS * GRID_STEP - SLOT_GAP + 4,
                dividerY + 1, 0xFF555555);
        graphics.fill(this.optionsLeft - 4, dividerY + 1, this.optionsLeft + COLUMNS * GRID_STEP - SLOT_GAP + 4,
                dividerY + 2, 0xFFFFFFFF);
        for (int i = 0; i < MAX_SLOTS; i++) {
            int x = this.optionsLeft + i * GRID_STEP;
            graphics.blit(RenderPipelines.GUI_TEXTURED, SLOT, x, hotbarTop, 0.0F, 0.0F, SLOT_SIZE, SLOT_SIZE, SLOT_SIZE, SLOT_SIZE);
            String key = Integer.toString(i + 1);
            graphics.text(this.font, key, x + (SLOT_SIZE - this.font.width(key)) / 2, keyLabelTop, 0xFF404040, true);
        }
    }

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int stepRows = Math.max(0, this.options.size() / COLUMNS + (this.options.size() % COLUMNS == 0 ? 0 : 1));
        int firstRow = Math.max(0, (int) Math.floor(this.scroll / GRID_STEP));
        int lastRow = Math.min(stepRows, (int) Math.ceil((this.scroll + Math.max(0, this.optionsBottom - this.optionsTop)) / GRID_STEP) + 1);
        for (int row = firstRow; row < lastRow; row++) {
            for (int col = 0; col < COLUMNS; col++) {
                int index = row * COLUMNS + col;
                if (index >= this.options.size()) break;
                int x = this.optionsLeft + col * GRID_STEP;
                int y = this.optionsTop + row * GRID_STEP - (int) this.scroll;
                if (y < this.optionsTop || y + SLOT_SIZE > this.optionsBottom) continue;
                Option option = this.options.get(index);
                boolean hovered = mouseX >= x && mouseX < x + SLOT_SIZE && mouseY >= y && mouseY < y + SLOT_SIZE;
                graphics.blit(RenderPipelines.GUI_TEXTURED, index == this.selectedOption ? SELECTED_SLOT : SLOT,
                        x, y, 0.0F, 0.0F, SLOT_SIZE, SLOT_SIZE, SLOT_SIZE, SLOT_SIZE);
                if (hovered && index != this.selectedOption)
                    graphics.outline(x, y, SLOT_SIZE, SLOT_SIZE, 0xFFFFFFFF);
                renderIcon(graphics, option.entry(), x + 3, y + 3);
            }
        }
        int hotbarTop = this.hotbarTop();
        for (int i = 0; i < MAX_SLOTS; i++) {
            Identifier id = this.slots.get(i);
            if (id == null) continue;
            int slotIndex = i;
            this.options.stream().filter(option -> option.id().equals(id)).findFirst()
                    .ifPresent(option -> renderIcon(graphics, option.entry(), this.optionsLeft + slotIndex * GRID_STEP + 3, hotbarTop + 3));
        }
        this.renderTooltip(graphics, mouseX, mouseY);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private void renderTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int row = (int) Math.floor((mouseY - this.optionsTop + this.scroll) / GRID_STEP);
        int col = (mouseX - this.optionsLeft) / GRID_STEP;
        if (row < 0 || col < 0 || col >= COLUMNS) return;
        int index = row * COLUMNS + col;
        if (index >= 0 && index < this.options.size()) {
            Option option = this.options.get(index);
            if (mouseX >= this.optionsLeft + col * GRID_STEP && mouseX < this.optionsLeft + col * GRID_STEP + SLOT_SIZE
                    && mouseY >= this.optionsTop + row * GRID_STEP - this.scroll
                    && mouseY < this.optionsTop + row * GRID_STEP - this.scroll + SLOT_SIZE)
                option.tooltip().ifPresent(value -> graphics.setTooltipForNextFrame(this.font, value, mouseX, mouseY));
        }
    }

    private static void renderIcon(GuiGraphicsExtractor graphics, HotbarEntry entry, int x, int y) {
        entry.icon().ifPresentOrElse(icon -> icon.item().ifPresentOrElse(
                        item -> graphics.item(item.create(), x, y),
                        () -> icon.texture().ifPresent(texture -> graphics.blit(RenderPipelines.GUI_TEXTURED, texture,
                                x, y, 0.0F, 0.0F, 16, 16, 16, 16))),
                () -> {
                    String name = entry.name().getString();
                    if (name.length() > 3) name = name.substring(0, 3);
                    graphics.text(Minecraft.getInstance().font, name, x + (16 - Minecraft.getInstance().font.width(name)) / 2,
                            y + 5, 0xFFE0E5EF, true);
                });
    }

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
        int index = this.optionAt(event.x(), event.y());
        if (index >= 0) {
            this.selectedOption = this.selectedOption == index ? -1 : index;
            return true;
        }
        int hotbarTop = this.hotbarTop();
        if (event.y() >= hotbarTop && event.y() < hotbarTop + SLOT_SIZE) {
            int slot = (int) ((event.x() - this.optionsLeft) / GRID_STEP);
            int offset = (int) (event.x() - this.optionsLeft) - slot * GRID_STEP;
            if (slot >= 0 && slot < MAX_SLOTS && offset >= 0 && offset < SLOT_SIZE) {
                this.slots.set(slot, this.selectedOption < 0 ? null : this.options.get(this.selectedOption).id());
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    private int optionAt(double mouseX, double mouseY) {
        int col = (int) ((mouseX - this.optionsLeft) / GRID_STEP);
        int row = (int) ((mouseY - this.optionsTop + this.scroll) / GRID_STEP);
        if (col < 0 || col >= COLUMNS || row < 0) return -1;
        int xOffset = (int) mouseX - (this.optionsLeft + col * GRID_STEP);
        int yOffset = (int) mouseY - (this.optionsTop + row * GRID_STEP - (int) this.scroll);
        if (xOffset < 0 || xOffset >= SLOT_SIZE || yOffset < 0 || yOffset >= SLOT_SIZE) return -1;
        int index = row * COLUMNS + col;
        return index >= 0 && index < this.options.size() ? index : -1;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseY >= this.optionsTop && mouseY < this.optionsBottom) {
            this.scroll -= scrollY * GRID_STEP * 2.0D;
            this.clampScroll();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void clampScroll() {
        int rows = this.options.size() / COLUMNS + (this.options.size() % COLUMNS == 0 ? 0 : 1);
        double max = Math.max(0, rows * GRID_STEP - Math.max(0, this.optionsBottom - this.optionsTop));
        this.scroll = Math.max(0, Math.min(max, this.scroll));
    }

    private int hotbarTop() {
        return this.panelTop + this.panelHeight - HOTBAR_HEIGHT;
    }

    @Override
    public void onClose() {
        this.access.write(new ArrayList<>(this.slots));
        super.onClose();
    }

    public record Option(Identifier id, HotbarEntry entry, @NotNull Optional<Component> tooltip) {
        public Option {
            if (id == null || entry == null) throw new IllegalArgumentException("Hotbar option fields cannot be null");
        }

        public static Option of(HotbarEntry entry) {
            Identifier id = entry.id();
            if (id == null) throw new IllegalArgumentException("Configurable hotbar entries require an ID");
            return new Option(id, entry, Optional.of(entry.name()));
        }
    }

    @FunctionalInterface
    public interface HotbarAccess {
        List<Identifier> read();

        default void write(List<@Nullable Identifier> slots) {
        }
    }
}
