package com.iafenvoy.mxt.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import org.jspecify.annotations.NonNull;

import java.util.function.Consumer;

public final class SpecialItem extends Item {
    private final int tooltipLines;

    public SpecialItem(Properties properties, int tooltipLines) {
        super(properties);
        this.tooltipLines = tooltipLines;
    }

    @SuppressWarnings("deprecation")
    @Override
    public void appendHoverText(@NonNull ItemStack stack, @NonNull TooltipContext context, @NonNull TooltipDisplay display, @NonNull Consumer<Component> builder, @NonNull TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, display, builder, tooltipFlag);
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        for (int line = 0; line < this.tooltipLines; line++)
            builder.accept(Component.translatable(id.toLanguageKey("tooltip", Integer.toString(line))).withStyle(ChatFormatting.GOLD));
    }
}
