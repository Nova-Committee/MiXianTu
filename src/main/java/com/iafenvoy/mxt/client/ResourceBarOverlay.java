package com.iafenvoy.mxt.client;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.attachment.ResourceHolderData;
import com.iafenvoy.mxt.attachment.ResourceHolderData.Audit;
import com.iafenvoy.mxt.data.resource.ResourceBarDefinition;
import com.iafenvoy.mxt.data.resource.ResourceBarDefinition.Anchor;
import com.iafenvoy.mxt.data.resource.ResourceBarDefinition.Context;
import com.iafenvoy.mxt.data.resource.ResourceDefinition;
import com.iafenvoy.mxt.data.resourcebar.BuiltinResourceBarRenderers.Radial;
import com.iafenvoy.mxt.data.resourcebar.BuiltinResourceBarRenderers.Segmented;
import com.iafenvoy.mxt.data.resourcebar.BuiltinResourceBarRenderers.TextOnly;
import com.iafenvoy.mxt.data.resourcebar.BuiltinResourceBarRenderers.Textured;
import com.iafenvoy.mxt.data.resourcebar.ResourceBarRenderer;
import com.iafenvoy.mxt.data.resourcebar.ResourceBarView;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.formula.FormulaContext;
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
import java.util.EnumMap;
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
    private static final int BAR_GAP = 2;
    private static final int DEFAULT_FILL = 0xFF4E9CFF;
    private static final int DEFAULT_EMPTY = 0xFF243047;

    @Override
    public void render(@NotNull GuiGraphicsExtractor graphics, @NotNull DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (minecraft.options.hideGui || player == null || minecraft.level == null) return;

        Map<Anchor, Integer> offsets = new EnumMap<>(Anchor.class);
        for (ResolvedBar bar : collect(player)) {
            int offset = offsets.getOrDefault(bar.definition().anchor(), 0);
            Position position = position(minecraft, bar, offset);
            renderBar(graphics, minecraft, bar, position);
            offsets.put(bar.definition().anchor(), offset + bar.height() + BAR_GAP);
        }
    }

    private static List<ResolvedBar> collect(Player player) {
        Registry<ResourceBarDefinition> bars = player.level().registryAccess().lookupOrThrow(MxtDatapackRegistries.RESOURCE_BAR);
        Registry<ResourceDefinition> resources = player.level().registryAccess().lookupOrThrow(MxtDatapackRegistries.RESOURCE);
        ResourceHolderData values = player.getData(MxtAttachments.RESOURCE_HOLDER);
        long gameTime = player.level().getGameTime();
        List<ResolvedBar> result = new ArrayList<>();

        for (Reference<ResourceBarDefinition> holder : bars.listElements().toList()) {
            Identifier id = holder.unwrapKey().map(ResourceKey::identifier).orElse(null);
            ResourceBarDefinition bar = holder.value();
            Identifier resourceId = HolderHelper.id(bar.resource());
            if (id == null || bar.context() != Context.SELF_HUD || !values.contains(resourceId)) continue;
            ResourceDefinition resource = bar.resource().value();

            double min = resource.min().evaluate(FormulaContext.EMPTY);
            double maximum = resource.max().evaluate(FormulaContext.EMPTY);
            double current = values.get(resourceId);
            if (!Double.isFinite(min) || !Double.isFinite(maximum) || !Double.isFinite(current) || maximum < min || maximum < 0.0D)
                continue;

            Audit audit = values.audit(resourceId);
            long changedAt = audit.lastChangedTick();
            long ticksSinceChanged = changedAt < 0L ? Long.MAX_VALUE : Math.max(0L, gameTime - changedAt);
            ResourceBarView view = new ResourceBarView(current, maximum, ticksSinceChanged, false);
            if (!bar.visibility().visible(view)) continue;
            result.add(new ResolvedBar(id, bar, current, min, maximum));
        }

        return result.stream()
                .sorted(Comparator.comparingInt((ResolvedBar bar) -> bar.definition().order()).thenComparing(ResolvedBar::id))
                .toList();
    }

    private static Position position(Minecraft minecraft, ResolvedBar bar, int offset) {
        int width = minecraft.getWindow().getGuiScaledWidth();
        int height = minecraft.getWindow().getGuiScaledHeight();
        int barWidth = bar.width();
        int barHeight = bar.height();
        int hotbarY = height - 47;
        Player player = minecraft.player;
        if (player != null) {
            if (player.getVehicle() instanceof LivingEntity vehicle)
                hotbarY -= 8 * (int) (vehicle.getMaxHealth() / 20.0F);
            if (player.isEyeInFluid(FluidTags.WATER) || player.getAirSupply() < player.getMaxAirSupply())
                hotbarY -= 8;
        }
        return switch (bar.definition().anchor()) {
            case ABOVE_HOTBAR -> new Position(width / 2 + 20, hotbarY - offset);
            case BELOW_HEALTH -> new Position(width / 2 - 91, height - 49 - offset);
            case TOP_LEFT_STACK -> new Position(4, 4 + offset);
            case TOP_RIGHT_STACK -> new Position(width - barWidth - 4, 4 + offset);
        };
    }

    private static void renderBar(GuiGraphicsExtractor graphics, Minecraft minecraft, ResolvedBar bar, Position position) {
        ResourceBarRenderer renderer = bar.definition().renderer();
        double progress = bar.progress();
        if (renderer instanceof Textured textured) {
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
    }

    private static void renderSegmented(GuiGraphicsExtractor graphics, Segmented renderer,
                                        double progress, Position position) {
        int segmentWidth = Math.max(1, (BAR_WIDTH - (renderer.segments() - 1) * renderer.gap()) / renderer.segments());
        int filled = (int) Math.round(progress * renderer.segments());
        int fullColor = color(renderer.fullColor(), DEFAULT_FILL);
        int emptyColor = color(renderer.emptyColor(), DEFAULT_EMPTY);
        for (int index = 0; index < renderer.segments(); index++) {
            int x = position.x() + index * (segmentWidth + renderer.gap());
            graphics.fill(x, position.y(), x + segmentWidth, position.y() + BAR_HEIGHT, index < filled ? fullColor : emptyColor);
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

    private record ResolvedBar(Identifier id, ResourceBarDefinition definition, double current, double minimum,
                               double maximum) {
        private double progress() {
            return this.maximum == this.minimum ? 1.0D : Math.max(0.0D, Math.min(1.0D, (this.current - this.minimum) / (this.maximum - this.minimum)));
        }

        private int width() {
            return switch (this.definition.renderer()) {
                case Textured textured -> textured.width();
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
