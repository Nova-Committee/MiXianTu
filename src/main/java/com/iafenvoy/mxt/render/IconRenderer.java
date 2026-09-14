package com.iafenvoy.mxt.render;

import com.iafenvoy.mxt.data.IconReference;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;

import java.util.Optional;

/**
 * The client-side half of {@link IconReference}: the only place that turns an icon into pixels.
 *
 * <p>A screen passes the graphics and the box it has room for, and this decides whether to draw an item
 * or a texture and how to centre it. Both branches used to be written out at every call site, which is
 * how three screens ended up with three slightly different copies of it.</p>
 */
public final class IconRenderer {
    /**
     * The size an icon is drawn at. Vanilla items render at 16x16 and a GUI texture is authored at 16x16,
     * so a roomier slot centres the icon instead of stretching it.
     */
    public static final int ICON_SIZE = 16;
    /**
     * How far a stand-in made of the name sits below the icon's own position. The hotbar's own nudge, so
     * its entries keep the look they had before this renderer existed.
     */
    private static final int NAME_OFFSET = 7;
    private static final int NAME_COLOR = 0xFFE0E5EF;
    private static final int NAME_MAX_LENGTH = 3;

    private IconRenderer() {
    }

    /**
     * Draws the icon centred in a square box of {@code boxSize} whose top-left corner is {@code (x, y)}.
     */
    public static void render(GuiGraphicsExtractor graphics, IconReference icon, int x, int y, int boxSize) {
        int inset = Math.max(0, (boxSize - ICON_SIZE) / 2);
        renderAt(graphics, icon, x + inset, y + inset);
    }

    /**
     * Draws the icon at an exact position, for a caller that has already centred it itself.
     */
    public static void renderAt(GuiGraphicsExtractor graphics, IconReference icon, int x, int y) {
        icon.item().ifPresentOrElse(
                item -> graphics.item(item.create(), x, y),
                () -> icon.texture().ifPresent(texture -> graphics.blit(RenderPipelines.GUI_TEXTURED, texture,
                        x, y, 0.0F, 0.0F, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE)));
    }

    /**
     * Draws an icon in a box, or the first few characters of the name when a definition has no icon yet.
     */
    public static void renderOrName(GuiGraphicsExtractor graphics, Font font, Optional<IconReference> icon,
                                    Component name, int x, int y, int boxSize) {
        if (icon.isPresent()) {
            render(graphics, icon.orElseThrow(), x, y, boxSize);
            return;
        }
        String text = name.getString();
        if (text.length() > NAME_MAX_LENGTH) text = text.substring(0, NAME_MAX_LENGTH);
        graphics.text(font, text, x + (boxSize - font.width(text)) / 2,
                y + Math.max(0, (boxSize - ICON_SIZE) / 2) + NAME_OFFSET, NAME_COLOR, true);
    }
}
