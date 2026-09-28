package com.iafenvoy.mxt.render;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.item.Pill;
import com.iafenvoy.mxt.runtime.item.ItemBindingService;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.color.item.ItemTintSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Paints the built-in pill carrier with the colour of the pill the stack resolves. Only the carrier's own client item
 * definition declares this tint, so an item a binding claims keeps the texture it already had.
 */
@EventBusSubscriber(Dist.CLIENT)
public final class PillTintSource implements ItemTintSource {
    // The type id items/pill.json names. This is a tint source type, not a datapack registry.
    public static final Identifier ID = Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "pill");
    public static final PillTintSource INSTANCE = new PillTintSource();
    public static final MapCodec<PillTintSource> MAP_CODEC = MapCodec.unit(INSTANCE);

    private PillTintSource() {
    }

    @SubscribeEvent
    public static void register(RegisterColorHandlersEvent.ItemTintSources event) {
        event.register(ID, MAP_CODEC);
    }

    @Override
    public int calculate(@NonNull ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity owner) {
        // The resolution the dose itself uses, so a stack is never painted as a pill other than the one it runs. No
        // level means no registry to resolve in and no world to hold a pill in; a pill that would be refused paints
        // nothing, which is also what an item no definition claims gets.
        Pill pill = level == null ? null : ItemBindingService.resolvePill(level.registryAccess(), stack).effects().orElse(null);
        return ARGB.opaque(pill == null ? Pill.DEFAULT_COLOR : pill.color());
    }

    @Override
    public @NonNull MapCodec<? extends ItemTintSource> type() {
        return MAP_CODEC;
    }
}
