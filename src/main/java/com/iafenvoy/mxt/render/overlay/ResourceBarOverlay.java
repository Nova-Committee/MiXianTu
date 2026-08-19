package com.iafenvoy.mxt.render.overlay;
import com.iafenvoy.mxt.registry.MxtResourceKeys;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.attachment.ResourceHolderData;
import com.iafenvoy.mxt.attachment.ResourceHolderData.Audit;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.data.resource.ResourceBar;
import com.iafenvoy.mxt.data.resource.ResourceBar.Anchor;
import com.iafenvoy.mxt.data.resource.ResourceBar.Context;
import com.iafenvoy.mxt.data.resource.ResourceBar.ValueDisplay;
import com.iafenvoy.mxt.data.resourcebar.BuiltinResourceBarRenderers.Radial;
import com.iafenvoy.mxt.data.resourcebar.BuiltinResourceBarRenderers.Segmented;
import com.iafenvoy.mxt.data.resourcebar.BuiltinResourceBarRenderers.TextOnly;
import com.iafenvoy.mxt.data.resourcebar.BuiltinResourceBarRenderers.Textured;
import com.iafenvoy.mxt.data.resourcebar.BuiltinResourceBarRenderers.Origins;
import com.iafenvoy.mxt.data.resourcebar.ResourceBarRenderer;
import com.iafenvoy.mxt.data.resourcebar.ResourceBarView;
import com.iafenvoy.mxt.registry.MxtAttachments;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.Holder.Reference;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.GuiLayer;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Client-side resource HUD. Its collection and stacking model follows Origins'
 * resource overlay, while rendering is driven by MiXianTu's resource-bar data.
 */
@EventBusSubscriber(modid = MiXianTu.MOD_ID, value = Dist.CLIENT)
public enum ResourceBarOverlay implements GuiLayer {
    INSTANCE;

    private static final int BAR_WIDTH = 71;
    private static final int BAR_HEIGHT = 8;
    private static final int BAR_GAP = 0;
    private static final int DEFAULT_FILL = 0xFF4E9CFF;
    private static final int DEFAULT_EMPTY = 0xFF243047;

    @Override
    public void render(@NotNull GuiGraphicsExtractor graphics, @NotNull DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (minecraft.options.hideGui || player == null || minecraft.level == null) return;

        Map<LayoutKey, Integer> offsets = new HashMap<>();
        for (ResolvedBar bar : collect(player)) {
            LayoutKey layout = new LayoutKey(bar.definition().context(), bar.definition().anchor());
            int offset = offsets.getOrDefault(layout, 0);
            Position position = position(minecraft, bar, offset);
            renderBar(graphics, minecraft, bar, position);
            offsets.put(layout, offset + bar.height() + BAR_GAP);
        }
    }

    private static List<ResolvedBar> collect(Player player) {
        Registry<Resource> resources = player.level().registryAccess().lookupOrThrow(MxtResourceKeys.RESOURCE);
        List<ResolvedBar> result = new ArrayList<>();
        collectFor(result, resources, player, Context.SELF_HUD);
        if (minecraft().crosshairPickEntity instanceof LivingEntity target)
            collectFor(result, resources, target, Context.TARGET_OVERLAY);
        if (minecraft().crosshairPickEntity instanceof LivingEntity target)
            collectFor(result, resources, target, Context.BOSS_OVERLAY);
        return result.stream()
                .sorted(Comparator.comparing((ResolvedBar bar) -> bar.definition().context())
                        .thenComparingInt(bar -> bar.definition().order()).thenComparing(ResolvedBar::resourceId)
                        .thenComparingInt(ResolvedBar::index))
                .toList();
    }

    private static Minecraft minecraft() {
        return Minecraft.getInstance();
    }

    /**
     * Height occupied by visible self-HUD resource bars in one of the two shared columns.
     */
    public static int reservedSelfHudHeight(Player player, Anchor anchor) {
        return collect(player).stream()
                .filter(bar -> bar.definition().context() == Context.SELF_HUD && bar.definition().anchor() == anchor)
                .mapToInt(bar -> bar.height() + BAR_GAP)
                .sum();
    }

    private static void collectFor(List<ResolvedBar> result, Registry<Resource> resources, LivingEntity entity, Context context) {
        ResourceHolderData values = entity.getData(MxtAttachments.RESOURCE_HOLDER);
        long gameTime = entity.level().getGameTime();
        for (Reference<Resource> resource : resources.listElements().toList()) {
            Identifier id = resource.unwrapKey().map(ResourceKey::identifier).orElse(null);
            if (id == null || !values.contains(resource)) continue;
            List<ResourceBar> definitions = resource.value().bars();
            for (int index = 0; index < definitions.size(); index++) {
                ResourceBar bar = definitions.get(index);
                if (bar.context() != context || replacedByCustomBar(bar, definitions)) continue;
                Audit audit = values.audit(resource);
                double min = audit.minSnapshot();
                double maximum = audit.maxSnapshot();
                double current = values.get(resource);
                if (!Double.isFinite(min) || !Double.isFinite(maximum) || !Double.isFinite(current) || maximum < min || maximum < 0.0D)
                    continue;

                long changedAt = audit.lastChangedTick();
                long ticksSinceChanged = changedAt < 0L ? Long.MAX_VALUE : Math.max(0L, gameTime - changedAt);
                ResourceBarView view = new ResourceBarView(current, maximum, ticksSinceChanged, entity != minecraft().player);
                if (!bar.visibility().visible(view)) continue;
                result.add(new ResolvedBar(id, index, bar, current, min, maximum));
            }
        }
    }

