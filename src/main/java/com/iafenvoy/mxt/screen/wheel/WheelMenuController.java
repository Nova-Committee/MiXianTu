package com.iafenvoy.mxt.screen.wheel;

import com.iafenvoy.mxt.api.WheelMenuEntry;
import com.iafenvoy.mxt.config.MxtClientConfig;
import com.iafenvoy.mxt.registry.MxtKeyMappings;
import com.iafenvoy.mxt.runtime.wheel.WheelSource;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Opens and closes the wheel and spends what it holds: the wheel key only chooses, the use key spends the pointed
 * cell (or the remembered one while the wheel is down), the twelve slot keys each arm and spend their cell of the
 * page that is up, and the two switch keys move between pages. A wheel with nothing on it anywhere does not open,
 * and opening always lands on the first page.
 *
 * <p>The page is followed by everything that draws the ring, while the <em>chosen cell</em> is a number that
 * survives its page going away - see {@link WheelSelectionState}. The switch keys therefore work with the wheel
 * up or down; when they are pressed with another screen open they stay silent, like every other key here.</p>
 *
 * <p>All keys are polled raw ({@code InputConstants}/GLFW), because {@code setScreen} releases every mapping
 * and {@code grabMouse}'s {@code setAll()} would re-press the use key and double-fire; edges are once a tick.
 * The slot and switch keys are polled here for the same reason, even though they are registered in
 * {@link MxtKeyMappings} with the rest: a {@code KeyMapping}'s own edge would fire a cast that nobody pressed.</p>
 *
 * <p>A key that was pressed and could do nothing answers on the action bar ({@code WheelMenuController#notice})
 * in two cases - the wheel is empty on every page, or nothing was ever chosen. A cell whose entry is gone on
 * purpose stays silent: the wheel grid already draws it as empty, slot keys included.</p>
 */
@EventBusSubscriber(Dist.CLIENT)
public final class WheelMenuController {
    private static @Nullable WheelMenuScreen open;
    private static boolean lastDown;
    private static boolean lastUseDown;
    private static boolean lastPreviousDown;
    private static boolean lastNextDown;
    /**
     * The last state of each slot key, indexed like {@link MxtKeyMappings#WHEEL_SLOTS} - that is, by cell.
     * All twelve are unbound by default, so an unbound one simply never goes down.
     */
    private static final boolean[] slotDown = new boolean[MxtKeyMappings.WHEEL_SLOTS.size()];

    private WheelMenuController() {
    }

    static void usePointed(WheelSelection.Method method) {
        WheelMenuScreen screen = open;
        if (screen == null) return;
        // Null for an empty cell: nothing is spent and the wheel stays up. Silent on purpose - the cell is
        // drawn as empty right there, so a line about it would be answering a question nobody asked.
        WheelSelection selection = screen.selection(method);
        if (selection != null) selection.entry().onSelected(selection);
    }

    /** Called when something else takes the screen down; the controller must not touch the new screen. */
    static void screenRemoved(WheelMenuScreen screen) {
        if (open == screen) open = null;
    }

    private static void open(Minecraft minecraft) {
        if (minecraft.screen != null) return;
        // Whatever was up while the wheel was down, opening lands on the first page: that is the one the player
        // arranged, and the ones read from their gear are one keypress away.
        WheelSelectionState.firstPage();
        WheelSelectionState.refresh(minecraft.player);
        // A wheel with nothing anywhere is not opened: there would be nothing to point at. Say so rather than
        // do nothing. The test spans every page, because the first page being empty must not hide the rest.
        if (WheelMenuContent.hasAnyEntry(WheelSelectionState.pages())) {
            WheelMenuScreen screen = new WheelMenuScreen();
            open = screen;
            minecraft.setScreen(screen);
            return;
        }
        notice(Component.translatable("actionbar.mxt.wheel.empty"));
    }

    private static void close() {
        if (open == null) return;
        // Forgotten before the screen is taken down, so the removal callback is a no-op.
        open = null;
        Minecraft.getInstance().setScreen(null);
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        // Closed from the outside (escape, another screen): drop the reference without closing anything.
        if (open != null && minecraft.screen != open) open = null;
        // No world to show the wheel over - a disconnect while it was open.
        if (open != null && minecraft.level == null) {
            close();
            return;
        }
        // Recorded before this tick's edges: letting go must keep whatever the pointer was last on.
        if (open != null) WheelSelectionState.selectSector(open.pointedSector());
        // Before this tick's edges act on it, and before the HUD cell draws it.
        WheelSelectionState.refresh(minecraft.player);

        // The page first, so a tick that switches and spends does the whole thing on the page it switched to.
        usePageKeys(minecraft);

        // The use key next, so a tick that presses one key and releases the other spends what was aimed at.
        boolean useDown = keyDown(minecraft, MxtKeyMappings.WHEEL_USE);
        if (useDown != lastUseDown) {
            lastUseDown = useDown;
            if (useDown) use(minecraft);
        }
        // After it: a tick that presses both spends the cell that was armed, then the slot key's own one.
        useSlotKeys(minecraft);
        boolean down = keyDown(minecraft, MxtKeyMappings.WHEEL);
        if (down == lastDown) return;
        lastDown = down;
        if (down) {
            if (open == null) open(minecraft);
                // The second press in toggle mode takes the wheel down, as letting go does in hold mode.
            else if (!hold()) close();
        } else if (open != null && hold()) {
            close();
        }
    }

    /**
     * The two switch keys: one page back, one page forward, wrapping at both ends. The page is only what the
     * ring draws and what the slot keys address - the chosen cell is not moved by it, so switching cannot drop
     * what the use key would spend.
     */
    private static void usePageKeys(Minecraft minecraft) {
        // Sampled even while another screen owns the keys, so a key held through a screen change cannot turn
        // into a press the moment it closes; only acting is gated, exactly as for the slot keys below.
        boolean live = minecraft.player != null && (minecraft.screen == null || open != null);
        boolean previous = keyDown(minecraft, MxtKeyMappings.WHEEL_PREVIOUS);
        boolean next = keyDown(minecraft, MxtKeyMappings.WHEEL_NEXT);
        boolean back = previous && !lastPreviousDown;
        boolean forward = next && !lastNextDown;
        lastPreviousDown = previous;
        lastNextDown = next;
        // Both at once is a tie, and neither decides nothing; only one of them moves the page.
        if (!live || back == forward) return;
        WheelSelectionState.stepPage(back ? -1 : 1);
        // Re-resolved right away: the HUD grid draws this tick, and the ring would otherwise show the old page.
        WheelSelectionState.refresh(minecraft.player);
        notice(Component.translatable("actionbar.mxt.wheel.page",
                WheelSelectionState.page() + 1, WheelSelectionState.pages().size(),
                WheelSelectionState.pageSource().displayName()));
    }

    /**
     * The twelve slot keys: each arms its own cell of the page that is up and spends it in the same breath, so
     * one key does what "point at that cell, then press the use key" does - a keyboard hotbar laid over the
     * wheel, and the way a key can reach a cell that is not on the page currently drawn.
     */
    private static void useSlotKeys(Minecraft minecraft) {
        List<MxtKeyMappings.KeyMappingHolder> slots = MxtKeyMappings.WHEEL_SLOTS;
        // Edges are sampled even while another screen owns the keys, so a key held through a screen change
        // cannot turn into a press the moment it closes; only acting is gated. Raw polling is what keeps a
        // slot key from double-firing on the false press `grabMouse`'s `setAll()` invents (see the class doc).
        boolean live = minecraft.player != null && (minecraft.screen == null || open != null);
        for (int sector = 0; sector < slots.size(); sector++) {
            boolean down = keyDown(minecraft, slots.get(sector));
            boolean pressed = down && !slotDown[sector];
            slotDown[sector] = down;
            if (pressed && live) useSlotKey(minecraft, sector);
        }
    }

    /**
     * Arms one cell of the page that is up and spends it; an empty one is silent, and a press is never sent for
     * nothing.
     */
    private static void useSlotKey(Minecraft minecraft, int sector) {
        int number = WheelSelectionState.numberAt(sector);
        // Read before it is chosen, so the entry and the source that trigger is sent with are the same cell.
        WheelMenuEntry entry = WheelSelectionState.entry(number);
        if (entry == null) return;
        WheelSource source = WheelMenuContent.source(WheelSelectionState.pages(), number);
        WheelSelectionState.selectSector(sector);
        entry.onSelected(new WheelSelection(source, number, entry, WheelSelection.Method.KEY));
    }

    /** The use key went down: spend the pointed cell if the wheel is up, the remembered one if it is not. */
    private static void use(Minecraft minecraft) {
        if (open != null) {
            usePointed(WheelSelection.Method.KEY);
            return;
        }
        // Another screen owns the key: typing a "v" in chat must not cast anything, nor complain about it.
        if (minecraft.screen != null) return;
        WheelSelection selection = remembered();
        if (selection != null) {
            selection.entry().onSelected(selection);
            return;
        }
        // Only "nothing was ever picked" is worth an answer. A cell that was picked and no longer holds
        // anything (empty, deleted, un-granted) is a normal state, and the wheel grid already shows it.
        if (WheelSelectionState.number() < 0)
            notice(Component.translatable("actionbar.mxt.wheel.no_selection",
                    MxtKeyMappings.WHEEL.get().getTranslatedKeyMessage()));
    }

    /**
     * One line on the action bar, for a key that was pressed and could do nothing - or, for a switch, which page
     * it moved to. Client-side on purpose: this is feedback about the local player's own HUD, and the server never
     * hears about a key that did nothing. A key pressed while another screen is open stays silent - that is
     * typing, not a request.
     */
    private static void notice(Component message) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) minecraft.gui.setOverlayMessage(message, false);
    }

    /**
     * The remembered cell as a selection, or {@code null} when there is nothing to spend. Read from what this
     * tick resolved, so the use key and the HUD cell that announces it agree by construction - including the
     * fallback that applies when the chosen number points past the pages that exist.
     */
    private static @Nullable WheelSelection remembered() {
        WheelMenuEntry entry = WheelSelectionState.selected();
        if (entry == null) return null;
        return new WheelSelection(WheelSelectionState.selectedSource(), WheelSelectionState.effective(),
                entry, WheelSelection.Method.KEY);
    }

    /** A bound key's state straight from the input device; an unbound key is never down. */
    private static boolean keyDown(Minecraft minecraft, MxtKeyMappings.KeyMappingHolder holder) {
        InputConstants.Key bound = holder.get().getKey();
        int value = bound.getValue();
        if (value == InputConstants.UNKNOWN.getValue()) return false;
        if (bound.getType() == InputConstants.Type.MOUSE)
            return GLFW.glfwGetMouseButton(minecraft.getWindow().handle(), value) == GLFW.GLFW_PRESS;
        return InputConstants.isKeyDown(minecraft.getWindow(), value);
    }

    private static boolean hold() {
        return MxtClientConfig.INSTANCE.wheel.mode.getValue() == MxtClientConfig.WheelMode.HOLD;
    }
}
