package com.iafenvoy.mxt.screen.overlay.resourcebar;

import com.iafenvoy.mxt.data.resource.ResourceBar.Anchor;
import com.iafenvoy.mxt.data.resourcebar.ResourceBarContext.Layout;
import com.iafenvoy.mxt.screen.overlay.hud.AbstractHudEntry;
import com.iafenvoy.mxt.screen.overlay.hud.RenderBlock;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;

/**
 * The resource bars about whatever the crosshair is on, split into the two rows the original overlay drew:
 * the target row near the top of the screen and the boss row below it.
 *
 * <p>Like the two columns, these are HUD elements drawn by the framework's single renderer - that is the
 * point of them being entries at all. Unlike them they are <strong>not movable</strong>: they belong to the
 * entity being looked at rather than to the player's own arrangement, so there is nothing for the player to
 * decide and nothing to store.</p>
 *
 * <p>{@link #visible()} answers "is there something to show right now" rather than "did the player switch
 * this on". That is what keeps the row out of the layout file: visibility is part of what an entry stores,
 * and nothing here ever calls {@link #setVisible(boolean)}, so a row that appears and disappears never
 * writes a placement.</p>
 */
public final class ResourceBarFixedEntry extends AbstractHudEntry {
    /** Where the row starts, measured from the top of the screen. */
    private static final int TARGET_TOP = 16;
    private static final int BOSS_TOP = 48;

    private final Layout layout;
    private final int top;

    private ResourceBarFixedEntry(String layoutKey, Layout layout, int top) {
        super(layoutKey, 0, 0);
        this.layout = layout;
        this.top = top;
    }

    public static ResourceBarFixedEntry target() {
        return new ResourceBarFixedEntry("resource_bars.target", Layout.TARGET_OVERLAY, TARGET_TOP);
    }

    public static ResourceBarFixedEntry boss() {
        return new ResourceBarFixedEntry("resource_bars.boss", Layout.BOSS_OVERLAY, BOSS_TOP);
    }

    @Override
    public String displayName() {
        return Component.translatable(this.layout == Layout.TARGET_OVERLAY
                ? "hud.mxt.resource_bar.target_overlay"
                : "hud.mxt.resource_bar.boss_overlay").getString();
    }

    /**
     * Fixed: the row is pinned to whatever the crosshair is on, so there is nothing to drag and the editor
     * does not offer it.
     */
    @Override
    public boolean moveable() {
        return false;
    }

    @Override
    public boolean visible() {
        return !this.row().isEmpty();
    }

    @Override
    public int layoutWidth() {
        int width = 0;
        for (ResourceBarRenderState state : this.row()) width = Math.max(width, state.renderData().width());
        return width;
    }

    @Override
    public int layoutHeight() {
        return RenderBlock.boundsOf(this.renderBlocks()).height();
    }

    /**
     * The row's blocks, spaced exactly like a column's so the two read as the same kind of thing.
     */
    @Override
    public List<RenderBlock> renderBlocks() {
        return ResourceBarEntry.blocksWithGaps(this.row());
    }

    /**
     * Nothing to do: this entry is built out of blocks, so the layout draws it.
     */
    @Override
    public void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
    }

    @Override
    public int defaultX() {
        List<ResourceBarRenderState> row = this.row();
        Anchor side = row.isEmpty() ? Anchor.LEFT : row.get(0).anchor();
        return ResourceBarOverlay.rowX(Minecraft.getInstance().getWindow().getGuiScaledWidth(), side,
                this.layoutWidth());
    }

    @Override
    public int defaultY() {
        return this.top;
    }

    /**
     * The row's size is derived every frame and its position is fixed, so it is placed through
     * {@link #placeAtDefault()} - the framework's "use the position this class suggests, store nothing" path -
     * and deliberately not through the base {@link #refreshPlacement()}, which would let a stored value from
     * an older version tug the row around. Without the placement call the row has no anchor point at all, and
     * it draws at the top-left corner of the window instead of under the crosshair's target.
     */
    @Override
    public void refreshPlacement() {
        this.setSize(this.layoutWidth(), this.layoutHeight());
        this.placeAtDefault();
    }

    private List<ResourceBarRenderState> row() {
        LivingEntity target = Minecraft.getInstance().crosshairPickEntity instanceof LivingEntity living ? living : null;
        return target == null ? List.of() : ResourceBarOverlay.row(target, this.layout);
    }
}
