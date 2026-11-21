package com.iafenvoy.mxt.compat.jei;

import com.iafenvoy.saturn.render.IconRenderer;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.inputs.IJeiInputHandler;
import mezz.jei.api.gui.widgets.IRecipeWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenPosition;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import org.jspecify.annotations.NonNull;

import java.util.List;

final class SpiritJeiAuraWidget implements IRecipeWidget, IJeiInputHandler {
    private static final int WIDTH = 124, ROW_HEIGHT = 18, VISIBLE_ROWS = 3;
    private final List<SpiritJeiText.AuraEntry> entries;
    private int firstRow;

    SpiritJeiAuraWidget(List<SpiritJeiText.AuraEntry> entries) {
        this.entries = List.copyOf(entries);
    }

    @Override
    public @NonNull ScreenPosition getPosition() {
        return new ScreenPosition(8, 62);
    }

    @Override
    public @NonNull ScreenRectangle getArea() {
        return new ScreenRectangle(8, 62, WIDTH, ROW_HEIGHT * VISIBLE_ROWS);
    }

    @Override
    public ScreenRectangle getScreenRectangle() {
        return this.getArea();
    }

    @Override
    public void drawWidget(@NonNull GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
        Font font = Minecraft.getInstance().font;
        int width = this.entries.size() > VISIBLE_ROWS ? WIDTH - 6 : WIDTH;
        for (int row = 0; row < VISIBLE_ROWS && this.firstRow + row < this.entries.size(); row++) {
            SpiritJeiText.AuraEntry entry = this.entries.get(this.firstRow + row);
            int y = row * ROW_HEIGHT;
            String amount = SpiritJeiText.trim(font, "× " + entry.amount(), width - 20);
            if (entry.display().icon().isPresent()) {
                IconRenderer.renderAt(graphics, entry.display().icon().orElseThrow(), 0, y);
            } else {
                String name = SpiritJeiText.trim(font, entry.display().name().getString(),
                        Math.max(0, width - font.width(amount) - 4));
                int color = SpiritJeiText.readableTextColor(entry.display().color(), SpiritJeiText.PANEL_BACKGROUND);
                graphics.text(font, name, 0, y + 4, 0xFF000000 | color, false);
            }
            graphics.text(font, amount, width - font.width(amount), y + 4, 0xFF333333, false);
        }
        if (this.entries.size() > VISIBLE_ROWS) {
            int height = ROW_HEIGHT * VISIBLE_ROWS;
            int thumb = Math.max(8, height * VISIBLE_ROWS / this.entries.size());
            int top = this.firstRow * (height - thumb) / (this.entries.size() - VISIBLE_ROWS);
            graphics.fill(WIDTH - 3, 0, WIDTH - 1, height, 0xFF999999);
            graphics.fill(WIDTH - 3, top, WIDTH - 1, top + thumb, 0xFF444444);
        }
    }

    @Override
    public void getTooltip(@NonNull ITooltipBuilder tooltip, double mouseX, double mouseY) {
        if (mouseX < 0 || mouseX >= WIDTH - (this.entries.size() > VISIBLE_ROWS ? 6 : 0)
                || mouseY < 0 || mouseY >= ROW_HEIGHT * VISIBLE_ROWS) return;
        int row = this.firstRow + (int) (mouseY / ROW_HEIGHT);
        if (row < this.entries.size()) tooltip.addAll(this.entries.get(row).tooltip());
    }

    @Override
    public boolean handleMouseScrolled(double mouseX, double mouseY, double scrollDeltaX, double scrollDeltaY) {
        if (!Double.isFinite(scrollDeltaY) || scrollDeltaY == 0 || this.entries.size() <= VISIBLE_ROWS) return false;
        this.firstRow = Math.clamp(this.firstRow - (scrollDeltaY > 0 ? 1 : -1), 0, this.entries.size() - VISIBLE_ROWS);
        return true;
    }
}
