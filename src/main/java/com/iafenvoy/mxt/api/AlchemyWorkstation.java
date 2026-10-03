package com.iafenvoy.mxt.api;

import com.iafenvoy.mxt.data.alchemy.AlchemyFurnaceDefinition;
import com.iafenvoy.mxt.runtime.alchemy.AlchemyFurnaceStructure;
import com.iafenvoy.mxt.runtime.alchemy.AlchemyPhase;
import com.iafenvoy.mxt.runtime.alchemy.AlchemyWorkstationState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/**
 * A placed furnace controller. Part block entities own material inventories. This controller owns the furnace item,
 * the batch and the heat cell it reads.
 */
public interface AlchemyWorkstation {
    /**
     * Live view of the nine logical slots. Not a copied input list.
     */
    Container container();

    AlchemyWorkstationState state();

    BlockPos getBlockPos();

    /**
     * Live count-1 furnace item. Callers must not mutate it.
     */
    ItemStack furnaceItem();

    Optional<Holder<AlchemyFurnaceDefinition>> furnaceDefinition();

    double temperature();

    double targetTemperature();

    boolean setTargetTemperature(double temperature);

    void setTemperature(double temperature);

    /**
     * The cell the heat comes from: the bottom layer's centre, never claimed by the structure. A block standing
     * there - read through the {@code mxt:heat_source} table or its own {@link AlchemyHeatSource} - is what heats
     * the furnace; nothing has to be put into an interface.
     */
    BlockPos heatSourcePos();

    /**
     * Minimum over the wall cells' ratings, or 0 when any of them is missing, unloaded, disabled or illegal.
     */
    double wallTemperatureLimit();

    /**
     * Heat source maximum at {@link #heatSourcePos()}, or 0 when that cell is unloaded or holds no heat source.
     */
    double heatTemperatureLimit();

    /**
     * {@code min(wall, heat)}, or 0 when either limit is unavailable.
     */
    double maximumTemperature();

    AlchemyFurnaceStructure.Status structureStatus();

    AlchemyPhase phase();

    void setChanged();
}
