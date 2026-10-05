package com.iafenvoy.mxt.compat.jade;

import com.iafenvoy.mxt.item.block.DisplayStandBlock;
import com.iafenvoy.mxt.item.block.SpiritCraftingTableBlock;
import net.minecraft.world.level.block.Block;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

/**
 * Registers Jade displays for datapack-driven, non-container block values. Both halves of a payload are registered
 * here, and always as two objects: Jade rejects a data provider that also implements {@link
 * snownee.jade.api.IComponentProvider} ("since Minecraft 1.21.6"), and it sends nothing for a data provider the
 * common half never heard of. The two objects pair up by uid.
 */
@WailaPlugin
public final class MxtJadePlugin implements IWailaPlugin {
    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(HeatSourceDataProvider.INSTANCE, Block.class);
        registration.registerBlockDataProvider(SpiritCraftingTableDataProvider.INSTANCE, SpiritCraftingTableBlock.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(BlockAuraComponentProvider.INSTANCE, Block.class);
        registration.registerBlockComponent(HeatSourceComponentProvider.INSTANCE, Block.class);
        registration.registerBlockComponent(DisplayStandComponentProvider.INSTANCE, DisplayStandBlock.class);
        registration.registerBlockComponent(SpiritCraftingTableComponentProvider.INSTANCE, SpiritCraftingTableBlock.class);
    }
}
