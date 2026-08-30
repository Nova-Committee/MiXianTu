package com.iafenvoy.mxt.render.overlay.resourcebar;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.aura.AuraZone;
import com.iafenvoy.mxt.data.aura.AuraZone.Bar;
import com.iafenvoy.mxt.data.aura.AuraZone.ClientHud;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.data.resource.ResourceBar;
import com.iafenvoy.mxt.data.resource.ResourceBar.Anchor;
import com.iafenvoy.mxt.data.resource.ResourceBar.ValueDisplay;
import com.iafenvoy.mxt.data.resourcebar.ResourceBarContext.Layout;
import com.iafenvoy.mxt.data.resourcebar.ResourceBarContext.Values;
import com.iafenvoy.mxt.data.resourcebar.ResourceBarView;
import com.iafenvoy.mxt.data.resourcebar.builtin.context.SelfHudContext;
import com.iafenvoy.mxt.data.resourcebar.builtin.renderdata.OriginsRenderData;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.render.overlay.resourcebar.ResourceBarRenderer.Context;
import com.iafenvoy.mxt.runtime.world.AuraClientState;
import com.iafenvoy.mxt.runtime.world.AuraClientState.Snapshot;
import com.iafenvoy.mxt.util.HolderHelper;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.Holder.Reference;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
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
import java.util.Map;
import java.util.Optional;

/**
 * The single client overlay for data-driven resources and environment-aura HUD rows.
 */
@EventBusSubscriber(Dist.CLIENT)
public enum ResourceBarOverlay implements GuiLayer {
    INSTANCE;

    private static final int BAR_GAP = 0;

    @Override
    public void render(@NotNull GuiGraphicsExtractor graphics, @NotNull DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (minecraft.options.hideGui || player == null || minecraft.level == null) return;

        Map<LayoutKey, Integer> offsets = new HashMap<>();
        for (ResourceBarRenderState state : collect(player)) {
            LayoutKey key = new LayoutKey(state.context().layout(), state.anchor());
            int offset = offsets.getOrDefault(key, 0);
            Position position = position(minecraft, player, state, offset);
            ResourceBarRendererDispatcher.render(new Context(graphics, minecraft, state, position.x(), position.y()));
            offsets.put(key, offset + state.renderData().height() + BAR_GAP);
        }
    }

    private static List<ResourceBarRenderState> collect(Player player) {
        Registry<Resource> resources = player.level().registryAccess().lookupOrThrow(MxtResourceKeys.RESOURCE);
        List<ResourceBarRenderState> result = new ArrayList<>();
        collectResources(result, resources, player, Layout.SELF_HUD);
        if (Minecraft.getInstance().crosshairPickEntity instanceof LivingEntity target) {
            collectResources(result, resources, target, Layout.TARGET_OVERLAY);
            collectResources(result, resources, target, Layout.BOSS_OVERLAY);
        }
        collectAuraHud(result, player);
        return result.stream().sorted(Comparator.comparing((ResourceBarRenderState state) -> state.context().layout())
                .thenComparing(ResourceBarRenderState::anchor)
                .thenComparingInt(ResourceBarRenderState::order)
                .thenComparing(state -> state.id().toString())
                .thenComparingInt(ResourceBarRenderState::index)).toList();
    }

    private static void collectResources(List<ResourceBarRenderState> result, Registry<Resource> resources,
                                         LivingEntity entity, Layout layout) {
        long gameTime = entity.level().getGameTime();
        for (Reference<Resource> resource : resources.listElements().toList()) {
            Identifier id = HolderHelper.idOrNull(resource);
            if (id == null) continue;
            List<ResourceBar> definitions = resource.value().bars();
            for (int index = 0; index < definitions.size(); index++) {
                ResourceBar bar = definitions.get(index);
                if (bar.context().layout() != layout) continue;
                Optional<Values> extracted = bar.context().extract(entity, resource);
                if (extracted.isEmpty()) continue;
                Values values = extracted.get();
                double minimum = values.minimum();
                double maximum = values.maximum();
                double current = values.current();
                if (!validValues(minimum, maximum, current)) continue;
                long changedAt = values.lastChangedTick();
                long elapsed = changedAt < 0L ? Long.MAX_VALUE : Math.max(0L, gameTime - changedAt);
                if (!bar.visibility().visible(new ResourceBarView(current, minimum, maximum, elapsed, entity != Minecraft.getInstance().player)))
                    continue;
                result.add(new ResourceBarRenderState(
                        bar.context(), bar.anchor(), bar.order(), id, index, current, minimum, maximum, bar.renderer(),
                        Optional.of(bar.context().name(id)), bar.valueDisplay()));
            }
        }
    }

