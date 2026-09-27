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
 * A placed furnace controller. Part block entities own material inventories. This controller owns the fire slot,
 * the furnace item and the batch.
 */
public interface AlchemyWorkstation {
    /** Live view of the nine logical slots. Not a copied input list. */
    Container container();

    AlchemyWorkstationState state();

    BlockPos getBlockPos();

    /** Live count-1 furnace item. Callers must not mutate it. */
    ItemStack furnaceItem();

    Optional<Holder<AlchemyFurnaceDefinition>> furnaceDefinition();

    double temperature();

    double targetTemperature();

    boolean setTargetTemperature(double temperature);

    void setTemperature(double temperature);

    /** The single exotic-fire slot. Count is capped at 1. */
    Container fireContainer();

    boolean canPlaceFire(ItemStack stack);

    boolean canTakeFire();

    /** Minimum of the 22 wall ratings, or 0 when any wall is missing, unloaded, disabled or illegal. */
    double wallTemperatureLimit();

    /** Fire item maximum, or 0 when the slot is empty or the answer is not finite and positive. */
    double fireTemperatureLimit();

    /** {@code min(wall, fire)}, or 0 when either limit is unavailable. */
    double maximumTemperature();

    AlchemyFurnaceStructure.Status structureStatus();

    AlchemyPhase phase();

    void setChanged();
}
