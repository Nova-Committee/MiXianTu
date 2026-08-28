package com.iafenvoy.mxt.render.overlay.resourcebar.renderer;

import com.iafenvoy.mxt.config.MxtClientConfig;
import com.iafenvoy.mxt.config.MxtClientConfig.ResourceBarIconLayout;
import com.iafenvoy.mxt.data.resource.ResourceBar.Anchor;
import com.iafenvoy.mxt.data.resource.ResourceBar.ValueDisplay;
import com.iafenvoy.mxt.data.resourcebar.builtin.renderdata.OriginsRenderData;
import com.iafenvoy.mxt.render.overlay.resourcebar.ResourceBarRenderState;
import com.iafenvoy.mxt.render.overlay.resourcebar.ResourceBarRenderer.Context;
import it.unimi.dsi.fastutil.objects.ObjectIntPair;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.Locale;
import java.util.Optional;

/**
 * Common labels, icons, and number display shared by stateless resource-bar renderers.
 */
final class ResourceBarRendererHelper {
    static void decorations(Context context, boolean showValueDisplay) {
        ResourceBarRenderState state = context.state();
        icon(state).ifPresent(icon -> renderIcon(context, icon));
        if (showValueDisplay && state.valueDisplay() != ValueDisplay.NONE) renderValueDisplay(context);
        if (MxtClientConfig.showResourceBarNames()) state.name().ifPresent(name -> renderName(context, name));
    }

    static void value(Context context, int color, boolean showMaximum, String valueFormat) {
        ResourceBarRenderState state = context.state();
        String value = valueFormat.replace("%current%", format(state.current()))
                .replace("%maximum%", format(state.maximum()));
        if (showMaximum && !valueFormat.contains("%maximum%")) value += " / " + format(state.maximum());
        context.graphics().text(context.minecraft().font, value,
                context.x() + (state.renderData().width() - context.minecraft().font.width(value)) / 2,
                contentY(context), color, true);
    }

    private static void renderIcon(Context context, ObjectIntPair<Identifier> icon) {
        ResourceBarRenderState state = context.state();
        boolean center = MxtClientConfig.resourceBarIconLayout() == ResourceBarIconLayout.CENTER;
        int x = state.anchor() == Anchor.LEFT
                ? center ? context.x() + state.renderData().width() + 3 : context.x() - 11
                : center ? context.x() - 11 : context.x() + state.renderData().width() + 3;
        context.graphics().blit(RenderPipelines.GUI_TEXTURED, icon.left(), x, context.y() - 2,
                73.0F, 8 + icon.rightInt() * 10, 8, 8, 256, 256);
    }

    private static void renderName(Context context, Component name) {
        ResourceBarRenderState state = context.state();
        String text = name.getString();
        if (text.isBlank()) return;
        boolean left = state.anchor() == Anchor.LEFT;
        boolean hasSideIcon = icon(state).isPresent()
                && MxtClientConfig.resourceBarIconLayout() != ResourceBarIconLayout.CENTER;
        int x = left ? context.x() - 3 - context.minecraft().font.width(text)
                : context.x() + state.renderData().width() + 3;
        if (hasSideIcon) x += left ? -11 : 11;
        context.graphics().text(context.minecraft().font, name, x, contentY(context), 0xFFFFFFFF, true);
    }

    private static void renderValueDisplay(Context context) {
        ResourceBarRenderState state = context.state();
        String value = switch (state.valueDisplay()) {
            case NONE -> "";
            case CURRENT -> format(state.current());
            case CURRENT_AND_MAXIMUM -> format(state.current()) + " / " + format(state.maximum());
            case PERCENTAGE -> format(state.percentage() * 100.0D) + "%";
        };
        if (!value.isBlank()) context.graphics().text(context.minecraft().font, value,
                context.x() + (state.renderData().width() - context.minecraft().font.width(value)) / 2,
                contentY(context), 0xFFFFFFFF, true);
    }

    private static int contentY(Context context) {
        int barY = context.y();
        if (context.state().renderData() instanceof OriginsRenderData) barY -= 2;
        return barY + (context.state().renderData().height() - context.minecraft().font.lineHeight) / 2;
    }

    private static Optional<ObjectIntPair<Identifier>> icon(ResourceBarRenderState state) {
        if (state.renderData() instanceof OriginsRenderData origins)
            return Optional.of(ObjectIntPair.of(origins.texture(), origins.resolvedIconIndex()));
        return Optional.empty();
    }

    static String format(double value) {
        return Math.abs(value - Math.rint(value)) < 0.0001D ? Long.toString(Math.round(value))
                : String.format(Locale.ROOT, "%.1f", value);
    }
}
