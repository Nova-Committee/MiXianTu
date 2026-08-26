package com.iafenvoy.mxt.render.overlay.hotbar;

import com.iafenvoy.mxt.config.MxtClientConfig;
import com.iafenvoy.mxt.config.MxtClientConfig.HotbarMode;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent.Post;
import net.neoforged.neoforge.client.event.ClientTickEvent.Pre;
import net.neoforged.neoforge.client.event.InputEvent.Key;

import java.util.List;
import java.util.Objects;

/**
 * Owns the client input, state, and vanilla-input suppression for both Mxt hotbars.
 */
@EventBusSubscriber(Dist.CLIENT)
public final class HotbarController {
    private static final int[] NUMBER_KEYS = {
            InputConstants.KEY_1, InputConstants.KEY_2, InputConstants.KEY_3,
            InputConstants.KEY_4, InputConstants.KEY_5, InputConstants.KEY_6,
            InputConstants.KEY_7, InputConstants.KEY_8, InputConstants.KEY_9
    };
    private static final boolean[] NUMBER_DOWN = new boolean[NUMBER_KEYS.length];
    private static final Identifier[] NUMBER_MODES = new Identifier[NUMBER_KEYS.length];
    private static Identifier mode;
    private static final HotbarEntry[] ACTIVE_ENTRIES = new HotbarEntry[NUMBER_KEYS.length];
    private static int numberReconcileTicks;
    private static boolean previousHotbarOpen;

    @SubscribeEvent
    public static void onClientTickPre(Pre event) {
        clearVanillaHotbarClicks();
    }

    @SubscribeEvent
    public static void onClientTickPost(Post event) {
        boolean hotbarOpen = isHotbarOpen();
        if (hotbarOpen != previousHotbarOpen || hotbarOpen && ++numberReconcileTicks >= 5) {
            reconcileNumberKeys();
            numberReconcileTicks = 0;
        }
        previousHotbarOpen = hotbarOpen;
        tickActiveEntries();
    }

    @SubscribeEvent
    public static void onKey(Key event) {
        int index = numberKeyIndex(event.getKey());
        if (index >= 0) {
            if (event.getAction() == InputConstants.PRESS) updateNumberKey(index, true);
            else if (event.getAction() == InputConstants.RELEASE) updateNumberKey(index, false);
            suppressVanillaHotbarKey(index);
        }
    }

    static void handleModeKey(Identifier id, boolean pressed) {
        if (MxtClientConfig.hotbarMode() == HotbarMode.TOGGLE && !pressed) return;
        if (HotbarModeRegistry.get(id).isEmpty()) return;
        boolean open = MxtClientConfig.hotbarMode() == HotbarMode.TOGGLE ? !isMode(id) : pressed;
        if (open) openMode(id);
        else closeMode(id);
    }

    private static void openMode(Identifier id) {
        if (mode != null && !mode.equals(id)) closeMode(mode);
        mode = id;
    }

    private static void closeMode(Identifier id) {
        if (!isMode(id)) return;
        mode = null;
        HotbarModeRegistry.close(id, Minecraft.getInstance().player);
    }

    private static void reconcileNumberKeys() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;
        for (int index = 0; index < NUMBER_KEYS.length; index++) {
            boolean down = isHotbarOpen() && InputConstants.isKeyDown(minecraft.getWindow(), NUMBER_KEYS[index]);
            if (down != NUMBER_DOWN[index]) updateNumberKey(index, down);
        }
    }

    private static void updateNumberKey(int index, boolean down) {
        if (!isHotbarOpen()) {
            if (!down && NUMBER_DOWN[index]) releaseNumberKey(index);
            NUMBER_DOWN[index] = false;
            NUMBER_MODES[index] = null;
            return;
        }
        Identifier targetMode = mode;
        if (down && NUMBER_DOWN[index] && !Objects.equals(NUMBER_MODES[index], targetMode)) {
            releaseNumberKey(index);
            NUMBER_DOWN[index] = false;
        }
        if (down == NUMBER_DOWN[index]) return;
        if (down) {
            NUMBER_MODES[index] = targetMode;
            HotbarEntry entry = entry(index);
            if (entry != null && entry.canPress(Minecraft.getInstance().player)) {
                ACTIVE_ENTRIES[index] = entry;
                entry.onPress(Minecraft.getInstance().player);
            }
            Minecraft minecraft = Minecraft.getInstance();
            if (MxtClientConfig.allowVanillaHotbarSelection() && minecraft.player != null)
                minecraft.player.getInventory().setSelectedSlot(index);
        } else releaseNumberKey(index);
        NUMBER_DOWN[index] = down;
    }

    private static void releaseNumberKey(int index) {
        HotbarEntry entry = ACTIVE_ENTRIES[index];
        if (entry != null) entry.onRelease(Minecraft.getInstance().player);
        ACTIVE_ENTRIES[index] = null;
        NUMBER_MODES[index] = null;
    }

    private static boolean isHotbarOpen() {
        return mode != null;
    }

    private static void suppressVanillaHotbarKey(int index) {
        if (!isHotbarOpen() || MxtClientConfig.allowVanillaHotbarSelection()) return;
        Minecraft minecraft = Minecraft.getInstance();
        KeyMapping hotbarKey = minecraft.options.keyHotbarSlots[index];
        hotbarKey.setDown(false);
        while (hotbarKey.consumeClick()) {
            // The key is reserved for the currently visible Mxt hotbar.
        }
    }

    private static void clearVanillaHotbarClicks() {
        if (!isHotbarOpen() || MxtClientConfig.allowVanillaHotbarSelection()) return;
        for (int index = 0; index < NUMBER_KEYS.length; index++) suppressVanillaHotbarKey(index);
    }

    public static Identifier mode() {
        return mode;
    }

    public static boolean isOpen() {
        return mode != null;
    }

    public static boolean isMode(Identifier id) {
        return id != null && id.equals(mode);
    }

    /** Opens a registered mode without requiring a dedicated key mapping. */
    public static boolean open(Identifier id) {
        if (HotbarModeRegistry.get(id).isEmpty()) return false;
        openMode(id);
        return true;
    }

    /** Closes the currently active mode when it matches the supplied ID. */
    public static boolean close(Identifier id) {
        if (!isMode(id)) return false;
        closeMode(id);
        return true;
    }

    public static boolean isEntryActive(int index) {
        return index >= 0 && index < ACTIVE_ENTRIES.length && ACTIVE_ENTRIES[index] != null;
    }

    public static List<HotbarEntry> entries(Player player) {
        if (player == null) return List.of();
        return HotbarModeRegistry.entries(mode, player);
    }

    private static HotbarEntry entry(int index) {
        Player player = Minecraft.getInstance().player;
        if (player == null) return null;
        List<HotbarEntry> entries = entries(player);
        return index >= 0 && index < entries.size() ? entries.get(index) : null;
    }

    private static void tickActiveEntries() {
        Player player = Minecraft.getInstance().player;
        if (player == null) return;
        for (HotbarEntry entry : ACTIVE_ENTRIES)
            if (entry != null) entry.onPressTick(player);
    }

    private static int numberKeyIndex(int key) {
        for (int index = 0; index < NUMBER_KEYS.length; index++)
            if (NUMBER_KEYS[index] == key) return index;
        return -1;
    }
}
