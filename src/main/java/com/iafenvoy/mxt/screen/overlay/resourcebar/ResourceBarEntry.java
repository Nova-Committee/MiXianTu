package com.iafenvoy.mxt.screen.overlay.resourcebar;

import com.iafenvoy.mxt.data.resource.ResourceBar.Anchor;
import com.iafenvoy.mxt.screen.overlay.hud.AbstractHudEntry;
import com.iafenvoy.mxt.screen.overlay.hud.HudAnchor;
import com.iafenvoy.mxt.screen.overlay.hud.HudLayout;
import com.iafenvoy.mxt.screen.overlay.hud.RenderBlock;
import com.iafenvoy.mxt.screen.overlay.hud.ScreenBounds;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * One draggable column of resource bars - the left column or the right column, and nothing else.
 *
 * <p>The two columns are separate entries on purpose. They are two independent stacks of bars that happen to
 * share a layout rule, and a player who wants their own resource on the left and the target's on the right
 * (or the whole column moved out of another mod's way) is expressing two decisions, not one. It is also what
 * makes "drag the left column" something the framework can express at all: an entry has one position, and a
 * left-and-right pair does not.</p>
 *
 * <p>Both columns keep their original defaults, derived from the centre of the screen and from where vanilla
 * puts the hotbar and the health bar. The framework re-applies a default on every frame until the player
 * moves the entry for the first time, so a column that has never been dragged still follows the window; once
 * it has been placed, it stays where it was put.</p>
 *
 * <p>The column hangs from the midpoint of its bottom edge ({@link HudAnchor#CENTER_BOTTOM}), which is both
 * where the original layout stood - a fixed line above the hotbar, growing upward - and what makes the
 * default position of a column independent of how many bars happen to be in it right now.</p>
 *
 * <p>The bars themselves live at positions relative to the column's top-left corner, and the icon and name of
 * a bar are drawn relative to the bar ({@code ResourceBarRendererHelper}). So an entry only has to add its
 * own position to each bar's slot, and the whole assembly - bars, icons and names - moves together.</p>
 */
public final class ResourceBarEntry extends AbstractHudEntry {
    /**
     * Half the horizontal room the original layout kept around the centre of the screen: resource bars were
     * drawn from {@code centreX - 20} leftwards and {@code centreX + 20} rightwards, which is the vanilla
     * hotbar's own extent plus a small margin.
     */
    public static final int CENTRE_GAP = 20;

    /**
     * Vertical room between two bars of one column. The bars themselves are five pixels tall, so this is what
     * keeps them from reading as one block. It is added as an empty block by {@link #blocksWithGaps} rather
     * than baked into every bar's height, so that the last bar does not reserve room for a gap it never has.
     */
    public static final int BAR_GAP = 5;

    /**
     * The room an empty column keeps: the size of one bar, so that a player with no resource bars yet still
     * sees a rectangle in the editor and can place the column before it has anything in it.
     */
    private static final int EMPTY_WIDTH = 71;
    private static final int EMPTY_HEIGHT = 8;

    /**
     * How far above the bottom of the screen the columns stand: the height of the hotbar plus the health bar
     * and the armour row above it.
     */
    private static final int BOTTOM_MARGIN = 47;

    private final Anchor side;
    private Bounds layout = new Bounds(EMPTY_WIDTH, EMPTY_HEIGHT);
    /**
     * The column as it was last measured: built once per frame so the size the framework places and the
     * blocks it draws are literally the same list.
     */
    private List<RenderBlock> blocks = List.of();

    public ResourceBarEntry(String layoutKey, Anchor side) {
        super(layoutKey, EMPTY_WIDTH, EMPTY_HEIGHT);
        this.side = side;
    }

    /**
     * Which of the two columns this is. Named {@code side} rather than {@code anchor} so that it cannot be
     * confused with {@link HudAnchor}, which is about a point of the rectangle rather than about left and
     * right of the screen.
     */
    public Anchor side() {
        return this.side;
    }

    @Override
    public String displayName() {
        return Component.translatable(this.side == Anchor.LEFT
                ? "hud.mxt.resource_column.left"
                : "hud.mxt.resource_column.right").getString();
    }

    @Override
    public int layoutWidth() {
        return this.layout.width();
    }

    @Override
    public int layoutHeight() {
        return this.layout.height();
    }

    @Override
    public void refreshPlacement() {
        // The column's size is what it is about to draw, so the placement is computed from the same list the
        // renderer is handed rather than from a size guessed a frame earlier. Both calls land in the same
        // frame, so the rectangle drawn and the rectangle clicked are the same one.
        this.applyLayout(ResourceBarOverlay.column(this.side));
        super.refreshPlacement();
    }

    @Override
    public List<RenderBlock> renderBlocks() {
        // The entry contributes content and nothing else: it measures the column so the layout can place it,
        // and offers one block per bar with its slot already resolved. Every coordinate in this method is
        // relative to the column, which is what lets the layout move the whole thing.
        List<ResourceBarRenderState> column = ResourceBarOverlay.column(this.side);
        this.applyLayout(column);
        return this.blocks;
    }

    /**
     * Nothing to do: this entry is built out of blocks, so the layout draws it. The framework only calls this
     * for an entry whose {@link #renderBlocks()} came back empty, and an empty column is not drawn outside the
     * editor - there is nothing to draw.
     */
    @Override
    public void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
    }

    /**
     * The rectangle the column occupies, which is also what the editor outlines. An empty column keeps the
     * bar minimum size rather than collapsing, so that a player with no resource bars yet can still see that
     * the feature is there and where the column lives.
     */
    @Override
    public ScreenBounds bounds() {
        return super.bounds();
    }

    /**
     * The columns hang from the midpoint of their bottom edge. That is what the original layout did - the bars
     * stood on a fixed line above the hotbar and grew upward as more of them appeared - and it is what makes
     * the default position meaningful: the centre marks where the column is, and the bottom edge marks where
     * it ends.
     */
    @Override
    public HudAnchor anchor() {
        return HudAnchor.CENTER_BOTTOM;
    }

    @Override
    public int defaultX() {
        int[] window = HudLayout.window();
        if (window == null) return 0;
        int centre = window[0] / 2;
        // The two columns sit one on each side of the middle of the screen, and the value handed here is the
        // anchor point - the centre of the column - because the anchor is centred horizontally. The left
        // column's *right* edge used to be 20 pixels left of centre, so its centre is 20 pixels plus half a
        // column further out; the right column mirrors that.
        int offset = CENTRE_GAP + this.layout.width() / 2;
        return this.side == Anchor.LEFT ? centre - offset : centre + offset;
    }

    @Override
    public int defaultY() {
        int[] window = HudLayout.window();
        return window == null ? 0 : window[1] - BOTTOM_MARGIN;
    }

    /**
     * Turns the column into the blocks it is made of, records the room they need, and hands the size to the
     * framework. Both the size and the drawing therefore come from one list built in one place: measuring
     * from the render data instead would count a bar's declared height rather than the height its block
     * reports, and the two differ for exactly the bar type that dominates this mod's HUD.
     */
    private void applyLayout(List<ResourceBarRenderState> column) {
        this.blocks = blocksWithGaps(column);
        ScreenBounds union = RenderBlock.boundsOf(this.blocks);
        // An empty column keeps the bar minimum rather than collapsing to nothing, so the editor still has
        // something to show and to aim at.
        this.layout = this.blocks.isEmpty()
                ? new Bounds(EMPTY_WIDTH, EMPTY_HEIGHT)
                : new Bounds(union.width(), union.height());
        this.setSize(this.layout.width(), this.layout.height());
    }

    /**
     * Turns a column of bars into the blocks the layout stacks: one per bar, with an empty block between them
     * for {@link #BAR_GAP}.
     *
     * <p>The gap is its own block rather than extra height on each bar, and there are two reasons. The last
     * bar would otherwise reserve room for a gap it never has, making the column taller than it draws; and a
     * block's height is the same number the stacking advances by, so a gap expressed anywhere else would be an
     * offset the stacking does not know about - which is precisely the double-count that made this column
     * twice its real height.</p>
     */
    public static List<RenderBlock> blocksWithGaps(List<ResourceBarRenderState> column) {
        List<RenderBlock> blocks = new ArrayList<>(column.size() * 2);
        for (ResourceBarRenderState state : column) {
            if (!blocks.isEmpty() && BAR_GAP > 0) blocks.add(RenderBlock.spacer(0, BAR_GAP));
            blocks.add(state.block());
        }
        return blocks;
    }

    /**
     * The union of what a column draws: as wide as its widest bar, and as tall as the blocks add up to.
     *
     * <p>Icons and name labels are deliberately not counted in. They stick out past the bars, and drawing
     * outside the placement rectangle is what a label is for; widening the rectangle would put empty space
     * under the cursor instead. A column with no bars at all keeps the {@link #EMPTY_WIDTH} by
     * {@link #EMPTY_HEIGHT} box the editor uses to show that the column exists.</p>
     */
    public static Bounds measure(List<RenderBlock> blocks) {
        if (blocks.isEmpty()) return new Bounds(EMPTY_WIDTH, EMPTY_HEIGHT);
        ScreenBounds union = RenderBlock.boundsOf(blocks);
        return new Bounds(union.width(), union.height());
    }

    /**
     * The size of one column, computed from the bars rather than declared by the data pack.
     */
    public record Bounds(int width, int height) {
    }
}
