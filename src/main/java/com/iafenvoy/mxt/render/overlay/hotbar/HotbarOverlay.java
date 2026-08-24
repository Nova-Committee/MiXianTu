package com.iafenvoy.mxt.render.overlay.hotbar;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.attachment.AbilityAttachment;
import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.data.ability.AbilityComponentState;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.render.overlay.hotbar.AbilityHotbarClient.ResolvedAbility;
import com.iafenvoy.mxt.render.overlay.hotbar.HotbarController.Mode;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.GuiLayer;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;

/**
 * Unified renderer for the ability and spirit-resource hotbars.
 */
@EventBusSubscriber(Dist.CLIENT)
public enum HotbarOverlay implements GuiLayer {
    INSTANCE;

    private static final int BOTTOM_OFFSET = 104;

    @Override
    public void render(@NotNull GuiGraphicsExtractor graphics, @NotNull DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (minecraft.options.hideGui || player == null || minecraft.level == null
                || HotbarController.mode() == Mode.NONE) return;

        List<HotbarEntry> entries = HotbarController.entries(player);
        if (entries.isEmpty()) return;
        int width = HotbarOverlayRenderer.width(entries.size());
        int x = minecraft.getWindow().getGuiScaledWidth() / 2 - width / 2;
        int y = minecraft.getWindow().getGuiScaledHeight() - BOTTOM_OFFSET;
        for (int index = 0; index < entries.size(); index++) {
            HotbarEntry entry = entries.get(index);
            entry.render(graphics, minecraft.font, player,
                    x + index * (HotbarEntry.SLOT_SIZE + HotbarEntry.SLOT_GAP), y, index,
                    HotbarController.isEntryActive(index));
        }

        if (HotbarController.mode() == Mode.ABILITY) {
            List<ResolvedAbility> abilities = AbilityHotbarClient.all(player);
            long gameTime = player.level().getGameTime();
            drawCastBar(graphics, minecraft, player, x, y - 10, abilities, gameTime);
        }
    }

    private static void drawCastBar(GuiGraphicsExtractor graphics, Minecraft minecraft, Player player,
                                    int x, int y, List<ResolvedAbility> abilities, long gameTime) {
        AbilityAttachment holder = player.getData(MxtAttachments.ABILITY_HOLDER);
        ResolvedAbility casting = abilities.stream().filter(value -> {
            Optional<Holder<Ability>> bound = holder.sources().keySet().stream().filter(ability -> ability.value() == value.definition()).findFirst();
            return bound.isPresent() && holder.componentState(bound.get(), "cast_ends_at")
                    .map(state -> state.value() < Double.MAX_VALUE && state.value() > gameTime).orElse(false);
        }).findFirst().orElse(null);
        if (casting == null) return;

        Optional<Holder<Ability>> bound = holder.sources().keySet().stream().filter(ability -> ability.value() == casting.definition()).findFirst();
        if (bound.isEmpty()) return;
        AbilityComponentState state = holder.componentState(bound.get(), "cast_ends_at").orElse(null);
        if (state == null) return;
        long end = Math.round(state.value());
        long start = state.changedAt();
        double progress = end <= start ? 1.0D : Math.max(0.0D, Math.min(1.0D, (gameTime - start) / (double) (end - start)));
        int barWidth = HotbarOverlayRenderer.width(abilities.size());
        graphics.fill(x, y, x + barWidth, y + 5, 0xCC10131D);
        graphics.fill(x, y, x + (int) (barWidth * progress), y + 5, 0xFF6DB8FF);
        graphics.text(minecraft.font, Component.translatable("gui.mxt.ability.casting", casting.id().getPath()),
                x, y - 9, 0xFFE0E5EF, false);
    }

    @SubscribeEvent
    public static void register(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.HOTBAR,
                Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "hotbar"), INSTANCE);
    }
}
