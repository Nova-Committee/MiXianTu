package com.iafenvoy.mxt.item.block.entity;

import com.iafenvoy.mxt.api.AlchemyWorkstation;
import com.iafenvoy.mxt.data.alchemy.AlchemyFurnaceDefinition;
import com.iafenvoy.mxt.data.alchemy.AlchemyWallMaterial;
import com.iafenvoy.mxt.item.block.AlchemyFurnaceBlock;
import com.iafenvoy.mxt.registry.MxtBlockEntities;
import com.iafenvoy.mxt.runtime.alchemy.*;
import com.iafenvoy.mxt.runtime.alchemy.AlchemyFurnaceStructure.Status;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Optional;

/**
 * Never implement Container here: vanilla removal and hoppers would consume the ports' live stacks.
 * Only container() exposes the logical transaction view; each physical part keeps its own inventory.
 * The heat source is a block in the bottom centre cell, not a slot here; the structure never claims that cell.
 */
public final class AlchemyFurnaceBlockEntity extends BlockEntity implements AlchemyWorkstation {
    private final AlchemyAggregateContainer inventory = new AlchemyAggregateContainer(this);
    private final AlchemyWorkstationState state = new AlchemyWorkstationState();
    private ItemStack furnaceItem = ItemStack.EMPTY;
    private boolean voidContents;
    private int structureTicker;

