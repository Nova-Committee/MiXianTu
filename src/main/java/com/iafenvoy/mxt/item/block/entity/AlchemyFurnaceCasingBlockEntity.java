package com.iafenvoy.mxt.item.block.entity;

import com.iafenvoy.mxt.data.alchemy.AlchemyWallMaterial;
import com.iafenvoy.mxt.item.block.AlchemyFurnaceBlock;
import com.iafenvoy.mxt.item.block.AlchemyFurnaceCasingBlock;
import com.iafenvoy.mxt.registry.MxtBlockEntities;
import com.iafenvoy.mxt.registry.MxtDataComponents;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.alchemy.AlchemyFurnaceStructure;
import com.iafenvoy.mxt.util.HolderHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import java.util.Optional;

/**
 * Remembers which controller claimed this shell cell and which wall item was placed. The loot table hands the wall
 * item back; the cell never drops the controller or any port inventory.
 */
public final class AlchemyFurnaceCasingBlockEntity extends BlockEntity {
    private BlockPos controller;
    private ItemStack wallItem = ItemStack.EMPTY;
    private boolean voidContents;

    public AlchemyFurnaceCasingBlockEntity(BlockPos pos, BlockState state) {
        super(MxtBlockEntities.ALCHEMY_FURNACE_CASING.get(), pos, state);
    }

    public @Nullable BlockPos controller() {
        return this.controller;
    }

    public ItemStack wallItem() {
        return this.wallItem;
    }

    public void acceptWallItem(ItemStack stack) {
        this.wallItem = stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
        this.setChanged();
    }

    public Optional<Holder<AlchemyWallMaterial>> material() {
        if (this.wallItem.isEmpty() || this.level == null) return Optional.empty();
        Holder<AlchemyWallMaterial> stored = this.wallItem.get(MxtDataComponents.ALCHEMY_WALL_MATERIAL.get());
        if (stored == null) return Optional.empty();
        Identifier id = HolderHelper.id(stored);
        if (id.equals(HolderHelper.EMPTY)) return Optional.empty();
        Optional<Holder<AlchemyWallMaterial>> loaded = MxtDatapackRegistries.holder(this.level.registryAccess(),
                MxtResourceKeys.ALCHEMY_WALL_MATERIAL, id).map(holder -> holder);
        if (loaded.isEmpty()) return Optional.empty();
        double max = loaded.get().value().maxTemperature();
        return Double.isFinite(max) && max > 0.0D ? loaded : Optional.empty();
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
                && furnace.getBlockState().getBlock() instanceof AlchemyFurnaceBlock
                && furnace.getBlockState().getValue(AlchemyFurnaceBlock.FORMED);
    }

    public void voidContents() {
        this.voidContents = true;
        this.wallItem = ItemStack.EMPTY;
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (!this.voidContents && !this.wallItem.isEmpty()) components.addAll(this.wallItem.getComponents());
    }

    public void validateClaim() {
        if (this.controller == null || !(this.level instanceof ServerLevel server)) return;
        if (!server.isLoaded(this.controller)) {
            server.scheduleTick(this.worldPosition, this.getBlockState().getBlock(), 20);
            return;
        }
        if (server.getBlockEntity(this.controller) instanceof AlchemyFurnaceBlockEntity furnace
                && furnace.getBlockState().getBlock() instanceof AlchemyFurnaceBlock
                && furnace.getBlockState().getValue(AlchemyFurnaceBlock.FORMED)) return;
        this.clearClaim();
        server.setBlock(this.worldPosition, this.getBlockState().setValue(AlchemyFurnaceCasingBlock.PART, 0)
                .setValue(AlchemyFurnaceCasingBlock.LIT, false), 3);
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (this.level instanceof ServerLevel server && this.controller != null && server.isLoaded(this.controller)
                && server.getBlockEntity(this.controller) instanceof AlchemyFurnaceBlockEntity furnace
                && furnace.getBlockState().getBlock() instanceof AlchemyFurnaceBlock
                && furnace.getBlockState().getValue(AlchemyFurnaceBlock.FORMED)) {
            Direction facing = furnace.getBlockState().getValue(AlchemyFurnaceBlock.FACING);
            AlchemyFurnaceStructure.release(server, this.controller, facing);
            furnace.onStructureLost();
        }
        super.preRemoveSideEffects(pos, state);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (this.controller != null && this.level instanceof ServerLevel server)
            server.scheduleTick(this.worldPosition, this.getBlockState().getBlock(), 1);
    }

    @Override
    protected void loadAdditional(@NonNull ValueInput input) {
        super.loadAdditional(input);
        this.controller = input.read("controller", BlockPos.CODEC).orElse(null);
        this.wallItem = input.read("wall_item", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
        if (!this.wallItem.isEmpty()) this.wallItem.setCount(1);
    }

    @Override
    protected void saveAdditional(@NonNull ValueOutput output) {
        super.saveAdditional(output);
        if (this.controller != null) output.store("controller", BlockPos.CODEC, this.controller);
        output.store("wall_item", ItemStack.OPTIONAL_CODEC, this.wallItem);
    }
}
