package com.iafenvoy.mxt.screen.overlay.resourcebar;

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
import com.iafenvoy.mxt.screen.overlay.hud.HudManager;
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
 * Answers which resource bars want to be drawn, in which order, and at what slot of their stack.
 *
 * <p>It is a plain utility, not a renderer and not a GUI layer: every part of the screen it describes is
 * drawn by the HUD framework's single renderer, from the two movable columns to the two fixed rows about the
 * entity being looked at. Keeping the gathering here - rather than in the entries - is what lets the
 * entries stay about placement: an entry asks for a list of bars and hands the resulting blocks to the
 * layout.</p>
 *
 * <p>Every layout is split by the bar's own {@code anchor} into a left and a right pass, so a bar declared
 * {@code anchor: right} genuinely draws on the right.</p>
 */
public final class ResourceBarOverlay {
    /** Clearance between an overlay row and the centre of the screen. */
    private static final int OVERLAY_GAP = 8;

    private ResourceBarOverlay() {
    }

    /**
     * The player's own bars of one column, top to bottom, with their slots already resolved.
     *
     * <p>Asked once per column per frame by its entry - once to work out the column's size before placing
     * it, once to draw it. The answer is built from attachments that are already on the client, so the
     * second call costs an allocation and nothing else - cheaper than deciding which of the two callers is
     * allowed to work from a frame-old answer.</p>
     */
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

    /**
     * The bars of one fixed row - about the entity being looked at - with their slots already resolved.
     */
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

    /**
     * Where a fixed row's column starts, measured from the middle of the screen outwards. Unchanged from the
     * original overlay: a row is as wide as its widest bar, and it is placed by the edge that faces the
     * middle.
     */
    public static int rowX(int screenWidth, Anchor side, int barWidth) {
        return side == Anchor.LEFT ? screenWidth / 2 - OVERLAY_GAP - barWidth : screenWidth / 2 + OVERLAY_GAP;
    }

    /**
     * Brings a sorted list of bars into the shape the layout wants: order resolved, slot left at zero.
     *
     * <p>The horizontal slot is zero because bars are left-aligned in their column. The vertical slot is zero
     * too, and that is the important half: the layout stacks blocks back to back by the heights they report,
     * so a bar's y position is decided in exactly one place. The original overlay instead accumulated a
     * running y here, and keeping both meant the same stack had two answers - the accumulated one was counted
     * again when the column's height was measured, which made every column twice as tall as it drew and left
     * the gap this was chasing.</p>
     */
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
            // A bar is declared on a value; the use gate belongs to the aura that value carries, and a value
            // without one is ungated.
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

    /**
     * Registers everything this class describes with the HUD framework, once.
     *
     * <p>Called from client setup rather than left to the first frame. The framework's own layer is only
     * rendered while a world is loaded, so an editor opened from the main menu - or from the pause menu of a
     * world that has not drawn a frame yet - would otherwise find an empty registry and show nothing at all,
     * which is exactly what it looks like when a feature is broken. Doing it here makes "the elements exist"
     * independent of where the player is standing when they open the editor.</p>
     */
    public static void registerEntries() {
        HudManager.register(new ResourceBarEntry("resource_bars.left", Anchor.LEFT));
        HudManager.register(new ResourceBarEntry("resource_bars.right", Anchor.RIGHT));
        HudManager.register(ResourceBarFixedEntry.target());
        HudManager.register(ResourceBarFixedEntry.boss());
    }
}