    /**
     * A replacement declaration wins over any ordinary bar for the same resource and display context.
     */
    private static boolean replacedByCustomBar(ResourceBar candidate, List<ResourceBar> definitions) {
        return !candidate.replaceDefault() && definitions.stream().anyMatch(other -> other != candidate
                && other.replaceDefault() && other.context() == candidate.context());
    }

    private static Position position(Minecraft minecraft, ResolvedBar bar, int offset) {
        int width = minecraft.getWindow().getGuiScaledWidth();
        int height = minecraft.getWindow().getGuiScaledHeight();
        if (bar.definition().context() == Context.TARGET_OVERLAY)
            return overlayColumnPosition(width, 16 + offset, bar.width(), bar.definition().anchor());
        if (bar.definition().context() == Context.BOSS_OVERLAY)
            return overlayColumnPosition(width, 48 + offset, bar.width(), bar.definition().anchor());

        // Match Origins' placement above the hotbar, including the vanilla HUD adjustments.
        int y = height - 47;
        Player player = minecraft.player;
        if (player != null) {
            if (player.getVehicle() instanceof LivingEntity vehicle)
                y -= 8 * (int) (vehicle.getMaxHealth() / 20.0F);
            if (player.isEyeInFluid(FluidTags.WATER) || player.getAirSupply() < player.getMaxAirSupply())
                y -= 8;
        }
        int x = bar.definition().anchor() == Anchor.LEFT ? width / 2 - 20 - bar.width() : width / 2 + 20;
        return new Position(x, y - offset);
    }

    private static Position overlayColumnPosition(int width, int y, int barWidth, Anchor anchor) {
        int x = anchor == Anchor.LEFT ? width / 2 - 8 - barWidth : width / 2 + 8;
        return new Position(x, y);
    }

