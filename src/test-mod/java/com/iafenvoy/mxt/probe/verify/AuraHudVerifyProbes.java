package com.iafenvoy.mxt.probe.verify;

import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.aura.AuraZone;
import com.iafenvoy.mxt.probe.Probe;
import com.iafenvoy.mxt.probe.ProbeContext;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.world.AuraClientState;
import com.iafenvoy.mxt.runtime.world.AuraPool;
import com.iafenvoy.mxt.screen.resourcebar.AuraZoneBarContributor;
import com.iafenvoy.saturn.data.resource.ResourceBar.Anchor;
import com.iafenvoy.saturn.data.resourcebar.ResourceBarContext.Layout;
import com.iafenvoy.saturn.data.resourcebar.builtin.renderdata.OriginsRenderData;
import com.iafenvoy.saturn.screen.resourcebar.ResourceBarRenderState;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The two bars a zone may declare, read through the contributor the library's columns ask: which anchor each bar
 * lands on, what it is called, and which numbers it carries. The client state is fed here, so nothing has to run a
 * client to check the answer.
 */
public final class AuraHudVerifyProbes {
    private AuraHudVerifyProbes() {
    }

    public static List<Probe> probes() {
        return List.of(Probe.of("verify/aura_hud", AuraHudVerifyProbes::auraHud));
    }

    private static void auraHud(ProbeContext context) {
        Holder<AuraZone> zone = context.require(MxtResourceKeys.AURA_ZONE, "overworld");
        Holder<Aura> aura = context.require(MxtResourceKeys.AURA, "spirit_power");
        // The stored bar reports the ceiling the pool itself carries; the zone number is only its fallback.
        AuraClientState.update(Optional.of(zone),
                Map.of(aura, new AuraPool(120.0D, 200.0D, 0.0D, 0.0D)),
                Map.of(aura, new AuraPool(60.0D, 120.0D, 0.0D, 0.0D)));
        // The snapshot is interpolated towards what was synced, so the numbers are read after a couple of ticks.
        context.after(2, () -> {
            try {
                AuraZone.ClientHud hud = zone.value().clientHud();
                List<ResourceBarRenderState> left = collect(context, Anchor.LEFT);
                List<ResourceBarRenderState> right = collect(context, Anchor.RIGHT);
                ResourceBarRenderState sensed = find(left, "mxt:sensed_concentration");
                ResourceBarRenderState stored = find(left, "mxt:stored_aura");
                context.check("the left column carries both aura bars", sensed != null && stored != null);
                context.expect("the right column stays empty", right.size(), 0);
                if (sensed != null) {
                    AuraZone.Bar definition = hud.sensedConcentration().orElseThrow();
                    context.expect("sensed bar order", sensed.order(), definition.order());
                    context.expect("sensed bar index", sensed.index(), 1);
                    context.expect("sensed bar uses the zone maximum", sensed.maximum(), definition.maximum());
                    context.expect("sensed bar label", sensed.name().orElse(null),
                            Component.translatable("hud.mxt.resource_bar.sensed_concentration"));
                    context.check("sensed bar reads the environment pool",
                            sensed.current() > 0.0D && sensed.current() <= definition.maximum());
                    context.expect("sensed bar texture row",
                            sensed.renderData() instanceof OriginsRenderData data ? data.barIndex() : -1,
                            definition.barIndex());
                }
                if (stored != null) {
                    AuraZone.Bar definition = hud.storedAura().orElseThrow();
                    context.expect("stored bar order", stored.order(), definition.order());
                    context.expect("stored bar index", stored.index(), 0);
                    // Both numbers interpolate from the empty pool with the same factor, so their ratio names the
                    // ceiling the bar picked: the pool's 200, not the zone's fallback.
                    context.check("stored bar prefers the pool ceiling over the zone number",
                            stored.current() > 0.0D
                                    && Math.abs(stored.maximum() / stored.current() - 200.0D / 120.0D) < 1e-9);
                    context.expect("stored bar label", stored.name().orElse(null),
                            Component.translatable("hud.mxt.resource_bar.stored_aura"));
                    context.check("stored bar reads the actual pool",
                            stored.current() > 0.0D && stored.current() <= 200.0D);
                    context.check("stored bar follows the zone inverted flag",
                            stored.renderData() instanceof OriginsRenderData data && data.inverted() == definition.inverted());
                }
                AuraClientState.update(Optional.empty(), Map.of(), Map.of());
                context.expect("no zone draws no bars", collect(context, Anchor.LEFT).size(), 0);
                context.expect("no zone draws no right column either", collect(context, Anchor.RIGHT).size(), 0);
            } finally {
                AuraClientState.update(Optional.empty(), Map.of(), Map.of());
                context.done();
            }
        });
    }

    private static List<ResourceBarRenderState> collect(ProbeContext context, Anchor anchor) {
        List<ResourceBarRenderState> bars = new ArrayList<>();
        AuraZoneBarContributor.INSTANCE.collect(context.player(), Layout.SELF_HUD, anchor, bars);
        return bars;
    }

    private static @Nullable ResourceBarRenderState find(List<ResourceBarRenderState> bars, String id) {
        for (ResourceBarRenderState state : bars) if (state.id().toString().equals(id)) return state;
        return null;
    }
}
