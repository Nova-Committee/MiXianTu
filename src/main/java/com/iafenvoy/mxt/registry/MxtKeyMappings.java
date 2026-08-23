package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.network.payload.BackSlotSwapC2SPayload;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.InputConstants.Type;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.KeyMapping.Category;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent.Post;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * Registers key mappings that are not owned by a client overlay.
 */
@EventBusSubscriber(Dist.CLIENT)
public final class MxtKeyMappings {
    private static final Category CATEGORY = new Category(
            Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "general"));
    private static final KeyMapping SWAP_BACK = new KeyMapping(
            "key.mxt.swap_back", Type.KEYSYM, InputConstants.UNKNOWN.getValue(), CATEGORY);
    private static boolean swapBackPressed;

    private MxtKeyMappings() {
    }

    public static Category category() {
        return CATEGORY;
    }

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        event.registerCategory(CATEGORY);
        event.register(SWAP_BACK);
    }

    @SubscribeEvent
    public static void tick(Post event) {
        boolean pressed = SWAP_BACK.isDown();
        if (pressed && !swapBackPressed)
            ClientPacketDistributor.sendToServer(BackSlotSwapC2SPayload.INSTANCE);
        swapBackPressed = pressed;
    }
}
