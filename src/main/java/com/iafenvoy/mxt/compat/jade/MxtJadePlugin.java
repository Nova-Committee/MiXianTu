package com.iafenvoy.mxt.compat.jade;

import com.iafenvoy.mxt.item.block.DisplayStandBlock;
import com.iafenvoy.mxt.item.block.SpiritCraftingTableBlock;
import net.minecraft.world.level.block.Block;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

/**
 * Registers Jade displays for datapack-driven, non-container block values.
 */
@WailaPlugin
public final class MxtJadePlugin implements IWailaPlugin {
    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(BlockAuraComponentProvider.INSTANCE, Block.class);
        registration.registerBlockComponent(DisplayStandComponentProvider.INSTANCE, DisplayStandBlock.class);
        registration.registerBlockComponent(SpiritCraftingTableComponentProvider.INSTANCE, SpiritCraftingTableBlock.class);
    }
}
