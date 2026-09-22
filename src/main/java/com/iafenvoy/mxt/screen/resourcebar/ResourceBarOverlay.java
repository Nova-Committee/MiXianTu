package com.iafenvoy.mxt.screen.resourcebar;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.aura.Aura;
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
import com.iafenvoy.mxt.runtime.aura.AuraLookup;
import com.iafenvoy.mxt.runtime.resource.ResourceUseService;
import com.iafenvoy.mxt.runtime.world.AuraClientState;
import com.iafenvoy.mxt.runtime.world.AuraClientState.Snapshot;
import com.iafenvoy.mxt.screen.hud.HudManager;
import com.iafenvoy.mxt.util.HolderHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.Holder.Reference;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Answers which resource bars want to be drawn, in which order, and at what slot of their stack; everything it
 * describes is drawn by the HUD framework's single renderer. Gathering here rather than in the entries is what
 * lets the entries stay about placement, and bars are split by their own anchor into a left and a right pass.
 */
public final class ResourceBarOverlay {
    private static final int OVERLAY_GAP = 8;

    private ResourceBarOverlay() {
    }

    // Asked once per column per frame - once to size it, once to draw it. The answer is built from attachments
    // already on the client, so the second call costs an allocation and not a second source of truth.
    public static List<ResourceBarRenderState> column(Anchor anchor) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null || minecraft.level == null) return List.of();

        Registry<Resource> resources = player.level().registryAccess().lookupOrThrow(MxtResourceKeys.RESOURCE);
        List<ResourceBarRenderState> collected = new ArrayList<>();
        collectResources(collected, resources, player, Layout.SELF_HUD, anchor);
        collectAuraHud(collected, player, anchor);
        return stack(sort(collected));
    }

    public static List<ResourceBarRenderState> row(LivingEntity target, Layout layout) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return List.of();
        Registry<Resource> resources = target.level().registryAccess().lookupOrThrow(MxtResourceKeys.RESOURCE);
        List<ResourceBarRenderState> collected = new ArrayList<>();
        for (Anchor anchor : Anchor.values()) {
            collectResources(collected, resources, target, layout, anchor);
        }
        return stack(sort(collected));
    }

    // A row is as wide as its widest bar and is placed by the edge that faces the middle of the screen.
    public static int rowX(int screenWidth, Anchor side, int barWidth) {
        return side == Anchor.LEFT ? screenWidth / 2 - OVERLAY_GAP - barWidth : screenWidth / 2 + OVERLAY_GAP;
    }

    // Slots are zeroed here on purpose: the layout stacks blocks by the heights they report, so a bar's y has
    // exactly one answer. The original accumulated a running y here as well, which double-counted the height.
    private static List<ResourceBarRenderState> stack(List<ResourceBarRenderState> collected) {
        List<ResourceBarRenderState> stacked = new ArrayList<>(collected.size());
        for (ResourceBarRenderState state : collected) stacked.add(state.at(0, 0));
        return stacked;
    }

    private static List<ResourceBarRenderState> sort(List<ResourceBarRenderState> collected) {
        collected.sort(Comparator.comparingInt(ResourceBarRenderState::order)
                .thenComparing(state -> state.id().toString())
                .thenComparingInt(ResourceBarRenderState::index));
        return collected;
    }

    private static void collectResources(List<ResourceBarRenderState> result, Registry<Resource> resources,
                                         LivingEntity entity, Layout layout, Anchor anchor) {
        long gameTime = entity.level().getGameTime();
        for (Reference<Resource> resource : resources.listElements().toList()) {
            // A bar is declared on a value; the use gate belongs to the aura that value carries (no aura = ungated).
            Holder<Aura> aura = AuraLookup.holder(entity, resource).orElse(null);
            if (aura != null && !ResourceUseService.canUse(entity, aura)) continue;
            Identifier id = HolderHelper.idOrNull(resource);
            if (id == null) continue;
            List<ResourceBar> definitions = resource.value().bars();
            for (int index = 0; index < definitions.size(); index++) {
                ResourceBar bar = definitions.get(index);
                if (bar.context().layout() != layout || bar.anchor() != anchor) continue;
                Optional<Values> extracted = bar.context().extract(entity, resource);
                if (extracted.isEmpty()) continue;
                Values values = extracted.get();
                double minimum = values.minimum();
                double maximum = bar.maximum().orElse(values.maximum());
                double current = values.current();
                if (!validValues(minimum, maximum, current)) continue;
                long changedAt = values.lastChangedTick();
                long elapsed = changedAt < 0L ? Long.MAX_VALUE : Math.max(0L, gameTime - changedAt);
                if (!bar.visibility().visible(new ResourceBarView(current, minimum, maximum, elapsed, entity != Minecraft.getInstance().player)))
                    continue;
                result.add(new ResourceBarRenderState(
                        bar.context(), bar.anchor(), bar.order(), id, index, current, minimum, maximum, bar.renderer(),
                        0, 0, Optional.of(bar.context().name(id)), bar.valueDisplay()));
            }
        }
    }

    private static void collectAuraHud(List<ResourceBarRenderState> result, Player player, Anchor anchor) {
        Snapshot snapshot = AuraClientState.current();
        ClientHud hud = player.level().registryAccess().lookupOrThrow(MxtResourceKeys.AURA_ZONE)
                .getOptional(snapshot.source()).map(AuraZone::clientHud).orElse(ClientHud.NONE);
        hud.storedAura().ifPresent(bar -> addAuraEntry(result, "stored_aura", 0, bar, snapshot.actualConcentration(),
                resolvedMaximum(snapshot.actualMaximum(), bar.maximum()), anchor));
        hud.sensedConcentration().ifPresent(bar -> addAuraEntry(result, "sensed_concentration", 1, bar,
                snapshot.environmentConcentration(), bar.maximum(), anchor));
    }

    private static void addAuraEntry(List<ResourceBarRenderState> result, String id, int index, Bar definition,
                                     double current, double maximum, Anchor anchor) {
        if (definition.anchor() != anchor) return;
        if (!Double.isFinite(current) || !Double.isFinite(maximum) || maximum <= 0.0D) return;
        Identifier identifier = Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, id);
        String key = id.equals("stored_aura") ? "hud.mxt.resource_bar.stored_aura" : "hud.mxt.resource_bar.sensed_concentration";
        result.add(new ResourceBarRenderState(SelfHudContext.INSTANCE, definition.anchor(),
                definition.order(), identifier, index, current, 0.0D, maximum,
                new OriginsRenderData(
                        OriginsRenderData.DEFAULT_TEXTURE,
                        definition.barIndex(), Optional.of(definition.barIndex()), definition.inverted()),
                0, 0, Optional.of(Component.translatable(key)), ValueDisplay.NONE));
    }

    private static boolean validValues(double minimum, double maximum, double current) {
        return Double.isFinite(minimum) && Double.isFinite(maximum) && Double.isFinite(current)
                && maximum >= minimum && maximum >= 0.0D;
    }

    private static double resolvedMaximum(double dynamic, double fallback) {
        return Double.isFinite(dynamic) && dynamic > 0.0D ? dynamic : fallback;
    }

    // Called from client setup, not left to the first frame: the framework's layer only renders inside a world,
    // so an editor opened from the main menu would otherwise find an empty registry and show nothing at all.
    public static void registerEntries() {
        HudManager.register(new ResourceBarEntry("resource_bars.left", Anchor.LEFT));
        HudManager.register(new ResourceBarEntry("resource_bars.right", Anchor.RIGHT));
        HudManager.register(ResourceBarFixedEntry.target());
        HudManager.register(ResourceBarFixedEntry.boss());
    }
}

