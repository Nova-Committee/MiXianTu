package com.iafenvoy.mxt.render.overlay;
import com.iafenvoy.mxt.registry.MxtResourceKeys;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.attachment.AuraChunkData;
import com.iafenvoy.mxt.data.aura.AuraZone;
import com.iafenvoy.mxt.data.aura.AuraZone.Bar;
import com.iafenvoy.mxt.data.aura.AuraZone.ClientHud;
import com.iafenvoy.mxt.data.resource.ResourceBar.Anchor;
import com.iafenvoy.mxt.data.resourcebar.BuiltinResourceBarRenderers.Origins;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.runtime.world.AuraClientState;
import com.iafenvoy.mxt.runtime.world.AuraClientState.Snapshot;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
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
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal client view of the synchronized current-chunk aura attachment.
 */
@EventBusSubscriber(modid = MiXianTu.MOD_ID, value = Dist.CLIENT)
public enum AuraOverlay implements GuiLayer {
    INSTANCE;
    private static final int BAR_WIDTH = 71;

    @Override
    public void render(@NotNull GuiGraphicsExtractor graphics, @NotNull DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (minecraft.options.hideGui || player == null || minecraft.level == null) return;
        Snapshot snapshot = AuraClientState.current();
        ClientHud hud = minecraft.level.registryAccess().lookupOrThrow(MxtResourceKeys.AURA_ZONE)
                .getOptional(snapshot.source()).map(AuraZone::clientHud).orElse(ClientHud.NONE);
        AuraChunkData aura = minecraft.level.getChunkAt(player.blockPosition()).getData(MxtAttachments.AURA_CHUNK);
        List<AuraHudBar> bars = new ArrayList<>();
        hud.storedAura().ifPresent(definition -> bars.add(new AuraHudBar(definition,
                aura.initialized() ? aura.concentration() : snapshot.concentration())));
        hud.sensedConcentration().ifPresent(definition -> bars.add(new AuraHudBar(definition, snapshot.concentration())));
        Map<Anchor, Integer> offsets = new EnumMap<>(Anchor.class);
        bars.stream().sorted(Comparator.comparing((AuraHudBar bar) -> bar.definition().anchor())
                        .thenComparingInt(bar -> bar.definition().order()))
                .forEach(bar -> {
                    Anchor anchor = bar.definition().anchor();
                    int offset = offsets.computeIfAbsent(anchor, value -> ResourceBarOverlay.reservedSelfHudHeight(player, value));
                    Position position = position(minecraft, player, anchor, offset);
                    renderBar(graphics, bar.definition(), bar.value(), position.x(), position.y());
                    offsets.put(anchor, offset + 8);
                });
    }

    private static Position position(Minecraft minecraft, Player player, Anchor anchor, int offset) {
        int y = minecraft.getWindow().getGuiScaledHeight() - 47;
        if (player.getVehicle() instanceof LivingEntity vehicle)
            y -= 8 * (int) (vehicle.getMaxHealth() / 20.0F);
        if (player.isEyeInFluid(FluidTags.WATER) || player.getAirSupply() < player.getMaxAirSupply())
            y -= 8;
        int x = anchor == Anchor.LEFT ? minecraft.getWindow().getGuiScaledWidth() / 2 - 20 - BAR_WIDTH
                : minecraft.getWindow().getGuiScaledWidth() / 2 + 20;
        return new Position(x, y - offset);
    }

    private static void renderBar(GuiGraphicsExtractor graphics, Bar definition, double value, int x, int y) {
        float fill = (float) Math.clamp(value / definition.maximum(), 0.0D, 1.0D);
        if (definition.inverted()) fill = 1.0F - fill;
        int row = 8 + definition.barIndex() * 10;
        graphics.blit(RenderPipelines.GUI_TEXTURED, Origins.DEFAULT_TEXTURE, x, y, 0.0F, 0.0F, BAR_WIDTH, 5, 256, 256);
        graphics.blit(RenderPipelines.GUI_TEXTURED, Origins.DEFAULT_TEXTURE, x, y - 2, 0.0F, row, (int) (fill * BAR_WIDTH), 8, 256, 256);
        graphics.blit(RenderPipelines.GUI_TEXTURED, Origins.DEFAULT_TEXTURE, x - 10, y - 2, 73.0F, row, 8, 8, 256, 256);
    }

    @SubscribeEvent
    public static void register(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.HOTBAR, Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "aura"), INSTANCE);
    }

    private record AuraHudBar(Bar definition, double value) {
    }

    private record Position(int x, int y) {
    }
}
