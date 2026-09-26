package com.iafenvoy.mxt.screen.hud;

import com.iafenvoy.mxt.MiXianTu;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

import java.util.*;

/**
 * The registry of HUD entries, and the only thing the rest of the mod needs to know about the HUD: modules
 * register here, this draws them, the edit screen drags them and the layout file stores their placement.
 * Registration happens on first use, so an entry is built exactly when its owning module is loaded.
 */
@EventBusSubscriber(Dist.CLIENT)
public final class HudManager {
    private static final Map<String, HudEntry> REGISTRY = new LinkedHashMap<>();

    private HudManager() {
    }

    // Registering two entries under one layout key is a programming mistake rather than a configuration, so
    // it fails loudly instead of letting one silently shadow the other.
    public static <T extends HudEntry> T register(T entry) {
        HudEntry previous = REGISTRY.putIfAbsent(entry.layoutKey(), entry);
        if (previous != null)
            throw new IllegalStateException("Duplicate HUD layout key: " + entry.layoutKey());
        return entry;
    }

    // Every element the editor may offer, hidden ones included: the editor draws those faintly so their checkbox
    // can turn them back on, which a hidden element filtered out here could never do. An element that is not
    // movable stays out - it draws, but there is nothing to place.
    public static List<HudEntry> moveableEntries() {
        List<HudEntry> result = new ArrayList<>();
        for (HudEntry entry : REGISTRY.values())
            if (entry.moveable()) result.add(entry);
        return Collections.unmodifiableList(result);
    }

    // Later registrations are drawn on top, so the last hit is also the one the player sees on top.
    public static HudEntry at(double pointX, double pointY) {
        HudEntry found = null;
        for (HudEntry entry : moveableEntries())
            if (entry.bounds().contains(pointX, pointY)) found = entry;
        return found;
    }

    // Each entry is told the window may have changed immediately before it draws. That is the only moment the
    // framework knows a frame is starting, and doing it here rather than on a resize event also puts an entry
    // registered late - or one whose own size changed - in step on the very next frame.
    public static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        // The vanilla HUD is hidden by F1, and an entry has no way to know that; asking once here keeps every
        // entry from having to remember the same guard.
        if (Minecraft.getInstance().options.hideGui) return;
        for (HudEntry entry : REGISTRY.values()) {
            entry.refreshPlacement();
            if (!entry.visible()) continue;
            List<RenderBlock> blocks = entry.renderBlocks();
            if (blocks.isEmpty()) entry.render(graphics, deltaTracker);
            else if (entry.anchor().atBottom())
                HudRenderer.renderStanding(graphics, blocks, entry.x(), entry.y() + entry.layoutHeight());
            else HudRenderer.renderColumn(graphics, blocks, entry.x(), entry.y());
        }
    }

    public static void layoutChanged() {
        for (HudEntry entry : REGISTRY.values()) entry.refreshPlacement();
    }

    // Refreshing first is what makes the editor independent of where it was opened from: the framework's GUI
    // layer only draws inside a world, so an editor opened from the main menu would otherwise show its
    // placeholders wherever the entries happened to start, in the top-left corner.
    public static void openEditor() {
        layoutChanged();
        Minecraft.getInstance().setScreen(new HudEditScreen());
    }

    @SubscribeEvent
    public static void registerLayer(RegisterGuiLayersEvent event) {
        event.registerAboveAll(Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "hud_framework"), HudManager::render);
    }
}
