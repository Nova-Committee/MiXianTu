package com.iafenvoy.mxt.screen.resourcebar;

import com.iafenvoy.mxt.data.resource.ResourceBar.Anchor;
import com.iafenvoy.mxt.screen.hud.*;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * One draggable column of resource bars - left or right. Each column is a separate entry because an entry has
 * one position, and they hang from the midpoint of their bottom edge, where the original layout stood above
 * the hotbar. Bar positions are relative to the column's top-left corner.
 */
public final class ResourceBarEntry extends AbstractHudEntry {
    // Half the horizontal room the original layout kept around the centre of the screen: the vanilla hotbar's
    // own extent plus a small margin.
    public static final int CENTRE_GAP = 20;

    // Vertical room between two bars of one column (the bars themselves are five pixels tall), added as an
    // empty block by blocksWithGaps rather than baked into every bar, so the last bar reserves no gap.
    public static final int BAR_GAP = 5;

    // The room an empty column keeps - one bar's size - so the editor still has a rectangle to place.
    private static final int EMPTY_WIDTH = 71;
    private static final int EMPTY_HEIGHT = 8;

    // How far above the bottom of the screen the columns stand: hotbar plus health bar plus the armour row.
    private static final int BOTTOM_MARGIN = 47;

    private final Anchor side;
    private Bounds layout = new Bounds(EMPTY_WIDTH, EMPTY_HEIGHT);
    // The column as last measured: one list per frame, so the size the framework places and the blocks it
    // draws are literally the same.
    private List<RenderBlock> blocks = List.of();

    public ResourceBarEntry(String layoutKey, Anchor side) {
        super(layoutKey, EMPTY_WIDTH, EMPTY_HEIGHT);
        this.side = side;
    }

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
        // Measured from the same list the renderer is handed, not from a size guessed a frame earlier: both
        // calls land in the same frame, so the rectangle drawn and the rectangle clicked are the same one.
        this.applyLayout(ResourceBarOverlay.column(this.side));
        super.refreshPlacement();
    }

    @Override
    public List<RenderBlock> renderBlocks() {
        // Every coordinate here is relative to the column, which is what lets the layout move the whole thing.
        List<ResourceBarRenderState> column = ResourceBarOverlay.column(this.side);
        this.applyLayout(column);
        return this.blocks;
    }

    // Nothing to do: the entry is built out of blocks, and the framework only calls this for one whose
    // renderBlocks() came back empty.
    @Override
    public void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
    }

    @Override
    public ScreenBounds bounds() {
        return super.bounds();
    }

    // Centred on the bottom edge of the window: the centre marks where the column is and that edge where it ends,
    // so the default position does not depend on how many bars the column holds right now.
    @Override
    public HudAnchor defaultAnchor() {
        return HudAnchor.CENTER_BOTTOM;
    }

    @Override
    public int defaultOffsetX() {
        // What is handed here is the distance from the middle of the screen, because the anchor is centred
        // horizontally. The left column's centre sits this far left of it; the right column mirrors it.
        int offset = CENTRE_GAP + this.layout.width() / 2;
        return this.side == Anchor.LEFT ? -offset : offset;
    }

    @Override
    public int defaultOffsetY() {
        // Upward from the bottom edge: the sign the anchor's offset is measured with.
        return -BOTTOM_MARGIN;
    }

    // Size and drawing come from one list built in one place: measuring from the render data instead would
    // count a bar's declared height, which differs from the height its block reports.
    private void applyLayout(List<ResourceBarRenderState> column) {
        this.blocks = blocksWithGaps(column);
        ScreenBounds union = RenderBlock.boundsOf(this.blocks);
        // An empty column keeps the bar minimum rather than collapsing, so the editor still has something to
        // show and to aim at.
        this.layout = this.blocks.isEmpty()
                ? new Bounds(EMPTY_WIDTH, EMPTY_HEIGHT)
                : new Bounds(union.width(), union.height());
        this.setSize(this.layout.width(), this.layout.height());
    }

    // The gap is its own block rather than extra height on each bar: the last bar would otherwise reserve room
    // for a gap it never has, and a height the stacking does not advance by would double-count the column.
    public static List<RenderBlock> blocksWithGaps(List<ResourceBarRenderState> column) {
        List<RenderBlock> blocks = new ArrayList<>(column.size() * 2);
        for (ResourceBarRenderState state : column) {
            if (!blocks.isEmpty() && BAR_GAP > 0) blocks.add(RenderBlock.spacer(0, BAR_GAP));
            blocks.add(state.block());
        }
        return blocks;
    }

    // Icons and name labels are deliberately not counted in: they stick out past the bars, and widening the
    // rectangle would put empty space under the cursor. An empty column keeps the EMPTY_WIDTH/EMPTY_HEIGHT box.
    public static Bounds measure(List<RenderBlock> blocks) {
        if (blocks.isEmpty()) return new Bounds(EMPTY_WIDTH, EMPTY_HEIGHT);
        ScreenBounds union = RenderBlock.boundsOf(blocks);
        return new Bounds(union.width(), union.height());
    }

    public record Bounds(int width, int height) {
    }
}
