package com.iafenvoy.mxt.runtime.alchemy;

import com.iafenvoy.mxt.item.block.AlchemyFurnaceBlock;
import com.iafenvoy.mxt.item.block.entity.AlchemyFurnaceBlockEntity;
import com.iafenvoy.mxt.item.block.entity.AlchemyFurnaceInventoryBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

/**
 * Logical slots 0-1 main, 2-3 auxiliary, 4 catalyst, 5-8 output. Each call reads the loaded part; it never copies
 * a reclaimable input list and never loads a chunk.
 */
public final class AlchemyAggregateContainer implements Container {
    private final AlchemyFurnaceBlockEntity core;

    public AlchemyAggregateContainer(AlchemyFurnaceBlockEntity core) {
        this.core = core;
    }

    @Override
    public int getContainerSize() {
        return AlchemySlots.TOTAL;
    }

    public boolean present(int index) {
        return this.inventory(index) != null;
    }

    @Override
    public boolean isEmpty() {
        for (int index = 0; index < AlchemySlots.TOTAL; index++) if (!this.getItem(index).isEmpty()) return false;
        return true;
    }

    @Override
    public @NonNull ItemStack getItem(int index) {
        Container inventory = this.inventory(index);
        return inventory == null ? ItemStack.EMPTY : inventory.getItem(localSlot(index));
    }

    @Override
    public @NonNull ItemStack removeItem(int index, int count) {
        Container inventory = this.inventory(index);
        return inventory == null ? ItemStack.EMPTY : inventory.removeItem(localSlot(index), count);
    }

    @Override
    public @NonNull ItemStack removeItemNoUpdate(int index) {
        Container inventory = this.inventory(index);
        return inventory == null ? ItemStack.EMPTY : inventory.removeItemNoUpdate(localSlot(index));
    }

    @Override
    public void setItem(int index, @NonNull ItemStack stack) {
        Container inventory = this.inventory(index);
        if (inventory != null) inventory.setItem(localSlot(index), stack);
    }

    @Override
    public void setChanged() {
        this.core.setChanged();
    }

    @Override
    public boolean stillValid(@NonNull Player player) {
        var level = this.core.getLevel();
        return level != null && player.level() == level && !this.core.isRemoved()
                && player.distanceToSqr(this.core.getBlockPos().getCenter()) <= 64.0D
                && level.isLoaded(this.core.getBlockPos()) && level.getBlockEntity(this.core.getBlockPos()) == this.core;
    }

    @Override
    public void clearContent() {
        for (int index = 0; index < AlchemySlots.TOTAL; index++) this.setItem(index, ItemStack.EMPTY);
    }

    private @Nullable Container inventory(int index) {
        if (index < 0 || index >= AlchemySlots.TOTAL) return null;
        if (!(this.core.getLevel() instanceof ServerLevel server)) return null;
        BlockPos corePos = this.core.getBlockPos();
        if (this.core.isRemoved() || !server.isLoaded(corePos) || server.getBlockEntity(corePos) != this.core) return null;
        if (!(this.core.getBlockState().getBlock() instanceof AlchemyFurnaceBlock)) return null;
        Direction facing = this.core.getBlockState().getValue(AlchemyFurnaceBlock.FACING);
        AlchemyInventoryKind kind = index < AlchemySlots.AUX_START ? AlchemyInventoryKind.MAIN
                : index < AlchemySlots.OUTPUT_START ? AlchemyInventoryKind.AUXILIARY : AlchemyInventoryKind.OUTPUT;
        BlockPos pos = AlchemyFurnaceStructure.world(this.core.getBlockPos(), facing, kind.index());
        if (!server.isLoaded(pos) || !(server.getBlockEntity(pos) instanceof AlchemyFurnaceInventoryBlockEntity inventory))
            return null;
        if (inventory.isRemoved() || inventory.kind() != kind || inventory.claimedByOther(corePos)
                || localSlot(index) >= inventory.inventory().getContainerSize()) return null;
        return inventory.inventory();
    }

    private static int localSlot(int index) {
        return index < AlchemySlots.AUX_START ? index
                : index < AlchemySlots.OUTPUT_START ? index - AlchemySlots.AUX_START : index - AlchemySlots.OUTPUT_START;
    }
}
