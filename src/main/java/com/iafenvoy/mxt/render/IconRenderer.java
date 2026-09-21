package com.iafenvoy.mxt.render;

import com.iafenvoy.mxt.data.IconReference;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;

import java.util.Optional;

/**
 * The client-side half of {@link IconReference}: the only place that turns an icon into pixels. A screen
 * passes the graphics and the box it has room for, and this picks the item branch or the texture branch and
 * centres it.
 */
public final class IconRenderer {
    /**
     * The size an icon is drawn at. Vanilla items render at 16x16 and a GUI texture is authored at 16x16,
     * so a roomier slot centres the icon instead of stretching it.
     */
    public static final int ICON_SIZE = 16;
    /**
     * How far a stand-in made of the name sits below the icon's own position: the nudge the compact slots
     * were drawn with.
     */
    private static final int NAME_OFFSET = 7;
    private static final int NAME_COLOR = 0xFFE0E5EF;
    /**
     * How much of the box's width the name may not use, so the frame it sits in stays visible.
     */
    private static final int NAME_INSET = 2;

    private IconRenderer() {
    }

    public static void render(GuiGraphicsExtractor graphics, IconReference icon, int x, int y, int boxSize) {
        int inset = Math.max(0, (boxSize - ICON_SIZE) / 2);
        renderAt(graphics, icon, x + inset, y + inset);
    }

    public static void renderAt(GuiGraphicsExtractor graphics, IconReference icon, int x, int y) {
        icon.item().ifPresentOrElse(
                item -> graphics.item(item.create(), x, y),
                () -> icon.texture().ifPresent(texture -> graphics.blit(RenderPipelines.GUI_TEXTURED, texture,
                        x, y, 0.0F, 0.0F, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE)));
    }

    /**
     * Draws an icon in a box, or as much of the name as fits when there is no icon. The name is cut to the box
     * rather than to a fixed character count, so it can never be drawn over the neighbouring box.
     */
    public static void renderOrName(GuiGraphicsExtractor graphics, Font font, Optional<IconReference> icon,
                                    Component name, int x, int y, int boxSize) {
        if (icon.isPresent()) {
            render(graphics, icon.orElseThrow(), x, y, boxSize);
            return;
        }
        String text = fit(font, name.getString(), boxSize - NAME_INSET);
        if (text.isEmpty()) return;
        graphics.text(font, text, x + (boxSize - font.width(text)) / 2,
                y + Math.max(0, (boxSize - ICON_SIZE) / 2) + NAME_OFFSET, NAME_COLOR, true);
    }

    /**
     * Draws a name centred on a point, cut to the given width: the same rule as {@link #renderOrName} for
     * callers that are not filling a box (the wheel writes a sector's name where its icon would have been).
     */
    public static void renderName(GuiGraphicsExtractor graphics, Font font, Component name, int centreX, int centreY, int maxWidth) {
        String text = fit(font, name.getString(), maxWidth);
        if (text.isEmpty()) return;
        graphics.text(font, text, centreX - font.width(text) / 2, centreY - font.lineHeight / 2, NAME_COLOR, true);
    }

    /**
     * The longest prefix that fits the width, counted in code points so a character is never cut in half.
     */
    private static String fit(Font font, String text, int maxWidth) {
        if (maxWidth <= 0) return "";
        String best = "";
        int characters = text.codePointCount(0, text.length());
        for (int count = 1; count <= characters; count++) {
            String prefix = text.substring(0, text.offsetByCodePoints(0, count));
            if (font.width(prefix) > maxWidth) break;
            best = prefix;
        }
        return best;
    }
}
