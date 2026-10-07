package com.iafenvoy.mxt.render;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.runtime.item.IncompleteMarkerService;
import com.iafenvoy.mxt.util.ClientLevelAccess;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.IItemDecorator;
import net.neoforged.neoforge.client.event.RegisterItemDecorationsEvent;
import org.jspecify.annotations.NonNull;

/**
 * The one place an {@code mxt:incomplete} declaration becomes pixels: a 16x16 badge blitted into the top-left of the
 * slot, drawn where a slot's count and wear are drawn so it appears in every screen that draws a stack and nowhere
 * in the world.
 *
 * <p>The platform's decorator is keyed by item while the question is asked of the stack, so it is registered for
 * every item and the declaration decides - which is what lets a pack mark a tag or a stack reading. It is also why
 * this is a registration rather than a per-item loop a pack could extend: a client's set of decorators is built
 * once, at setup, and only the declarations behind it change with a reload.
 */
@EventBusSubscriber(Dist.CLIENT)
public final class IncompleteMarkerRenderer implements IItemDecorator {
    // The badge is authored 16x16 with the mark in its top-left corner, which is the one part of a slot neither the
    // count nor the wear bar uses. The whole canvas is blitted, so the two never have to agree on the mark's size.
    private static final Identifier BADGE = Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "textures/gui/incomplete.png");
    private static final IncompleteMarkerRenderer INSTANCE = new IncompleteMarkerRenderer();
    private static final int BADGE_SIZE = 16;

    private IncompleteMarkerRenderer() {
    }

    @SubscribeEvent
    public static void register(RegisterItemDecorationsEvent event) {
        for (Item item : BuiltInRegistries.ITEM) event.register(item, INSTANCE);
    }

    @Override
    public boolean render(@NonNull GuiGraphicsExtractor graphics, @NonNull Font font, @NonNull ItemStack stack, int x, int y) {
        Provider access = ClientLevelAccess.level();
        // No level is no registries: a stack in a screen drawn before one is loaded carries no declaration.
        if (access == null || !IncompleteMarkerService.marked(access, stack)) return false;
        graphics.blit(RenderPipelines.GUI_TEXTURED, BADGE, x, y, 0.0F, 0.0F, BADGE_SIZE, BADGE_SIZE, BADGE_SIZE, BADGE_SIZE);
        // Nothing of the shared render state is touched, so the decorators after this one are unaffected.
        return false;
    }
}
