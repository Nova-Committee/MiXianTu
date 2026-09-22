package com.iafenvoy.mxt.render;

import com.iafenvoy.mxt.data.IconReference;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;

import java.util.Optional;

/**
 * The client-side half of {@link IconReference} and the only place that turns an icon into pixels.
 */
public final class IconRenderer {
    // Vanilla items and GUI textures are authored 16x16, so a roomier box centres the icon instead of stretching it.
    public static final int ICON_SIZE = 16;
    // The nudge the compact slots drew the name with, below the icon's own position.
    private static final int NAME_OFFSET = 7;
    private static final int NAME_COLOR = 0xFFE0E5EF;
    // Keeps the frame around the box visible.
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

    // The name is cut to the box rather than to a character count, so it can never overdraw the neighbouring box.
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

    // Same cut-to-width rule as renderOrName, for callers that are not filling a box.
    public static void renderName(GuiGraphicsExtractor graphics, Font font, Component name, int centreX, int centreY, int maxWidth) {
        String text = fit(font, name.getString(), maxWidth);
        if (text.isEmpty()) return;
        graphics.text(font, text, centreX - font.width(text) / 2, centreY - font.lineHeight / 2, NAME_COLOR, true);
    }

    // Counted in code points, so a character is never cut in half.
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
