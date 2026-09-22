package com.iafenvoy.mxt.screen.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * One thing for the layout to draw, expressed as a size plus the drawing itself. A HUD entry does not draw: it
 * hands the layout a list of these, and the single renderer in {@link HudRenderer} puts them at the entry's
 * position, so no block ever computes its own screen coordinate.
 */
@FunctionalInterface
public interface RenderBlock {
    void draw(GuiGraphicsExtractor graphics, Minecraft minecraft, int x, int y);

    default int width() {
        return 0;
    }

    default int height() {
        return 0;
    }

    // How far this block is drawn to the right of the stack it belongs to: the block says how far in it sits,
    // not where it lands. There is no vertical counterpart - the heights reported here already stack it.
    default int left() {
        return 0;
    }

    static RenderBlock of(int width, int height, RenderBlock drawable) {
        return new Sized(width, height, 0, drawable);
    }

    static RenderBlock at(int left, int width, int height, RenderBlock drawable) {
        return new Sized(width, height, left, drawable);
    }

    static RenderBlock label(Component text, int color) {
        return new Label(Minecraft.getInstance().font.width(text), color, text);
    }

    static List<RenderBlock> none() {
        return List.of();
    }

    static RenderBlock fill(int width, int height, int color) {
        return of(width, height, (graphics, minecraft, x, y) -> graphics.fill(x, y, x + width, y + height, color));
    }

    static RenderBlock spacer(int width, int height) {
        return of(width, height, (graphics, minecraft, x, y) -> {
        });
    }

    // Must stay in step with how HudRenderer stacks: the width is the furthest right edge, the height is the
    // sum of the reported heights.
    static ScreenBounds boundsOf(List<RenderBlock> blocks) {
        int width = 0;
        int height = 0;
        for (RenderBlock block : blocks) {
            width = Math.max(width, block.left() + block.width());
            height += block.height();
        }
        return new ScreenBounds(0, 0, width, height);
    }

    final class Sized implements RenderBlock {
        private final int width;
        private final int height;
        private final int left;
        private final RenderBlock drawable;

        private Sized(int width, int height, int left, RenderBlock drawable) {
            this.width = width;
            this.height = height;
            this.left = left;
            this.drawable = drawable;
        }

        @Override
        public void draw(GuiGraphicsExtractor graphics, Minecraft minecraft, int x, int y) {
            this.drawable.draw(graphics, minecraft, x, y);
        }

        @Override
        public int width() {
            return this.width;
        }

        @Override
        public int height() {
            return this.height;
        }

        @Override
        public int left() {
            return this.left;
        }
    }

    final class Label implements RenderBlock {
        private final int width;
        private final int color;
        private final Component text;

        private Label(int width, int color, Component text) {
            this.width = width;
            this.color = color;
            this.text = text;
        }

        @Override
        public void draw(GuiGraphicsExtractor graphics, Minecraft minecraft, int x, int y) {
            graphics.text(minecraft.font, this.text, x, y, this.color, true);
        }

        @Override
        public int width() {
            return this.width;
        }

        @Override
        public int height() {
            return Minecraft.getInstance().font.lineHeight;
        }
    }
}
