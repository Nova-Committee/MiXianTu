package com.iafenvoy.mxt.render.overlay.hotbar;

import com.iafenvoy.mxt.config.MxtClientConfig;
import com.iafenvoy.mxt.config.MxtClientConfig.HotbarMode;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.network.payload.SpiritBurstC2SPayload;
import com.iafenvoy.mxt.registry.MxtKeyMappings;
import com.iafenvoy.mxt.render.overlay.hotbar.AbilityHotbarClient.ResolvedAbility;
import com.iafenvoy.mxt.util.HolderHelper;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.InputConstants.Type;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.KeyMapping.Category;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder.Reference;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent.Post;
import net.neoforged.neoforge.client.event.ClientTickEvent.Pre;
import net.neoforged.neoforge.client.event.InputEvent.Key;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Owns the client input, state, and vanilla-input suppression for both Mxt hotbars.
 */
@EventBusSubscriber(Dist.CLIENT)
public final class HotbarController {
    public enum Mode {
        NONE, ABILITY, SPIRIT
    }

    private static final Category CATEGORY = MxtKeyMappings.category();
    private static final KeyMapping SPIRIT_BURST = new KeyMapping(
            "key.mxt.spirit_burst", Type.KEYSYM, InputConstants.KEY_V, CATEGORY);
    private static final KeyMapping ABILITY_MENU = new KeyMapping(
            "key.mxt.ability_menu", Type.KEYSYM, InputConstants.KEY_LALT, CATEGORY);
    private static final int[] NUMBER_KEYS = {
            InputConstants.KEY_1, InputConstants.KEY_2, InputConstants.KEY_3,
            InputConstants.KEY_4, InputConstants.KEY_5, InputConstants.KEY_6,
            InputConstants.KEY_7, InputConstants.KEY_8, InputConstants.KEY_9
    };
    private static final boolean[] NUMBER_DOWN = new boolean[NUMBER_KEYS.length];
    private static final byte[] NUMBER_MODES = new byte[NUMBER_KEYS.length];
    private static boolean abilityMenuPressed;
    private static boolean spiritBurstPressed;
    private static Mode mode = Mode.NONE;
    private static final HotbarEntry[] ACTIVE_ENTRIES = new HotbarEntry[NUMBER_KEYS.length];
    private static int numberReconcileTicks;
    private static boolean previousHotbarOpen;

    private HotbarController() {
    }

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(ABILITY_MENU);
        event.register(SPIRIT_BURST);
    }

    @SubscribeEvent
    public static void onClientTickPre(Pre event) {
        clearVanillaHotbarClicks();
    }

    @SubscribeEvent
    public static void onClientTickPost(Post event) {
        tickHotbarKey(ABILITY_MENU, true);
        tickHotbarKey(SPIRIT_BURST, false);

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

    private static void tickHotbarKey(KeyMapping mapping, boolean ability) {
        boolean pressed = mapping.isDown();
        boolean wasPressed = ability ? abilityMenuPressed : spiritBurstPressed;
        if (pressed != wasPressed) {
            if (ability) handleAbilityMenuKey(pressed);
            else handleSpiritBurstKey(pressed);
            if (ability) abilityMenuPressed = pressed;
            else spiritBurstPressed = pressed;
        }
    }

    private static void handleAbilityMenuKey(boolean pressed) {
        if (MxtClientConfig.hotbarMode() == HotbarMode.TOGGLE && !pressed) return;
        boolean open = MxtClientConfig.hotbarMode() == HotbarMode.TOGGLE
                ? mode != Mode.ABILITY : pressed;
        if (open) openAbilityHotbar();
        else closeAbilityHotbar();
    }

    private static void handleSpiritBurstKey(boolean pressed) {
        if (MxtClientConfig.hotbarMode() == HotbarMode.TOGGLE && !pressed) return;
        boolean open = MxtClientConfig.hotbarMode() == HotbarMode.TOGGLE ? mode != Mode.SPIRIT : pressed;
        if (open) openSpiritHotbar();
        else closeSpiritHotbar();
    }

    private static void openAbilityHotbar() {
        if (mode == Mode.SPIRIT) closeSpiritHotbar();
        mode = Mode.ABILITY;
    }

    private static void closeAbilityHotbar() {
        if (mode != Mode.ABILITY) return;
        mode = Mode.NONE;
    }

    private static void openSpiritHotbar() {
        if (mode == Mode.ABILITY) closeAbilityHotbar();
        mode = Mode.SPIRIT;
    }

    private static void closeSpiritHotbar() {
        if (mode != Mode.SPIRIT) return;
        mode = Mode.NONE;
        ClientPacketDistributor.sendToServer(new SpiritBurstC2SPayload(false, Optional.empty()));
    }

    private static void reconcileNumberKeys() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.getWindow() == null) return;
        for (int index = 0; index < NUMBER_KEYS.length; index++) {
            boolean down = isHotbarOpen() && InputConstants.isKeyDown(minecraft.getWindow(), NUMBER_KEYS[index]);
            if (down != NUMBER_DOWN[index]) updateNumberKey(index, down);
        }
    }

    private static void updateNumberKey(int index, boolean down) {
        if (!isHotbarOpen()) {
            if (!down && NUMBER_DOWN[index]) releaseNumberKey(index);
            NUMBER_DOWN[index] = false;
            NUMBER_MODES[index] = 0;
            return;
        }
        int targetMode = mode == Mode.ABILITY ? 1 : (mode == Mode.SPIRIT ? 2 : 0);
        if (down && NUMBER_DOWN[index] && NUMBER_MODES[index] != targetMode) {
            releaseNumberKey(index);
            NUMBER_DOWN[index] = false;
        }
        if (down == NUMBER_DOWN[index]) return;
        if (down) {
            if (targetMode == 1) {
                NUMBER_MODES[index] = 1;
            } else if (targetMode == 2) {
                NUMBER_MODES[index] = 2;
            }
            ACTIVE_ENTRIES[index] = entry(index);
            if (ACTIVE_ENTRIES[index] != null)
                ACTIVE_ENTRIES[index].onPress(Minecraft.getInstance().player);
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
        NUMBER_MODES[index] = 0;
    }

    private static boolean isHotbarOpen() {
        return mode != Mode.NONE;
    }

    private static void suppressVanillaHotbarKey(int index) {
        if (!isHotbarOpen() || MxtClientConfig.allowVanillaHotbarSelection()) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options == null) return;
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

    public static Mode mode() {
        return mode;
    }

    public static boolean isEntryActive(int index) {
        return index >= 0 && index < ACTIVE_ENTRIES.length && ACTIVE_ENTRIES[index] != null;
    }

    public static List<HotbarEntry> entries(Player player) {
        if (player == null) return List.of();
        List<HotbarEntry> result = new ArrayList<>();
        if (mode == Mode.ABILITY) {
            List<ResolvedAbility> abilities = AbilityHotbarClient.all(player);
            for (ResolvedAbility ability : abilities) result.add(new AbilityHotbarEntry(ability.id()));
        } else if (mode == Mode.SPIRIT) {
            List<Reference<Resource>> resources = SpiritBurstClient.resources(player);
            for (Reference<Resource> resource : resources) result.add(new SpiritHotbarEntry(HolderHelper.id(resource)));
        }
        return result;
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
