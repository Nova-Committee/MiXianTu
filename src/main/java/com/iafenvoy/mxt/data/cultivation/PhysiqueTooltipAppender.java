package com.iafenvoy.mxt.data.cultivation;

import com.iafenvoy.mxt.registry.MxtDataComponents;
import com.iafenvoy.mxt.util.DefinitionText;
import com.iafenvoy.mxt.util.HolderHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.tooltip.TooltipLocation;
import net.neoforged.neoforge.event.RegisterTooltipAppendersEvent;

import java.util.function.Consumer;

/**
 * Names the physique a stack carries and what using it does, so the row a content pack hands out explains itself.
 */
@EventBusSubscriber(Dist.CLIENT)
public final class PhysiqueTooltipAppender {
    private PhysiqueTooltipAppender() {
    }

    @SubscribeEvent
    public static void register(RegisterTooltipAppendersEvent event) {
        event.registerAppender(TooltipLocation.POST_CUSTOM, PhysiqueTooltipAppender::appendPhysique);
    }

    private static void appendPhysique(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                       Player player, TooltipFlag flag, Consumer<Component> builder) {
        Holder<Physique> physique = stack.get(MxtDataComponents.PHYSIQUE);
        if (physique == null) return;
        builder.accept(Component.translatable("tooltip.mxt.physique.physique", DefinitionText.name(physique, "physique")).withStyle(ChatFormatting.AQUA));
        builder.accept(Component.translatable("tooltip.mxt.physique.hint").withStyle(ChatFormatting.GRAY));
        if (flag.isAdvanced())
            builder.accept(Component.literal("   " + HolderHelper.id(physique)).withStyle(ChatFormatting.DARK_GRAY));
    }
}