    private static void collectAuraHud(List<ResourceBarRenderState> result, Player player) {
        Snapshot snapshot = AuraClientState.current();
        ClientHud hud = player.level().registryAccess().lookupOrThrow(MxtResourceKeys.AURA_ZONE)
                .getOptional(snapshot.source()).map(AuraZone::clientHud).orElse(ClientHud.NONE);
        hud.storedAura().ifPresent(bar -> addAuraEntry(result, "stored_aura", 0, bar, snapshot.actualConcentration(),
                resolvedMaximum(snapshot.actualMaximum(), bar.maximum())));
        hud.sensedConcentration().ifPresent(bar -> addAuraEntry(result, "sensed_concentration", 1, bar,
                snapshot.environmentConcentration(), bar.maximum()));
    }

    private static void addAuraEntry(List<ResourceBarRenderState> result, String id, int index, Bar definition,
                                     double current, double maximum) {
        if (!Double.isFinite(current) || !Double.isFinite(maximum) || maximum <= 0.0D) return;
        Identifier identifier = Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, id);
        String key = id.equals("stored_aura") ? "hud.mxt.resource_bar.stored_aura" : "hud.mxt.resource_bar.sensed_concentration";
        result.add(new ResourceBarRenderState(SelfHudContext.INSTANCE, definition.anchor(),
                definition.order(), identifier, index, current, 0.0D, maximum,
                new OriginsRenderData(
                        OriginsRenderData.DEFAULT_TEXTURE,
                        definition.barIndex(), Optional.of(definition.barIndex()), definition.inverted()),
                Optional.of(Component.translatable(key)), ValueDisplay.NONE));
    }

    private static boolean validValues(double minimum, double maximum, double current) {
        return Double.isFinite(minimum) && Double.isFinite(maximum) && Double.isFinite(current)
                && maximum >= minimum && maximum >= 0.0D;
    }

    private static double resolvedMaximum(double dynamic, double fallback) {
        return Double.isFinite(dynamic) && dynamic > 0.0D ? dynamic : fallback;
    }

    private static Position position(Minecraft minecraft, Player player, ResourceBarRenderState state, int offset) {
        int width = minecraft.getWindow().getGuiScaledWidth();
        if (state.context().layout() == Layout.TARGET_OVERLAY)
            return overlayPosition(width, 16 + offset, state.renderData().width(), state.anchor());
        if (state.context().layout() == Layout.BOSS_OVERLAY)
            return overlayPosition(width, 48 + offset, state.renderData().width(), state.anchor());
        int y = minecraft.getWindow().getGuiScaledHeight() - 47;
        if (player.getVehicle() instanceof LivingEntity vehicle)
            y -= 8 * (int) (vehicle.getMaxHealth() / 20.0F);
        if (player.isEyeInFluid(FluidTags.WATER) || player.getAirSupply() < player.getMaxAirSupply()) y -= 8;
        int x;
        x = state.anchor() == Anchor.LEFT ? width / 2 - 20 - state.renderData().width() : width / 2 + 20;
        return new Position(x, y - offset);
    }

    private static Position overlayPosition(int width, int y, int barWidth, Anchor anchor) {
        return new Position(anchor == Anchor.LEFT ? width / 2 - 8 - barWidth : width / 2 + 8, y);
    }

    @SubscribeEvent
    public static void registerOverlay(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.HOTBAR, Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "resource_bars"), INSTANCE);
    }

    private record LayoutKey(Layout layout, Anchor anchor) {
    }

    private record Position(int x, int y) {
    }
}
