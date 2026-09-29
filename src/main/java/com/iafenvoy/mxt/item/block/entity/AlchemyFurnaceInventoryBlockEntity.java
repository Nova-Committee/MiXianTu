package com.iafenvoy.mxt.item.block.entity;

import com.iafenvoy.mxt.data.alchemy.AlchemyFurnaceDefinition;
import com.iafenvoy.mxt.item.block.AlchemyFurnaceBlock;
import com.iafenvoy.mxt.item.block.AlchemyFurnaceInventoryBlock;
import com.iafenvoy.mxt.registry.MxtBlockEntities;
import com.iafenvoy.mxt.runtime.alchemy.*;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import java.util.List;

/**
 * One physical port. Main has two slots, auxiliary has two plus the catalyst, output has four.
 * Hoppers may extract only from the output block's down face.
 */
public final class AlchemyFurnaceInventoryBlockEntity extends BlockEntity implements WorldlyContainer {
    private static final int[] OUTPUT_SLOTS = {0, 1, 2, 3};
    private static final int[] NONE = {};
    private final AlchemyInventoryKind kind;
    private final SimpleContainer inventory;
    private BlockPos controller;
    private boolean voidContents;

    public AlchemyFurnaceInventoryBlockEntity(BlockPos pos, BlockState state) {
        super(MxtBlockEntities.ALCHEMY_FURNACE_INVENTORY.get(), pos, state);
        this.kind = state.getBlock() instanceof AlchemyFurnaceInventoryBlock block ? block.kind() : AlchemyInventoryKind.MAIN;
        this.inventory = new SimpleContainer(this.kind.slots()) {
            @Override
            public void setChanged() {
                AlchemyFurnaceInventoryBlockEntity.this.setChanged();
            }

            @Override
            public boolean canPlaceItem(int slot, @NonNull ItemStack stack) {
                return AlchemyFurnaceInventoryBlockEntity.this.canPlaceItem(slot, stack);
            }
        };
    }

    public AlchemyInventoryKind kind() {
        return this.kind;
    }

    public Container inventory() {
        return this.inventory;
    }

    public @Nullable AlchemyFurnaceBlockEntity controller() {
        if (this.controller == null || !(this.level instanceof ServerLevel server) || !server.isLoaded(this.controller))
            return null;
        if (!(server.getBlockEntity(this.controller) instanceof AlchemyFurnaceBlockEntity furnace)) return null;
        BlockState state = furnace.getBlockState();
        if (!(state.getBlock() instanceof AlchemyFurnaceBlock) || !state.getValue(AlchemyFurnaceBlock.FORMED))
            return null;
        return this.claimedBy(furnace.getBlockPos()) ? furnace : null;
    }

    public void claim(BlockPos controller) {
        this.controller = controller.immutable();
        this.setChanged();
    }

    public void clearClaim() {
        this.controller = null;
        this.setChanged();
    }

    public boolean claimedBy(BlockPos pos) {
        return this.controller != null && this.controller.equals(pos);
    }

    public boolean claimedByOther(BlockPos pos) {
        if (this.controller == null || this.controller.equals(pos) || this.level == null) return false;
        if (!this.level.isLoaded(this.controller)) return true;
        return this.level.getBlockEntity(this.controller) instanceof AlchemyFurnaceBlockEntity furnace
                && furnace.getBlockState().getValue(AlchemyFurnaceBlock.FORMED);
    }

    public void validateClaim() {
        if (this.controller == null || !(this.level instanceof ServerLevel server)) return;
        if (!server.isLoaded(this.controller)) {
            server.scheduleTick(this.worldPosition, this.getBlockState().getBlock(), 20);
            return;
        }
        if (server.getBlockEntity(this.controller) instanceof AlchemyFurnaceBlockEntity furnace
                && furnace.getBlockState().getValue(AlchemyFurnaceBlock.FORMED)) return;
        this.clearClaim();
        BlockState state = this.getBlockState();
        if (state.getValue(AlchemyFurnaceInventoryBlock.FORMED) || state.getValue(AlchemyFurnaceInventoryBlock.LIT))
            server.setBlock(this.worldPosition, state.setValue(AlchemyFurnaceInventoryBlock.FORMED, false)
                    .setValue(AlchemyFurnaceInventoryBlock.LIT, false), 3);
    }

    public void voidContents() {
        this.voidContents = true;
        this.inventory.clearContent();
    }

    @Override
    public int getContainerSize() {
        return this.inventory.getContainerSize();
    }

    @Override
    public boolean isEmpty() {
        return this.inventory.isEmpty();
    }

    @Override
    public @NonNull ItemStack getItem(int slot) {
        return this.inventory.getItem(slot);
    }

    @Override
    public @NonNull ItemStack removeItem(int slot, int count) {
        if (!this.canTakeItem(slot, this.inventory.getItem(slot))) return ItemStack.EMPTY;
        return this.inventory.removeItem(slot, count);
    }