    private static void renderBar(GuiGraphicsExtractor graphics, Minecraft minecraft, ResolvedBar bar, Position position) {
        ResourceBarRenderer renderer = bar.definition().renderer();
        double progress = bar.progress();
        if (renderer instanceof Origins origins) {
            renderOrigins(graphics, origins, progress, position);
        } else if (renderer instanceof Textured textured) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, textured.backgroundSprite(), position.x(), position.y(), textured.width(), textured.height());
            int filled = (int) Math.round(textured.width() * progress);
            if (filled > 0)
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, textured.fillSprite(), position.x(), position.y(), filled, textured.height());
            if (textured.showValue())
                drawValue(graphics, minecraft, bar, position.x(), position.y(), textured.width(), textured.height(), 0xFFFFFFFF, true);
        } else if (renderer instanceof Segmented segmented) {
            renderSegmented(graphics, segmented, progress, position);
        } else if (renderer instanceof Radial radial) {
            renderRadial(graphics, radial, progress, position);
        } else if (renderer instanceof TextOnly(
                String format, String color, boolean showMaximum
        )) {
            drawValue(graphics, minecraft, bar, position.x(), position.y(), BAR_WIDTH, BAR_HEIGHT,
                    color(color, 0xFFFFFFFF), showMaximum, format);
        }
        if (!(renderer instanceof TextOnly) && bar.definition().valueDisplay() != ValueDisplay.NONE)
            drawValueDisplay(graphics, minecraft, bar, position.x(), position.y(), bar.width(), bar.height());
    }

    private static void renderOrigins(GuiGraphicsExtractor graphics, Origins renderer, double progress, Position position) {
        int fillY = 8 + renderer.barIndex() * 10;
        float fill = (float) progress;
        if (renderer.inverted()) fill = 1.0F - fill;
        int filled = (int) (fill * BAR_WIDTH);
        graphics.blit(RenderPipelines.GUI_TEXTURED, renderer.texture(), position.x(), position.y(), 0.0F, 0.0F, BAR_WIDTH, 5, 256, 256);
        if (filled > 0)
            graphics.blit(RenderPipelines.GUI_TEXTURED, renderer.texture(), position.x(), position.y() - 2, 0.0F, fillY, filled, BAR_HEIGHT, 256, 256);
        graphics.blit(RenderPipelines.GUI_TEXTURED, renderer.texture(), position.x() - 10, position.y() - 2, 73.0F, fillY, 8, 8, 256, 256);
    }

    private static void drawValueDisplay(GuiGraphicsExtractor graphics, Minecraft minecraft, ResolvedBar bar,
                                         int x, int y, int width, int height) {
        String value = switch (bar.definition().valueDisplay()) {
            case NONE -> "";
            case CURRENT -> format(bar.current());
            case CURRENT_AND_MAXIMUM -> format(bar.current()) + " / " + format(bar.maximum());
            case PERCENTAGE -> format(bar.progress() * 100.0D) + "%";
        };
        if (value.isBlank()) return;
        graphics.text(minecraft.font, value, x + (width - minecraft.font.width(value)) / 2,
                y + (height - 8) / 2, 0xFFFFFFFF, true);
    }

    private static void renderSegmented(GuiGraphicsExtractor graphics, Segmented renderer,
                                        double progress, Position position) {
        int filled = (int) Math.round(progress * renderer.segments());
        int fullColor = color(renderer.fullColor(), DEFAULT_FILL);
        int emptyColor = color(renderer.emptyColor(), DEFAULT_EMPTY);
        for (int index = 0; index < renderer.segments(); index++) {
            int x = position.x() + index * (BAR_HEIGHT + renderer.gap());
            graphics.fill(x, position.y(), x + BAR_HEIGHT, position.y() + BAR_HEIGHT, index < filled ? fullColor : emptyColor);
        }
    }

    private static void renderRadial(GuiGraphicsExtractor graphics, Radial renderer,
                                     double progress, Position position) {
        int centerX = position.x() + renderer.radius();
        int centerY = position.y() + renderer.radius();
        drawArc(graphics, centerX, centerY, renderer.radius(), renderer.thickness(), renderer.startAngle(), renderer.endAngle(), 1.0D, DEFAULT_EMPTY);
        drawArc(graphics, centerX, centerY, renderer.radius(), renderer.thickness(), renderer.startAngle(), renderer.endAngle(), progress,
                color(renderer.fillColor(), DEFAULT_FILL));
    }

    private static void drawArc(GuiGraphicsExtractor graphics, int centerX, int centerY, int radius, int thickness,
                                double startAngle, double endAngle, double progress, int color) {
        double span = endAngle - startAngle;
        int steps = Math.max(1, (int) Math.ceil(Math.abs(span) * Math.max(0.0D, Math.min(1.0D, progress))));
        for (int step = 0; step <= steps; step++) {
            double angle = Math.toRadians(startAngle + span * step / steps * progress);
            int x = centerX + (int) Math.round(Math.cos(angle) * radius);
            int y = centerY + (int) Math.round(Math.sin(angle) * radius);
            int half = Math.max(1, thickness / 2);
            graphics.fill(x - half, y - half, x + half + 1, y + half + 1, color);
        }
    }

    private static void drawValue(GuiGraphicsExtractor graphics, Minecraft minecraft, ResolvedBar bar, int x, int y,
                                  int width, int height, int color, boolean showMaximum) {
        drawValue(graphics, minecraft, bar, x, y, width, height, color, showMaximum, "%current%");
    }

    private static void drawValue(GuiGraphicsExtractor graphics, Minecraft minecraft, ResolvedBar bar, int x, int y,
                                  int width, int height, int color, boolean showMaximum, String format) {
        String value = format.replace("%current%", format(bar.current()))
                .replace("%maximum%", format(bar.maximum()));
        if (showMaximum && !format.contains("%maximum%")) value += " / " + format(bar.maximum());
        int textX = x + (width - minecraft.font.width(value)) / 2;
        int textY = y + (height - 8) / 2;
        graphics.text(minecraft.font, value, textX, textY, color, true);
    }

    private static String format(double value) {
        return Math.abs(value - Math.rint(value)) < 0.0001D ? Long.toString(Math.round(value)) : String.format(Locale.ROOT, "%.1f", value);
    }

    private static int color(String value, int fallback) {
        try {
            String hex = value.startsWith("#") ? value.substring(1) : value;
            long parsed = Long.parseLong(hex, 16);
            return hex.length() <= 6 ? (int) (0xFF000000L | parsed) : (int) parsed;
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    @SubscribeEvent
    public static void registerOverlay(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.HOTBAR, Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "resource_bars"), INSTANCE);
    }

    private record Position(int x, int y) {
    }

    private record LayoutKey(Context context, Anchor anchor) {
    }

    private record ResolvedBar(Identifier resourceId, int index, ResourceBar definition, double current, double minimum,
                               double maximum) {
        private double progress() {
            return this.maximum == this.minimum ? 1.0D : Math.max(0.0D, Math.min(1.0D, (this.current - this.minimum) / (this.maximum - this.minimum)));
        }

        private int width() {
            return switch (this.definition.renderer()) {
                case Textured textured -> textured.width();
                case Segmented segmented ->
                        segmented.segments() * BAR_HEIGHT + (segmented.segments() - 1) * segmented.gap();
                case Radial radial -> radial.radius() * 2 + radial.thickness();
                default -> BAR_WIDTH;
            };
        }

        private int height() {
            return switch (this.definition.renderer()) {
                case Textured textured -> textured.height();
                case Radial radial -> radial.radius() * 2 + radial.thickness();
                default -> BAR_HEIGHT;
            };
        }
    }
}
