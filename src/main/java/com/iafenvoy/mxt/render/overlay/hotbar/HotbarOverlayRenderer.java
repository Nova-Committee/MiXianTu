package com.iafenvoy.mxt.render.overlay.hotbar;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Font;

import java.util.List;
import java.util.function.IntPredicate;

/**
 * Shared compact slot layout used by the ability and spirit-burst hotbars.
 */
public final class HotbarOverlayRenderer {
    public static final int SLOT_SIZE = 22;
    public static final int SLOT_GAP = 2;

    private HotbarOverlayRenderer() {
    }

    public static int width(int count) {
        return count <= 0 ? 0 : count * SLOT_SIZE + (count - 1) * SLOT_GAP;
    }

    public static void drawSlots(GuiGraphicsExtractor graphics, Font font, List<? extends HotbarEntry> slots, int x, int y, int selected) {
        drawSlots(graphics, font, slots, x, y, index -> index == selected);
    }

    public static void drawSlots(GuiGraphicsExtractor graphics, Font font, List<? extends HotbarEntry> slots,
                                 int x, int y, IntPredicate selected) {
        for (int index = 0; index < slots.size(); index++) {
            HotbarEntry slot = slots.get(index);
            int slotX = x + index * (SLOT_SIZE + SLOT_GAP);
            int background = selected.test(index) ? 0xEE26364A : 0xCC10131D;
            graphics.fill(slotX, y, slotX + SLOT_SIZE, y + SLOT_SIZE, background);
            graphics.fill(slotX, y, slotX + SLOT_SIZE, y + 1, slot.accentColor());
            graphics.fill(slotX, y + SLOT_SIZE - 1, slotX + SLOT_SIZE, y + SLOT_SIZE, 0xFF303747);
            graphics.text(font, Integer.toString(index + 1), slotX + 2, y + 2, 0xFFFFFFFF, false);
            slot.icon().ifPresentOrElse(icon -> graphics.item(icon, slotX + 3, y + 4), () -> {
                String name = slot.name().getString();
                if (name.length() > 3) name = name.substring(0, 3);
                graphics.text(font, name, slotX + (SLOT_SIZE - font.width(name)) / 2, y + 10, 0xFFE0E5EF, false);
            });
        }
    }
}
