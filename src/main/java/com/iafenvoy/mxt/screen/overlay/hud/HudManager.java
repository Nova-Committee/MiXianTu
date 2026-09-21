package com.iafenvoy.mxt.screen.overlay.hud;

import com.iafenvoy.mxt.MiXianTu;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The registry of HUD entries, and the only thing the rest of the mod needs to know about the HUD.
 *
 * <p>This is the port of AxolotlClient's {@code HudManager} ("This implementation of Hud modules is based on
 * KronHUD", GPL-3.0). A module that wants a movable element registers it here; this class then draws it,
 * lets the edit screen drag it, and keeps its placement in the client config. Nothing in the mod is
 * registered yet - the framework is here, its first entries are not (see {@code research/26}).</p>
 *
 * <p>Registration happens on first use rather than at client setup, so an entry is constructed exactly when
 * its owning module is loaded and there is no separate "list of things to build" to keep in step.</p>
 */
@EventBusSubscriber(Dist.CLIENT)
public final class HudManager {
    private static final Map<String, HudEntry> REGISTRY = new LinkedHashMap<>();

    private HudManager() {
    }

    /**
     * Publishes a HUD entry. Returns the entry, so a module can register and keep the reference in one
     * statement. Registering two entries under one layout key is a programming mistake rather than a
     * configuration, so it fails loudly instead of letting one silently shadow the other.
     */
    public static <T extends HudEntry> T register(T entry) {
        HudEntry previous = REGISTRY.putIfAbsent(entry.layoutKey(), entry);
        if (previous != null)
            throw new IllegalStateException("Duplicate HUD layout key: " + entry.layoutKey());
        return entry;
    }

    /**
     * The entries the player may drag, in registration order. This is the list the edit screen works with:
     * an entry that is hidden, or one that computes its own position and therefore answers {@code false} to
     * {@link HudEntry#moveable()}, is drawn by {@link #render} but is not part of this list.
     */
    public static List<HudEntry> moveableEntries() {
        List<HudEntry> result = new ArrayList<>();
        for (HudEntry entry : REGISTRY.values())
            if (entry.visible() && entry.moveable()) result.add(entry);
        return Collections.unmodifiableList(result);
    }

    /**
     * The topmost entry under a point, or {@code null}. Later registrations are drawn on top, so they are
     * also the ones a click finds first.
     */
    public static HudEntry at(double pointX, double pointY) {
        HudEntry found = null;
        for (HudEntry entry : moveableEntries())
            if (entry.bounds().contains(pointX, pointY)) found = entry;
        return found;
    }

    /**
     * Draws every visible entry. This is the body of the GUI layer registered by {@link #registerLayer}, and
     * the reason entries keep drawing while the edit screen is open.
     *
     * <p>Each entry is told the window may have changed immediately before it draws. That is the only moment
     * the framework knows a frame is starting, and it lets an entry that has never been placed keep its
     * default position (which may depend on the window) while costing a stored one nothing but a re-clamp.
     * Doing it here rather than on a resize event also means an entry registered late, or one whose own size
     * changed, is in step on the very next frame.</p>
     *
     * <p>An entry that hands over {@link RenderBlock}s is drawn by {@link HudRenderer}; one that draws itself
     * is called directly. Either way the position comes from the framework, never from the entry's own
     * arithmetic.</p>
     */
    public static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        // The vanilla HUD is hidden by F1, and an entry has no way to know that; asking once here keeps every
        // entry from having to remember the same guard.
        if (Minecraft.getInstance().options.hideGui) return;
        for (HudEntry entry : REGISTRY.values()) {
            entry.refreshPlacement();
            if (!entry.visible()) continue;
            List<RenderBlock> blocks = entry.renderBlocks();
            if (blocks.isEmpty()) entry.render(graphics, deltaTracker);
            else if (entry.anchor().bottom()) HudRenderer.renderStanding(graphics, blocks, entry.x(), entry.y() + entry.layoutHeight());
            else HudRenderer.renderColumn(graphics, blocks, entry.x(), entry.y());
        }
    }

    /**
     * Whether the edit screen is open, asked by an entry that draws differently while it is.
     */
    public static boolean editMode() {
        return ScreenHolder.EDITED != null;
    }

    /**
     * Called after anything that changes a stored layout, so entries that keep their own copy of a position
     * (or that size themselves from it) have a place to catch up.
     */
    public static void layoutChanged() {
        for (HudEntry entry : REGISTRY.values()) entry.refreshPlacement();
    }

    static void setEditedEntry(HudEntry entry) {
        ScreenHolder.EDITED = entry;
    }

    /**
     * Opens the drag editor. The only entry point into it - a key binding and, later, a button in the config
     * screen both come through here.
     *
     * <p>Every entry is placed first. Normally {@link #render} has already done it for this frame, but the
     * framework's GUI layer only draws inside a world, so an editor opened from the main menu would otherwise
     * show its placeholders wherever the entries happened to start - the top-left corner. Refreshing here
     * costs one pass over a handful of entries and makes "what the editor draws" independent of where it was
     * opened from.</p>
     */
    public static void openEditor() {
        layoutChanged();
        Minecraft.getInstance().setScreen(new HudEditScreen());
    }

    @SubscribeEvent
    public static void registerLayer(RegisterGuiLayersEvent event) {
        event.registerAboveAll(Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "hud_framework"), HudManager::render);
    }

    /**
     * Holds the entry the edit screen is currently dragging.
     *
     * <p>It lives in its own class on purpose. The common path - a GUI layer asking whether the edit screen
     * is open, every frame - only ever reads this field, so the client-only edit screen class is not needed
     * to answer it. Without the split, loading {@link HudManager} would pull in a screen class at the
     * earliest moment a HUD entry is constructed.</p>
     */
    private static final class ScreenHolder {
        private static HudEntry EDITED;
    }
}
