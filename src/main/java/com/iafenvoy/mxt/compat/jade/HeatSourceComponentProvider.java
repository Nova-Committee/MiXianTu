package com.iafenvoy.mxt.compat.jade;

import com.iafenvoy.mxt.util.TooltipText;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/**
 * The heat a block gives, the way the furnace reads it: what {@link HeatSourceDataProvider} sent, so a block that
 * answers for itself - a lit fire, a filled brazier - reads the same as one the {@code mxt:heat_source} table covers.
 */
public enum HeatSourceComponentProvider implements IBlockComponentProvider {
    INSTANCE;

    @Override
    public void appendTooltip(@NonNull ITooltip tooltip, BlockAccessor accessor, @NonNull IPluginConfig config) {
        CompoundTag data = accessor.getServerData();
        if (!data.contains(HeatSourceDataProvider.TEMPERATURE)) return;
        tooltip.add(Component.translatable("jade.mxt.heat_source.temperature",
                TooltipText.number(data.getDoubleOr(HeatSourceDataProvider.TEMPERATURE, 0.0D))), HeatSourceDataProvider.ID);
        tooltip.add(Component.translatable("jade.mxt.heat_source.speed",
                TooltipText.number(data.getDoubleOr(HeatSourceDataProvider.HEATING, 0.0D))), HeatSourceDataProvider.ID);
    }

    @Override
    public @NonNull Identifier getUid() {
        return HeatSourceDataProvider.ID;
    }
}