    @Override
    public @NonNull ItemStack removeItemNoUpdate(int slot) {
        if (!this.canTakeItem(slot, this.inventory.getItem(slot))) return ItemStack.EMPTY;
        return this.inventory.removeItemNoUpdate(slot);
    }

    @Override
    public void setItem(int slot, @NonNull ItemStack stack) {
        if (!stack.isEmpty() && !this.canPlaceItem(slot, stack)) return;
        this.inventory.setItem(slot, stack);
    }

    @Override
    public boolean stillValid(@NonNull Player player) {
        return this.level != null && !this.isRemoved() && player.distanceToSqr(this.worldPosition.getCenter()) <= 64.0D;
    }

    @Override
    public void clearContent() {
        this.inventory.clearContent();
    }

    @Override
    public boolean canPlaceItem(int slot, @NonNull ItemStack stack) {
        if (stack.isEmpty() || this.kind == AlchemyInventoryKind.OUTPUT || slot < 0 || slot >= this.kind.slots())
            return false;
        AlchemyFurnaceBlockEntity furnace = this.controller();
        if (furnace != null && furnace.state().busy()) return false;
        if (furnace != null) {
            AlchemyFurnaceDefinition spec = furnace.furnaceDefinition().map(Holder::value).orElse(null);
            int logical = this.kind == AlchemyInventoryKind.MAIN ? slot : AlchemySlots.AUX_START + slot;
            if (spec != null && !AlchemySlots.enabled(logical, spec)) return false;
        }
        if (this.level == null) return false;
        int logical = this.kind == AlchemyInventoryKind.MAIN ? slot : AlchemySlots.AUX_START + slot;
        return SpiritHerbService.potency(this.level.registryAccess(), stack, AlchemyResolver.herbRole(AlchemySlots.role(logical)), FormulaContext.of(this.level))
                .filter(SpiritHerbService.HerbPotency::placeable).isPresent();
    }

    @Override
    public boolean canTakeItem(@NonNull Container target, int slot, @NonNull ItemStack stack) {
        return this.canTakeItem(slot, stack);
    }

    public boolean canTakeItem(int slot, @NonNull ItemStack stack) {
        if (slot < 0 || slot >= this.kind.slots() || stack.isEmpty()) return false;
        AlchemyFurnaceBlockEntity furnace = this.controller();
        return furnace == null || !furnace.state().busy() || this.kind == AlchemyInventoryKind.OUTPUT;
    }

    @Override
    public int @NonNull [] getSlotsForFace(@NonNull Direction side) {
        return this.kind == AlchemyInventoryKind.OUTPUT && side == Direction.DOWN ? OUTPUT_SLOTS : NONE;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, @NonNull ItemStack stack, @Nullable Direction side) {
        return false;
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, @NonNull ItemStack stack, @NonNull Direction side) {
        return this.kind == AlchemyInventoryKind.OUTPUT && side == Direction.DOWN && this.canTakeItem(slot, stack);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (this.controller != null && this.level instanceof ServerLevel server)
            server.scheduleTick(this.worldPosition, this.getBlockState().getBlock(), 1);
    }

    @Override
    public void preRemoveSideEffects(@NonNull BlockPos pos, @NonNull BlockState state) {
        if (this.level instanceof ServerLevel server) {
            if (!this.voidContents) {
                Containers.dropContents(server, pos, this.inventory);
                this.inventory.clearContent();
            }
            this.notifyController(server);
        }
        super.preRemoveSideEffects(pos, state);
    }

    private void notifyController(ServerLevel server) {
        if (this.controller == null || !server.isLoaded(this.controller)) return;
        if (!(server.getBlockEntity(this.controller) instanceof AlchemyFurnaceBlockEntity furnace)) return;
        if (!(furnace.getBlockState().getBlock() instanceof AlchemyFurnaceBlock) || !furnace.getBlockState().getValue(AlchemyFurnaceBlock.FORMED))
            return;
        Direction facing = furnace.getBlockState().getValue(AlchemyFurnaceBlock.FACING);
        AlchemyFurnaceStructure.release(server, this.controller, facing);
        furnace.onStructureLost();
    }

    @Override
    protected void loadAdditional(@NonNull ValueInput input) {
        super.loadAdditional(input);
        this.inventory.clearContent();
        List<ItemStack> items = input.read("items", ItemStack.OPTIONAL_CODEC.listOf()).orElse(List.of());
        for (int slot = 0; slot < Math.min(this.inventory.getContainerSize(), items.size()); slot++)
            this.inventory.setItem(slot, items.get(slot));
        this.controller = input.read("controller", BlockPos.CODEC).orElse(null);
    }

    @Override
    protected void saveAdditional(@NonNull ValueOutput output) {
        super.saveAdditional(output);
        output.store("items", ItemStack.OPTIONAL_CODEC.listOf(), this.inventory.getItems());
        if (this.controller != null) output.store("controller", BlockPos.CODEC, this.controller);
    }
}
