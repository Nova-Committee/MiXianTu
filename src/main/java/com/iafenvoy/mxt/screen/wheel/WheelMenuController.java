package com.iafenvoy.mxt.screen.wheel;

import com.iafenvoy.mxt.runtime.wheel.WheelSourceTypes;
import com.iafenvoy.mxt.api.WheelMenuEntry;
import com.iafenvoy.mxt.config.MxtClientConfig;
import com.iafenvoy.mxt.registry.MxtKeyMappings;
import com.iafenvoy.mxt.api.WheelSource;
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
 * Opens and closes the wheel and spends what it holds: the wheel key chooses, the use key spends the pointed cell
 * (or the remembered one while it is down), the slot keys spend their own cell, the switch keys page. Keys are
 * polled raw: {@code setScreen} releases every mapping and {@code grabMouse}'s {@code setAll()} re-presses use.
 */
@EventBusSubscriber(Dist.CLIENT)
public final class WheelMenuController {
    private static @Nullable WheelMenuScreen open;
    private static boolean lastDown;
    private static boolean lastUseDown;
    private static boolean lastPreviousDown;
    private static boolean lastNextDown;
    // Last state of each slot key, indexed by cell. All twelve are unbound by default, so an unbound one never
    // goes down.
    private static final boolean[] slotDown = new boolean[MxtKeyMappings.WHEEL_SLOTS.size()];

    private WheelMenuController() {
    }

    static void usePointed(WheelSelection.Method method) {
        WheelMenuScreen screen = open;
        if (screen == null) return;
        // Null for an empty cell: nothing is spent, the wheel stays up, and the cell is drawn as empty anyway.
        WheelSelection selection = screen.selection(method);
        if (selection != null) selection.entry().onSelected(selection);
    }

    // Something else took the screen down; the controller must not touch the new screen.
    static void screenRemoved(WheelMenuScreen screen) {
        if (open == screen) open = null;
    }

    // Opened by an item rather than by the wheel key: the page named is the one the item is about, and the wheel
    // stays up the way the toggle mode leaves it, since no key is being held down. An item whose page holds
    // nothing says so with its own line - the wheel key's complaint would be about the wrong thing.
    public static void openAt(WheelSource source, Component empty) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen != null) return;
        WheelSelectionState.refresh(minecraft.player);
        if (!WheelMenuContent.hasAnyEntry(WheelSelectionState.pages(), source)) {
            notice(empty);
            return;
        }
        WheelSelectionState.firstPageOf(source);
        show(minecraft);
    }

    private static void open(Minecraft minecraft) {
        if (minecraft.screen != null) return;
        // The pages are read before the page is chosen, so the page the wheel opens on is the first that has one.
        WheelSelectionState.refresh(minecraft.player);
        WheelSelectionState.firstPageOf(WheelSourceTypes.first());
        show(minecraft);
    }

    private static void show(Minecraft minecraft) {
        // A wheel with nothing anywhere is not opened - there would be nothing to point at - and says so instead.
        // The test spans every page, because an empty first page must not hide the rest.
        if (WheelMenuContent.hasAnyEntry(WheelSelectionState.pages())) {
            // Never open with nothing armed: a player who has not chosen anything lands on the first cell that
            // holds something, which is the cell the HUD grid then frames.
            WheelSelectionState.selectDefault();
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

    // One page back, one page forward, wrapping at both ends. The page is only what the ring draws and what the
    // slot keys address: the chosen cell is not moved by it, so switching cannot drop what the use key spends.
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
        stepPage(back ? -1 : 1);
    }

    // One place for a page turn: the two keys and the scroll wheel share it, so they cannot drift in what they
    // announce. Asked from the screen as well, hence the fresh read of the client.
    static void stepPage(int step) {
        Minecraft minecraft = Minecraft.getInstance();
        WheelSelectionState.stepPage(step, MxtClientConfig.INSTANCE.wheel.wrapPages.getValue());
        // Re-resolved right away: the HUD grid draws this tick, and the ring would otherwise show the old page.
        WheelSelectionState.refresh(minecraft.player);
        notice(Component.translatable("actionbar.mxt.wheel.page",
                WheelSelectionState.page() + 1, WheelSelectionState.pages().size(),
                WheelSelectionState.pageSource().displayName()));
    }

    // The wheel is the one screen where the player is already holding the mouse, so scrolling turns its pages.
    static boolean scrollTurnsPages() {
        return MxtClientConfig.INSTANCE.wheel.scrollSwitch.getValue();
    }

    // Each slot key arms its own cell of the page that is up and spends it in the same breath, so one key does
    // what "point at that cell, then press use" does - a keyboard hotbar over the wheel.
    private static void useSlotKeys(Minecraft minecraft) {
        List<MxtKeyMappings.KeyMappingHolder> slots = MxtKeyMappings.WHEEL_SLOTS;
        // Edges are sampled even while another screen owns the keys (only acting is gated), and raw polling is
        // what keeps a slot key from double-firing on the false press grabMouse's setAll() invents.
        boolean live = minecraft.player != null && (minecraft.screen == null || open != null);
        for (int sector = 0; sector < slots.size(); sector++) {
            boolean down = keyDown(minecraft, slots.get(sector));
            boolean pressed = down && !slotDown[sector];
            slotDown[sector] = down;
            if (pressed && live) useSlotKey(minecraft, sector);
        }
    }

    // An empty cell is silent: a press is never sent for nothing.
    private static void useSlotKey(Minecraft minecraft, int sector) {
        int number = WheelSelectionState.numberAt(sector);
        // Read before it is chosen, so the entry and the source that trigger is sent with are the same cell.
        WheelMenuEntry entry = WheelSelectionState.entry(number);
        if (entry == null) return;
        WheelSource source = WheelMenuContent.source(WheelSelectionState.pages(), number);
        WheelSelectionState.selectSector(sector);
        entry.onSelected(new WheelSelection(source, number, entry, WheelSelection.Method.KEY));
    }

    // The use key went down: spend the pointed cell if the wheel is up, the remembered one if it is not.
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
        // Only an empty wheel has no target: a choice whose cell no longer holds anything has already fallen back
        // to the first cell that does, so "nothing has been chosen yet" is not a state the wheel can be in.
        notice(Component.translatable("actionbar.mxt.wheel.empty"));
    }

    // Client-side on purpose: feedback about the local player's own HUD - the server never hears about a key that
    // did nothing. A key pressed while another screen is open stays silent: that is typing, not a request.
    private static void notice(Component message) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) minecraft.gui.setOverlayMessage(message, false);
    }

    // Read from what this tick resolved, so the use key and the HUD cell that announces it agree by
    // construction - including the fallback for a chosen number that points past the pages that exist.
    private static @Nullable WheelSelection remembered() {
        WheelMenuEntry entry = WheelSelectionState.selected();
        if (entry == null) return null;
        return new WheelSelection(WheelSelectionState.selectedSource(), WheelSelectionState.effective(),
                entry, WheelSelection.Method.KEY);
    }

    // A bound key's state straight from the input device; an unbound key is never down.
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