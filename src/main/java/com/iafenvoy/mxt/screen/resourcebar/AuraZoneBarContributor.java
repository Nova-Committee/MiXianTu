package com.iafenvoy.mxt.screen.resourcebar;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.aura.AuraZone;
import com.iafenvoy.mxt.runtime.world.AuraClientState;
import com.iafenvoy.saturn.data.SpriteIcon;
import com.iafenvoy.saturn.data.resource.ResourceBar.Anchor;
import com.iafenvoy.saturn.data.resource.ResourceBar.ValueDisplay;
import com.iafenvoy.saturn.data.resourcebar.ResourceBarContext.Layout;
import com.iafenvoy.saturn.data.resourcebar.builtin.context.SelfHudContext;
import com.iafenvoy.saturn.data.resourcebar.builtin.renderdata.OriginsRenderData;
import com.iafenvoy.saturn.screen.resourcebar.ResourceBarContributor;
import com.iafenvoy.saturn.screen.resourcebar.ResourceBarRenderState;
import com.iafenvoy.saturn.util.MiscUtil;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * The two aura bars a zone may declare for the player: the concentration the zone stores for them and the
 * environmental template they are standing in. Both numbers are resolved on the client by the server, so they come
 * from {@link AuraClientState} instead of from a resource value.
 */
public final class AuraZoneBarContributor implements ResourceBarContributor {
    public static final AuraZoneBarContributor INSTANCE = new AuraZoneBarContributor();

    private AuraZoneBarContributor() {
    }

    @Override
    public void collect(LivingEntity entity, Layout layout, Anchor anchor, List<ResourceBarRenderState> bars) {
        if (layout != Layout.SELF_HUD) return;
        AuraClientState.Snapshot snapshot = AuraClientState.current();
        AuraZone.ClientHud hud = snapshot.source().map(Holder::value).map(AuraZone::clientHud).orElse(AuraZone.ClientHud.NONE);
        hud.storedAura().ifPresent(bar -> add(bars, "stored_aura", 0, bar, snapshot.actualConcentration(), resolvedMaximum(snapshot.actualMaximum(), bar.maximum()), anchor));
        hud.sensedConcentration().ifPresent(bar -> add(bars, "sensed_concentration", 1, bar, snapshot.environmentConcentration(), bar.maximum(), anchor));
    }

    private static void add(List<ResourceBarRenderState> bars, String id, int index, AuraZone.Bar definition, double current, double maximum, Anchor anchor) {
        if (definition.anchor() != anchor) return;
        if (!Double.isFinite(current) || !Double.isFinite(maximum) || maximum <= 0.0D) return;
        Identifier identifier = Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, id);
        bars.add(new ResourceBarRenderState(SelfHudContext.INSTANCE, definition.anchor(), definition.order(),
                identifier, index, current, 0.0D, maximum,
                new OriginsRenderData(SpriteIcon.of(OriginsRenderData.DEFAULT_TEXTURE, false), definition.barIndex(),
                        OptionalInt.of(definition.barIndex()), definition.inverted()),
                0, 0, Optional.of(Component.translatable("hud.mxt.resource_bar." + id)), ValueDisplay.NONE));
    }

    // A stored aura reports its own ceiling when it has one; the zone's number is the fallback for a pool without one.
    private static double resolvedMaximum(double dynamic, double fallback) {
        return MiscUtil.positive(dynamic) > 0.0D ? dynamic : fallback;
    }
}
