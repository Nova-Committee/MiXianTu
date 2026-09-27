package com.iafenvoy.mxt.item.block.entity;

import com.iafenvoy.mxt.api.AlchemyHeatSource;
import com.iafenvoy.mxt.api.AlchemyWorkstation;
import com.iafenvoy.mxt.data.alchemy.AlchemyFurnaceDefinition;
import com.iafenvoy.mxt.data.alchemy.AlchemyWallMaterial;
import com.iafenvoy.mxt.item.block.AlchemyFurnaceBlock;
import com.iafenvoy.mxt.item.block.AlchemyFurnaceCasingBlock;
import com.iafenvoy.mxt.registry.MxtBlockEntities;
import com.iafenvoy.mxt.runtime.alchemy.AlchemyAggregateContainer;
import com.iafenvoy.mxt.runtime.alchemy.AlchemyFailure;
import com.iafenvoy.mxt.runtime.alchemy.AlchemyFurnaceStructure;
import com.iafenvoy.mxt.runtime.alchemy.AlchemyFurnaceStructure.Status;
import com.iafenvoy.mxt.runtime.alchemy.AlchemyPhase;
import com.iafenvoy.mxt.runtime.alchemy.AlchemyWorkstationService;
import com.iafenvoy.mxt.runtime.alchemy.AlchemyWorkstationState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
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
 */
public final class AlchemyFurnaceBlockEntity extends BlockEntity implements AlchemyWorkstation {
    private final AlchemyAggregateContainer inventory = new AlchemyAggregateContainer(this);
    private final SimpleContainer fire = new SimpleContainer(1) {
        @Override
        public void setChanged() {
            AlchemyFurnaceBlockEntity.this.setChanged();
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public boolean canPlaceItem(int slot, @NonNull ItemStack stack) {
            return AlchemyFurnaceBlockEntity.this.canPlaceFire(stack);
        }

        @Override
        public void setItem(int slot, @NonNull ItemStack stack) {
            if (!AlchemyFurnaceBlockEntity.this.loading
                    && (stack.isEmpty() ? !AlchemyFurnaceBlockEntity.this.canTakeFire() : !this.canPlaceItem(slot, stack))) return;
            super.setItem(slot, stack.isEmpty() || stack.getCount() == 1 ? stack : stack.copyWithCount(1));
        }

        @Override
        public @NonNull ItemStack removeItem(int slot, int count) {
            if (!AlchemyFurnaceBlockEntity.this.loading && !AlchemyFurnaceBlockEntity.this.canTakeFire()) return ItemStack.EMPTY;
            return super.removeItem(slot, count);
        }

        @Override
        public @NonNull ItemStack removeItemNoUpdate(int slot) {
            if (!AlchemyFurnaceBlockEntity.this.loading && !AlchemyFurnaceBlockEntity.this.canTakeFire()) return ItemStack.EMPTY;
            return super.removeItemNoUpdate(slot);
        }
    };
    private final AlchemyWorkstationState state = new AlchemyWorkstationState();
    private ItemStack furnaceItem = ItemStack.EMPTY;
    private boolean voidContents;
    private boolean loading;
    private int structureTicker;

    public AlchemyFurnaceBlockEntity(BlockPos pos, BlockState blockState) {
        super(MxtBlockEntities.ALCHEMY_FURNACE.get(), pos, blockState);
    }

    public static void serverTick(ServerLevel level, BlockPos pos, BlockState blockState, AlchemyFurnaceBlockEntity furnace) {
        if (++furnace.structureTicker >= 20) {
            furnace.structureTicker = 0;
            furnace.refreshStructure();
        }
        AlchemyWorkstationService.tick(level, pos, furnace);
        // A completion block action may already have replaced this block. Do not write lit back over it.
        if (furnace.isRemoved() || !(level.getBlockState(pos).getBlock() instanceof AlchemyFurnaceBlock)) return;
        furnace.syncLit();
    }

    public void refreshStructure() {
        if (!(this.level instanceof ServerLevel server) || !(this.getBlockState().getBlock() instanceof AlchemyFurnaceBlock)) return;
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
        this.fire.clearContent();
        this.state.session().ifPresent(session -> session.clearPending());
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
        if (!Double.isFinite(temperature) || temperature < 0.0D || temperature > this.maximumTemperature()) return false;
        this.state.setTargetTemperature(temperature);
        this.setChanged();
        return true;
    }

    @Override
    public void setTemperature(double temperature) {
        this.state.setTemperature(temperature);
    }

    @Override
    public Container fireContainer() {
        return this.fire;
    }

    @Override
    public boolean canPlaceFire(ItemStack stack) {
        return !this.state.busy() && stack.getItem() instanceof AlchemyHeatSource;
    }

    @Override
    public boolean canTakeFire() {
        return !this.state.busy();
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
        return counted == 22 && Double.isFinite(minimum) ? minimum : 0.0D;
    }

    @Override
    public double fireTemperatureLimit() {
        if (!(this.level instanceof ServerLevel server)) return 0.0D;
        ItemStack stack = this.fire.getItem(0);
        if (!(stack.getItem() instanceof AlchemyHeatSource source)) return 0.0D;
        double value = source.maxTemperature(stack, server, this.worldPosition);
        return Double.isFinite(value) && value > 0.0D ? value : 0.0D;
    }

    @Override
    public double maximumTemperature() {
        double wall = this.wallTemperatureLimit();
        double fireLimit = this.fireTemperatureLimit();
        if (wall <= 0.0D || fireLimit <= 0.0D) return 0.0D;
        return Math.min(wall, fireLimit);
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
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (this.level instanceof ServerLevel server && state.getBlock() instanceof AlchemyFurnaceBlock) {
            AlchemyFurnaceStructure.release(server, pos, state.getValue(AlchemyFurnaceBlock.FACING));
            if (!this.voidContents) {
                if (!this.furnaceItem.isEmpty()) Block.popResource(server, pos, this.furnaceItem.copy());
                ItemStack flame = this.fire.getItem(0);
                if (!flame.isEmpty()) Block.popResource(server, pos, flame.copy());
                this.state.session().ifPresent(session -> session.pendingOutputs().forEach(stack -> Block.popResource(server, pos, stack)));
            }
            this.furnaceItem = ItemStack.EMPTY;
            this.fire.clearContent();
            this.state.clearSession();
        }
        super.preRemoveSideEffects(pos, state);
    }

    @Override
    protected void loadAdditional(@NonNull ValueInput input) {
        super.loadAdditional(input);
        this.loading = true;
        try {
            this.furnaceItem = input.read("furnace_item", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
            if (!this.furnaceItem.isEmpty()) this.furnaceItem.setCount(1);
            this.fire.setItem(0, input.read("fire", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY));
            if (!this.fire.getItem(0).isEmpty()) this.fire.getItem(0).setCount(1);
            AlchemyWorkstationState loaded = input.read("state", AlchemyWorkstationState.CODEC).orElseGet(AlchemyWorkstationState::new);
            this.state.clearSession();
            loaded.session().ifPresent(this.state::begin);
            this.state.setTemperature(loaded.temperature());
            this.state.setTargetTemperature(loaded.targetTemperature());
        } finally {
            this.loading = false;
        }
    }

    @Override
    protected void saveAdditional(@NonNull ValueOutput output) {
        super.saveAdditional(output);
        output.store("furnace_item", ItemStack.OPTIONAL_CODEC, this.furnaceItem);
        output.store("fire", ItemStack.OPTIONAL_CODEC, this.fire.getItem(0));
        output.store("state", AlchemyWorkstationState.CODEC, this.state);
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
