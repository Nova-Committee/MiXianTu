package com.iafenvoy.mxt.screen.resourcebar;

import com.iafenvoy.mxt.data.resource.ResourceBar.Anchor;
import com.iafenvoy.mxt.data.resourcebar.ResourceBarContext.Layout;
import com.iafenvoy.mxt.screen.hud.AbstractHudEntry;
import com.iafenvoy.mxt.screen.hud.RenderBlock;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;

/**
 * The bars about whatever the crosshair is on, as the original overlay's two rows: the target row near the top
 * of the screen and the boss row below it. Both are not movable - they belong to the entity being looked at -
 * and {@link #visible()} answers "is there something to show", which keeps the rows out of the layout file.
 */
public final class ResourceBarFixedEntry extends AbstractHudEntry {
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

    // The row is pinned to whatever the crosshair is on, so the editor does not offer it.
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

    @Override
    public List<RenderBlock> renderBlocks() {
        return ResourceBarEntry.blocksWithGaps(this.row());
    }

    // Nothing to do: the entry is built out of blocks, so the layout draws it.
    @Override
    public void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
    }

    @Override
    public int defaultOffsetX() {
        List<ResourceBarRenderState> row = this.row();
        Anchor side = row.isEmpty() ? Anchor.LEFT : row.getFirst().anchor();
        return ResourceBarOverlay.rowX(Minecraft.getInstance().getWindow().getGuiScaledWidth(), side,
                this.layoutWidth());
    }

    @Override
    public int defaultOffsetY() {
        return this.top;
    }

    // placeAtDefault() and not the base refreshPlacement(): the row stores nothing, and the call is also what
    // gives it an anchor point at all - without it the row would draw at the window's top-left corner.
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
