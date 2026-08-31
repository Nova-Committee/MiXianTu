package com.iafenvoy.mxt.screen.information;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.screen.information.InformationManager.Side;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.ObjectSelectionList.Entry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.loading.FMLEnvironment;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Expandable player information screen with a scrollable, selectable list.
 */
public final class InformationPanelScreen extends Screen {
    private static final int PANEL_WIDTH = 510;
    private static final int PANEL_HEIGHT = 315;
    private static final int PLAYER_RENDER_WIDTH = 120;
    private static final int HEADER_HEIGHT = 18;
    private static final int EQUIPMENT_SLOT_SIZE = 24;
    private static final int EQUIPMENT_SLOT_COUNT = 4;
    private static final int PLAYER_TO_BASIC_GAP = 14;
    private static final int PLAYER_RENDER_SCALE = 30;
    private static final EquipmentSlot[] EQUIPMENT_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };
    private InformationList list;
    private InformationList basicList;
    private int panelLeft;
    private int panelTop;
    private int panelWidth;
    private int panelHeight;
    private int playerWidth;
    private int playerPreviewHeight;
    private int equipmentLeft;
    private int playerRenderLeft;
    private int playerRenderRight;

    public InformationPanelScreen() {
        super(Component.translatable("screen.mxt.information_panel"));
    }

    @Override
    protected void init() {
        this.layoutWidgets();
        if (!this.children().contains(this.list)) this.addRenderableOnly(this.list);
        if (!this.children().contains(this.basicList)) this.addRenderableOnly(this.basicList);
    }

    /**
     * Recalculate all GUI-space bounds after a window/GUI-scale change.
     */
    private void layoutWidgets() {
        this.panelWidth = Math.max(1, Math.min(PANEL_WIDTH, this.width - 12));
        this.panelHeight = Math.max(1, Math.min(PANEL_HEIGHT, this.height - 12));
        this.panelLeft = (this.width - this.panelWidth) / 2;
        this.panelTop = (this.height - this.panelHeight) / 2;
        int playerRenderWidth = Math.min(PLAYER_RENDER_WIDTH, Math.max(60, (this.panelWidth - 250) / 2));
        this.playerWidth = EQUIPMENT_SLOT_SIZE + 8 + playerRenderWidth;
        this.equipmentLeft = this.panelLeft + 20;
        this.playerRenderLeft = this.equipmentLeft + EQUIPMENT_SLOT_SIZE + 8;
        this.playerRenderRight = this.playerRenderLeft + playerRenderWidth;
        int contentTop = this.panelTop + HEADER_HEIGHT + 2;
        int contentBottom = this.panelTop + this.panelHeight - 12;
        int contentHeight = Math.max(1, contentBottom - contentTop);
        this.playerPreviewHeight = EQUIPMENT_SLOT_SIZE * EQUIPMENT_SLOT_COUNT;
        int rightX = this.playerRenderRight + 12;
        int rightWidth = Math.max(1, this.panelLeft + this.panelWidth - rightX - 20);
        int rightListTop = contentTop + 16;
        int rightListHeight = Math.max(1, contentHeight - 16);
        if (this.list == null) {
            this.list = new InformationList(this.minecraft, rightX, rightListTop, rightWidth, rightListHeight);
            this.list.replaceEntries(this.buildEntries(Side.CULTIVATION));
        } else {
            this.list.updateSizeAndPosition(rightWidth, rightListHeight, rightX, rightListTop);
        }
        int basicTop = contentTop + this.playerPreviewHeight + PLAYER_TO_BASIC_GAP;
        int basicListTop = basicTop + 16;
        int basicHeight = Math.max(1, contentBottom - basicListTop);
        int basicWidth = Math.max(1, this.playerWidth);
        if (this.basicList == null) {
            this.basicList = new InformationList(this.minecraft, this.panelLeft + 20, basicListTop, basicWidth, basicHeight);
            this.basicList.replaceEntries(this.buildEntries(Side.BASIC));
        } else {
            this.basicList.updateSizeAndPosition(basicWidth, basicHeight, this.panelLeft + 20, basicListTop);
        }
    }

    @Override
    protected void repositionElements() {
        this.layoutWidgets();
    }

    private List<InformationList.LineEntry> buildEntries(Side side) {
        List<InformationEntry> information = this.minecraft.player == null
                ? List.of() : InformationManager.entries(this.minecraft.player, side);
        int nameWidth = information.stream()
                .mapToInt(entry -> this.font.width(entry.name()))
                .max().orElse(0);
        List<InformationList.LineEntry> entries = new ArrayList<>(information.size());
        for (InformationEntry entry : information)
            entries.add(new InformationList.LineEntry(entry, nameWidth));
        return entries;
    }

    @Override
    public void extractBackground(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        this.extractTransparentBackground(graphics);
        graphics.fill(this.panelLeft, this.panelTop, this.panelLeft + this.panelWidth, this.panelTop + this.panelHeight, 0xF0101018);
        graphics.outline(this.panelLeft, this.panelTop, this.panelWidth, this.panelHeight, 0xFF6F7C92);
        graphics.text(this.font, this.title, this.panelLeft + 18, this.panelTop + 10, 0xFFFFFFFF, true);
        int contentTop = this.panelTop + HEADER_HEIGHT + 2;
        graphics.text(this.font, Component.translatable("info.mxt.cultivation"), this.playerRenderRight + 12, contentTop, 0xFF8EC7FF, true);
        int basicTop = contentTop + this.playerPreviewHeight + PLAYER_TO_BASIC_GAP;
        graphics.text(this.font, Component.translatable("info.mxt.basic"), this.panelLeft + 20, basicTop, 0xFF8EC7FF, true);
        if (this.minecraft.player != null) {
            int x1 = this.playerRenderLeft;
            int x2 = this.playerRenderRight;
            int y1 = contentTop;
            int y2 = y1 + this.playerPreviewHeight;
            graphics.fill(x1, y1, x2, y2, 0x402A3344);
            graphics.outline(x1, y1, x2 - x1, y2 - y1, 0xFF4C566A);
            for (int index = 0; index < EQUIPMENT_SLOT_COUNT; index++) {
                int slotTop = contentTop + index * EQUIPMENT_SLOT_SIZE;
                graphics.fill(this.equipmentLeft, slotTop, this.equipmentLeft + EQUIPMENT_SLOT_SIZE,
                        slotTop + EQUIPMENT_SLOT_SIZE, 0x402A3344);
                graphics.outline(this.equipmentLeft, slotTop, EQUIPMENT_SLOT_SIZE, EQUIPMENT_SLOT_SIZE, 0xFF4C566A);
            }
        }
    }

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if (this.minecraft.player != null) {
            int x1 = this.playerRenderLeft;
            int x2 = this.playerRenderRight;
            int y1 = this.panelTop + HEADER_HEIGHT + 2;
            int y2 = y1 + this.playerPreviewHeight;
            for (int index = 0; index < EQUIPMENT_SLOT_COUNT; index++) {
                ItemStack stack = this.minecraft.player.getItemBySlot(EQUIPMENT_SLOTS[index]);
                if (!stack.isEmpty()) {
                    int slotX = this.equipmentLeft + (EQUIPMENT_SLOT_SIZE - 16) / 2;
                    int slotY = y1 + index * EQUIPMENT_SLOT_SIZE + (EQUIPMENT_SLOT_SIZE - 16) / 2;
                    graphics.item(stack, slotX, slotY);
                    graphics.itemDecorations(this.font, stack, slotX, slotY);
                    if (mouseX >= this.equipmentLeft && mouseX < this.equipmentLeft + EQUIPMENT_SLOT_SIZE
                            && mouseY >= y1 + index * EQUIPMENT_SLOT_SIZE
                            && mouseY < y1 + (index + 1) * EQUIPMENT_SLOT_SIZE)
                        graphics.setTooltipForNextFrame(this.font, stack, mouseX, mouseY);
                }
            }
            InventoryScreen.extractEntityInInventoryFollowsMouse(graphics, x1, y1, x2, y2, PLAYER_RENDER_SCALE, 0, mouseX, mouseY, this.minecraft.player);
        }
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        InformationList target = this.list != null && this.list.isMouseOver(mouseX, mouseY) ? this.list
                : this.basicList != null && this.basicList.isMouseOver(mouseX, mouseY) ? this.basicList : null;
        if (target != null) {
            target.setScrollAmount(target.scrollAmount() - scrollY * 18.0D);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static final class InformationList extends ObjectSelectionList<InformationList.LineEntry> {
        private InformationList(Minecraft minecraft, int x, int y, int width, int height) {
            super(minecraft, width, height, y, 18);
            this.setX(x);
        }

        @Override
        public int getRowWidth() {
            return Math.max(1, this.getWidth() - 8);
        }

        private static final class LineEntry extends Entry<LineEntry> {
            private final InformationEntry entry;
            private final int nameWidth;
            private boolean overflowReported;

            private LineEntry(InformationEntry entry, int nameWidth) {
                this.entry = entry;
                this.nameWidth = nameWidth;
            }

            @Override
            public void extractContent(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float partialTick) {
                int color = 0xFFE0E4EC;
                if (hovered || this.isFocused())
                    graphics.fill(this.getX(), this.getY(), this.getX() + this.getWidth(), this.getY() + this.getHeight(), 0x503F6A91);
                Font font = Minecraft.getInstance().font;
                int availableWidth = Math.max(1, this.getWidth() - 16);
                int valueX = this.getX() + 8 + this.nameWidth + 8;
                int nameAvailableWidth = Math.max(1, Math.min(this.nameWidth, availableWidth));
                int valueAvailableWidth = Math.max(1, this.getX() + this.getWidth() - valueX - 8);
                int nameLineCount = font.split(this.entry.name(), nameAvailableWidth).size();
                int valueLineCount = font.split(this.entry.value(), valueAvailableWidth).size();
                if ((nameLineCount > 1 || valueLineCount > 1) && !this.overflowReported) {
                    this.overflowReported = true;
                    if (!FMLEnvironment.isProduction())
                        MiXianTu.LOGGER.error("Information entry contains more than one line (width={}): {} = {}",
                                availableWidth, this.entry.name().getString(), this.entry.value().getString());
                }
                String fullName = this.entry.name().getString();
                String fullValue = this.entry.value().getString();
                String renderedName = abbreviate(font, fullName, nameAvailableWidth);
                String renderedValue = abbreviate(font, fullValue, valueAvailableWidth);
                graphics.text(font, renderedName, this.getX() + 8, this.getY() + 4, color, false);
                graphics.text(font, renderedValue, valueX, this.getY() + 4, color, false);
                if (hovered && (!renderedName.equals(fullName) || !renderedValue.equals(fullValue)))
                    graphics.setTooltipForNextFrame(font, this.entry.name().copy().append(": ").append(this.entry.value()), mouseX, mouseY);
            }

            private static String abbreviate(Font font, String text, int width) {
                if (font.width(text) <= width) return text;
                String suffix = "...";
                return font.plainSubstrByWidth(text, Math.max(0, width - font.width(suffix))) + suffix;
            }

            @Override
            public @NonNull Component getNarration() {
                return this.entry.name().copy().append(": ").append(this.entry.value());
            }
        }
    }
}