    public AlchemyFurnaceBlockEntity(BlockPos pos, BlockState blockState) {
        super(MxtBlockEntities.ALCHEMY_FURNACE.get(), pos, blockState);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState blockState, AlchemyFurnaceBlockEntity furnace) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        if (++furnace.structureTicker >= 20) {
            furnace.structureTicker = 0;
            furnace.refreshStructure();
        }
        AlchemyWorkstationService.tick(serverLevel, pos, furnace);
        // A completion block action may already have replaced this block. Do not write lit back over it.
        if (furnace.isRemoved() || !(level.getBlockState(pos).getBlock() instanceof AlchemyFurnaceBlock)) return;
        furnace.syncLit();
    }

    public void refreshStructure() {
        if (!(this.level instanceof ServerLevel server) || !(this.getBlockState().getBlock() instanceof AlchemyFurnaceBlock))
            return;
        Direction facing = this.getBlockState().getValue(AlchemyFurnaceBlock.FACING);
        Status status = AlchemyFurnaceStructure.inspect(server, this.worldPosition, facing);
        boolean formed = this.getBlockState().getValue(AlchemyFurnaceBlock.FORMED);
        if (status.complete() && !formed)
            AlchemyFurnaceStructure.form(server, this.worldPosition, facing, this.temperature() > 0.0D);
        else if (formed && (!status.missing().isEmpty() || !status.conflicts().isEmpty())) {
            AlchemyFurnaceStructure.release(server, this.worldPosition, facing);
            this.onStructureLost();
        }
    }

    public void onStructureLost() {
        if (this.level instanceof ServerLevel server)
            AlchemyWorkstationService.abort(server, this.worldPosition, this, AlchemyFailure.STRUCTURE);
    }

    public void acceptFurnaceItem(ItemStack stack) {
        this.furnaceItem = stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
        this.setChanged();
    }

    public void voidContents() {
        this.voidContents = true;
        this.state.session().ifPresent(AlchemySession::clearPending);
        this.state.clearSession();
        this.furnaceItem = ItemStack.EMPTY;
    }

    @Override
    public Container container() {
        return this.inventory;
    }

    @Override
    public AlchemyWorkstationState state() {
        return this.state;
    }

    @Override
    public ItemStack furnaceItem() {
        return this.furnaceItem;
    }

    @Override
    public Optional<Holder<AlchemyFurnaceDefinition>> furnaceDefinition() {
        return this.level == null ? Optional.empty() : AlchemyWorkstationService.furnaceDefinition(this.level.registryAccess(), this.furnaceItem);
    }

    // The loot table hands the core back, so its specification travels as a component instead of being popped here.
    @Override
    protected void collectImplicitComponents(DataComponentMap.@NonNull Builder components) {
        super.collectImplicitComponents(components);
        if (!this.voidContents && !this.furnaceItem.isEmpty()) components.addAll(this.furnaceItem.getComponents());
    }

    @Override
    public double temperature() {
        return this.state.temperature();
    }

    @Override
    public double targetTemperature() {
        return this.state.targetTemperature();
    }

    @Override
    public boolean setTargetTemperature(double temperature) {
        if (!Double.isFinite(temperature) || temperature < 0.0D || temperature > this.maximumTemperature())
            return false;
        this.state.setTargetTemperature(temperature);
        this.setChanged();
        return true;
    }

    @Override
    public void setTemperature(double temperature) {
        this.state.setTemperature(temperature);
    }

    @Override
    public BlockPos heatSourcePos() {
        return AlchemyFurnaceStructure.heatSource(this.worldPosition, this.facing());
    }

    @Override
    public double wallTemperatureLimit() {
        if (!(this.level instanceof ServerLevel server) || !(this.getBlockState().getBlock() instanceof AlchemyFurnaceBlock))
            return 0.0D;
        Direction facing = this.getBlockState().getValue(AlchemyFurnaceBlock.FACING);
        double minimum = Double.POSITIVE_INFINITY;
        int counted = 0;
        for (int index = 0; index < 27; index++) {
            if (!AlchemyFurnaceStructure.wall(index)) continue;
            BlockPos pos = AlchemyFurnaceStructure.world(this.worldPosition, facing, index);
            if (!server.isLoaded(pos) || !(server.getBlockEntity(pos) instanceof AlchemyFurnaceCasingBlockEntity casing))
                return 0.0D;
            Optional<Holder<AlchemyWallMaterial>> material = casing.material();
            if (material.isEmpty()) return 0.0D;
            minimum = Math.min(minimum, material.get().value().maxTemperature());
            counted++;
        }
        // Counted against the structure's own list, never a written number: a wall added or dropped there would
        // otherwise silently turn this limit into zero.
        return counted == AlchemyFurnaceStructure.walls().length && Double.isFinite(minimum) ? minimum : 0.0D;
    }

    @Override
    public double heatTemperatureLimit() {
        if (!(this.level instanceof ServerLevel server)) return 0.0D;
        return AlchemyHeatService.maxTemperature(server, this.heatSourcePos());
    }

    @Override
    public double maximumTemperature() {
        AlchemyFurnaceDefinition spec = this.furnaceDefinition().map(Holder::value).orElse(null);
        return AlchemyFurnaceDefinition.temperatureLimit(this.wallTemperatureLimit(), this.heatTemperatureLimit(), spec);
    }

    @Override
    public Status structureStatus() {
        if (this.level == null || !(this.getBlockState().getBlock() instanceof AlchemyFurnaceBlock))
            return new Status(false, false, List.of(), List.of(), List.of(), this.worldPosition, Direction.NORTH);
        return AlchemyFurnaceStructure.inspect(this.level, this.worldPosition, this.getBlockState().getValue(AlchemyFurnaceBlock.FACING));
    }

    @Override
    public AlchemyPhase phase() {
        return this.state.phase();
    }

    @Override
    public void preRemoveSideEffects(@NonNull BlockPos pos, @NonNull BlockState state) {
        if (this.level instanceof ServerLevel server && state.getBlock() instanceof AlchemyFurnaceBlock) {
            AlchemyFurnaceStructure.release(server, pos, state.getValue(AlchemyFurnaceBlock.FACING));
            if (!this.voidContents)
                this.state.session().ifPresent(session -> session.pendingOutputs().forEach(stack -> Block.popResource(server, pos, stack)));
            this.state.clearSession();
        }
        super.preRemoveSideEffects(pos, state);
    }

    @Override
    protected void loadAdditional(@NonNull ValueInput input) {
        super.loadAdditional(input);
        this.furnaceItem = input.read("furnace_item", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
        if (!this.furnaceItem.isEmpty()) this.furnaceItem.setCount(1);
        AlchemyWorkstationState loaded = input.read("state", AlchemyWorkstationState.CODEC).orElseGet(AlchemyWorkstationState::new);
        this.state.clearSession();
        loaded.session().ifPresent(this.state::begin);
        this.state.setTemperature(loaded.temperature());
        this.state.setTargetTemperature(loaded.targetTemperature());
    }

    @Override
    protected void saveAdditional(@NonNull ValueOutput output) {
        super.saveAdditional(output);
        output.store("furnace_item", ItemStack.OPTIONAL_CODEC, this.furnaceItem);
        output.store("state", AlchemyWorkstationState.CODEC, this.state);
    }

    // Every reader of the shell's facing goes through here: the state is not ours while the block is being replaced.
    private Direction facing() {
        return this.getBlockState().getBlock() instanceof AlchemyFurnaceBlock
                ? this.getBlockState().getValue(AlchemyFurnaceBlock.FACING) : Direction.NORTH;
    }

    private void syncLit() {
        if (!(this.level instanceof ServerLevel server) || this.isRemoved()) return;
        BlockState world = server.getBlockState(this.worldPosition);
        if (!(world.getBlock() instanceof AlchemyFurnaceBlock)) return;
        boolean lit = this.temperature() > 0.0D;
        if (world.getValue(AlchemyFurnaceBlock.LIT) != lit) {
            server.setBlock(this.worldPosition, world.setValue(AlchemyFurnaceBlock.LIT, lit), 3);
            if (this.isRemoved()) return;
            world = server.getBlockState(this.worldPosition);
            if (!(world.getBlock() instanceof AlchemyFurnaceBlock)) return;
        }
        if (world.getValue(AlchemyFurnaceBlock.FORMED))
            AlchemyFurnaceStructure.syncLit(server, this.worldPosition, world.getValue(AlchemyFurnaceBlock.FACING), lit);
    }
}
