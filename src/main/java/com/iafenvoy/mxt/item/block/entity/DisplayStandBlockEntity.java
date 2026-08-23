package com.iafenvoy.mxt.item.block.entity;

import com.iafenvoy.mxt.registry.MxtBlockEntities;
import com.iafenvoy.mxt.data.cultivation.Element;
import com.iafenvoy.mxt.runtime.spirit.SpiritAccess;
import com.iafenvoy.mxt.runtime.spirit.SpiritItemAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.NonNull;

/**
 * Persistent displayed stack shared by every wooden display stand variant.
 */
public final class DisplayStandBlockEntity extends BlockEntity implements SpiritAccess {
    private ItemStack displayedItem = ItemStack.EMPTY;

    public DisplayStandBlockEntity(BlockPos pos, BlockState state) {
        super(MxtBlockEntities.DISPLAY_STAND.get(), pos, state);
    }

    public ItemStack displayedItem() {
        return this.displayedItem;
    }

    public void setDisplayedItem(ItemStack displayedItem) {
        this.displayedItem = displayedItem;
        this.markChangedAndSync();
    }

    public ItemStack removeDisplayedItem() {
        ItemStack displayed = this.displayedItem;
        this.setDisplayedItem(ItemStack.EMPTY);
        return displayed;
    }

    @Override
    public int add(Holder<Element> type, int amount, boolean simulate) {
        SpiritAccess.requireNonNegative(amount);
        if (!(this.displayedItem.getItem() instanceof SpiritItemAccess access)) return amount;
        if (this.level == null) return amount;
        int remaining = access.add(this.displayedItem, type, amount, simulate);
        if (!simulate && remaining != amount) this.markChangedAndSync();
        return remaining;
    }

    @Override
    public int extract(Holder<Element> type, int amount, boolean simulate) {
        return SpiritAccess.requireNonNegative(amount);
    }

    @Override
    protected void loadAdditional(@NonNull ValueInput input) {
        super.loadAdditional(input);
        this.displayedItem = input.read("displayed_item", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
    }

    @Override
    protected void saveAdditional(@NonNull ValueOutput output) {
        super.saveAdditional(output);
        output.store("displayed_item", ItemStack.OPTIONAL_CODEC, this.displayedItem);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public @NonNull CompoundTag getUpdateTag(@NonNull Provider registries) {
        return this.saveWithoutMetadata(registries);
    }

    private void markChangedAndSync() {
        this.setChanged();
        if (this.level != null && !this.level.isClientSide())
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
    }
}
