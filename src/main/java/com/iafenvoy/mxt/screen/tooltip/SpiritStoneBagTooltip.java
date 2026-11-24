package com.iafenvoy.mxt.screen.tooltip;

import com.iafenvoy.mxt.item.SpiritStoneBagItem;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientBundleTooltip;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import org.jspecify.annotations.NonNull;

@EventBusSubscriber(Dist.CLIENT)
public final class SpiritStoneBagTooltip extends ClientBundleTooltip {
    private static final Identifier BAR_BORDER = Identifier.withDefaultNamespace("container/bundle/bundle_progressbar_border");
    private static final Identifier BAR_FILL = Identifier.withDefaultNamespace("container/bundle/bundle_progressbar_fill");
    private static final Identifier BAR_FULL = Identifier.withDefaultNamespace("container/bundle/bundle_progressbar_full");
    private static final int BAR_HEIGHT = 13;
    private static final int BOTTOM_MARGIN = 4;

    private final long count;

    private SpiritStoneBagTooltip(SpiritStoneBagItem.BagTooltip tooltip) {
        super(tooltip.contents());
        this.count = SpiritStoneBagItem.stoneCount(tooltip.contents());
    }

    @SubscribeEvent
    public static void register(RegisterClientTooltipComponentFactoriesEvent event) {
        event.register(SpiritStoneBagItem.BagTooltip.class, SpiritStoneBagTooltip::new);
    }

    @Override
    public void extractImage(@NonNull Font font, int x, int y, int width, int height, GuiGraphicsExtractor graphics) {
        // Keep the vanilla item layout and replace its weight-based capacity bar.
        int barY = y + this.getHeight(font) - BAR_HEIGHT - BOTTOM_MARGIN;
        graphics.enableScissor(0, 0, graphics.guiWidth(), barY);
        try {
            super.extractImage(font, x, y, width, height, graphics);
        } finally {
            graphics.disableScissor();
        }

        int barWidth = this.getWidth(font);
        int barX = x + (width - barWidth) / 2;
        int capacity = SpiritStoneBagItem.CAPACITY;
        int fillWidth = (int) (Math.min(this.count, capacity) * (barWidth - 2) / capacity);
        Identifier fill = this.count >= capacity ? BAR_FULL : BAR_FILL;
        if (fillWidth > 0)
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, fill, barX + 1, barY, fillWidth, BAR_HEIGHT);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, BAR_BORDER, barX, barY, barWidth, BAR_HEIGHT);
        graphics.centeredText(font, Component.translatable("item.mxt.spirit_stone_bag.capacity", this.count, capacity),
                barX + barWidth / 2, barY + 3, -1);
    }
}
