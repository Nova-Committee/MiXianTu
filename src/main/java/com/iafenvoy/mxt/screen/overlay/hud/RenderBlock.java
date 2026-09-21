package com.iafenvoy.mxt.screen.overlay.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * One thing for the layout to draw, expressed as a size plus the drawing itself.
 *
 * <p>A HUD entry does not draw; it hands the layout a list of these. Everything that needs a position - a
 * stack of resource bars, a centred label, an icon - is described here in its own terms, and the single
 * renderer in {@link HudRenderer} puts it at the entry's position. That is what lets one entry own a stack
 * whose pieces know nothing about each other, and what keeps every draw going through one place.</p>
 *
 * <p>The size is what the layout needs: it is added up per axis to size the entry, so an entry with an empty
 * block list has no size of its own unless it declares a minimum.</p>
 */
@FunctionalInterface
public interface RenderBlock {
    void draw(GuiGraphicsExtractor graphics, Minecraft minecraft, int x, int y);

    /**
     * The horizontal extent of this block, in screen pixels, at the moment it is asked.
     */
    default int width() {
        return 0;
    }

    /**
     * The vertical extent of this block, in screen pixels, at the moment it is asked.
     */
    default int height() {
        return 0;
    }

    /**
     * How far this block is drawn to the right of the stack it belongs to. Only an element that draws
     * something beside its own body - a name label, a bar that is narrower than its column - needs this, and
     * the offset still comes from the layout: the block says how far in it sits, not where it lands.
     *
     * <p>There is deliberately no vertical counterpart. Blocks are stacked back to back by the heights they
     * report, so a vertical offset would be a second answer to a question the stacking already answers - and
     * the two would disagree the moment one of them was forgotten, which is exactly how a column ends up
     * twice as tall as it draws.</p>
     */
    default int left() {
        return 0;
    }

    /**
     * A block of a known size, drawn wherever the layout puts it.
     */
    static RenderBlock of(int width, int height, RenderBlock drawable) {
        return new Sized(width, height, 0, drawable);
    }

    /**
     * A block drawn at a horizontal offset from the stack it belongs to - a label beside a bar, say. Where
     * the stack itself lands is still the layout's business.
     */
    static RenderBlock at(int left, int width, int height, RenderBlock drawable) {
        return new Sized(width, height, left, drawable);
    }

    /**
     * A one-line block whose width and height come from the font.
     */
    static RenderBlock label(Component text, int color) {
        return new Label(Minecraft.getInstance().font.width(text), color, text);
    }

    /**
     * An empty list, for an entry that currently has nothing to show.
     */
    static List<RenderBlock> none() {
        return List.of();
    }

    /**
     * A rectangle of colour - the shape most HUD elements are made of.
     */
    static RenderBlock fill(int width, int height, int color) {
        return of(width, height, (graphics, minecraft, x, y) -> graphics.fill(x, y, x + width, y + height, color));
    }

    /**
     * Empty room in a stack: it draws nothing and only advances the stacking cursor. This is how an element
     * asks for a gap between two of its blocks, which keeps the spacing in the same currency as everything
     * else - the heights the stack is built from.
     */
    static RenderBlock spacer(int width, int height) {
        return of(width, height, (graphics, minecraft, x, y) -> {
        });
    }

    /**
     * The union of a list of blocks: as wide as their furthest right edge, as tall as their heights add up,
     * which is exactly how {@link HudRenderer} places them.
     */
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
