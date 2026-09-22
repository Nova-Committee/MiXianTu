package com.iafenvoy.mxt.screen.wheel;

import com.iafenvoy.mxt.config.MxtClientConfig;
import com.iafenvoy.mxt.registry.MxtKeyMappings;
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
 * Opens and closes the wheel and spends what it holds: the wheel key only chooses, the use key spends the
 * pointed sector (or the remembered one while the wheel is down), and the twelve slot keys each arm and spend
 * their own sector. An empty wheel does not open.
 *
 * <p>Both keys are polled raw ({@code InputConstants}/GLFW), because {@code setScreen} releases every mapping
 * and {@code grabMouse}'s {@code setAll()} would re-press the use key and double-fire; edges are once a tick.
 * The slot keys are polled here for the same reason, even though they are registered in {@link MxtKeyMappings}
 * with the rest: a {@code KeyMapping}'s own edge would fire a cast that nobody pressed.</p>
 *
 * <p>A key that was pressed and could do nothing answers on the action bar ({@code WheelMenuController#notice})
 * in two cases - the wheel is empty, or nothing was ever selected. A sector whose entry is gone on purpose
 * stays silent: the wheel grid already draws it as empty, slot keys included.</p>
 */
@EventBusSubscriber(Dist.CLIENT)
public final class WheelMenuController {
    private static @Nullable WheelMenuScreen open;
    private static boolean lastDown;
    private static boolean lastUseDown;
    /**
     * The last state of each slot key, indexed like {@link MxtKeyMappings#WHEEL_SLOTS} - that is, by sector.
     * All twelve are unbound by default, so an unbound one simply never goes down.
     */
    private static final boolean[] slotDown = new boolean[MxtKeyMappings.WHEEL_SLOTS.size()];

    private WheelMenuController() {
    }

    static void usePointed(WheelSelection.Method method) {
        WheelMenuScreen screen = open;
        if (screen == null) return;
        // Null for an empty sector: nothing is spent and the wheel stays up. Silent on purpose - the sector
        // is drawn as empty right there, so a line about it would be answering a question nobody asked.
        WheelSelection selection = screen.selection(method);
        if (selection != null) selection.entry().onSelected(selection);
    }

    /** Called when something else takes the screen down; the controller must not touch the new screen. */
    static void screenRemoved(WheelMenuScreen screen) {
        if (open == screen) open = null;
    }

    private static void open(Minecraft minecraft) {
        if (minecraft.screen != null) return;
        // An empty wheel is not opened: there would be nothing to point at. Say so rather than do nothing.
        if (WheelMenuContent.isEmpty(minecraft.player)) {
            notice(Component.translatable("actionbar.mxt.wheel.empty"));
            return;
        }
        WheelMenuScreen screen = new WheelMenuScreen();
        open = screen;
        minecraft.setScreen(screen);
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
        if (open != null) WheelSelectionState.select(open.pointedSector());
        // Before this tick's edges act on it, and before the HUD cell draws it.
        WheelSelectionState.refresh(minecraft.player);

        // The use key first, so a tick that presses one key and releases the other spends what was aimed at.
        boolean useDown = keyDown(minecraft, MxtKeyMappings.WHEEL_USE);
        if (useDown != lastUseDown) {
            lastUseDown = useDown;
            if (useDown) use(minecraft);
        }
        // After it: a tick that presses both spends the sector that was armed, then the slot key's own one.
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
     * The twelve slot keys: each arms its own sector and spends it in the same breath, so one key does what
     * "point at that sector, then press the use key" does - a keyboard hotbar laid over the wheel.
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
     * Arms one sector and spends it; an empty one is silent, and a press is never sent for nothing.
     */
    private static void useSlotKey(Minecraft minecraft, int sector) {
        WheelMenuEntry entry = WheelMenuContent.entry(minecraft.player, sector);
        if (entry == null) return;
        WheelSelectionState.select(sector);
        entry.onSelected(new WheelSelection(sector, entry, WheelSelection.Method.KEY));
    }

    /** The use key went down: spend the pointed sector if the wheel is up, the remembered one if it is not. */
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
        // Only "nothing was ever picked" is worth an answer. A sector that was picked and no longer resolves
        // to an entry (empty, deleted, un-granted) is a normal state, and the wheel grid already shows it.
        if (WheelSelectionState.sector() < 0)
            notice(Component.translatable("actionbar.mxt.wheel.no_selection",
                    MxtKeyMappings.WHEEL.get().getTranslatedKeyMessage()));
    }

    /**
     * One line on the action bar, for a key that was pressed and could do nothing. Client-side on purpose:
     * this is feedback about the local player's own HUD, and the server never hears about a key that did
     * nothing. A key pressed while another screen is open stays silent - that is typing, not a request.
     */
    private static void notice(Component message) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) minecraft.gui.setOverlayMessage(message, false);
    }

    /**
     * The remembered sector as a selection, or {@code null} when there is nothing to spend. Read from the
     * selection this tick resolved, so the use key and the HUD cell that announces it agree by construction.
     */
    private static @Nullable WheelSelection remembered() {
        WheelMenuEntry entry = WheelSelectionState.selected();
        return entry == null ? null
                : new WheelSelection(WheelSelectionState.sector(), entry, WheelSelection.Method.KEY);
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
