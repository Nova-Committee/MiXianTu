package com.iafenvoy.mxt.item.block.entity;

import com.iafenvoy.mxt.api.AuraAccess;
import com.iafenvoy.mxt.api.ItemAuraAccess;
import com.iafenvoy.mxt.api.UseItemAuraAccess;
import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.registry.MxtBlockEntities;
import com.iafenvoy.mxt.runtime.spirit.SpiritSource;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

/**
 * Persistent displayed stack shared by every wooden display stand variant.
 */
public final class DisplayStandBlockEntity extends BlockEntity implements AuraAccess {
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
    public Object2IntMap<Holder<Aura>> getCapacity(@Nullable LivingEntity entity) {
        if (this.displayedItem.getItem() instanceof ItemAuraAccess access)
            return access.getCapacity(entity, this.displayedItem);
        return new Object2IntOpenHashMap<>();
    }

    @Override
    public int insert(@Nullable LivingEntity entity, Holder<Aura> aura, int amount, boolean simulate) {
        AuraAccess.requireNonNegative(amount);
        // Only a store that asked to be poured into is filled here: the reading is the storing interface, the
        // report is the manual one.
        if (!(this.displayedItem.getItem() instanceof ItemAuraAccess access)) return amount;
        if (this.level == null) return amount;
        int remaining = access.insert(entity, this.displayedItem, aura, amount, simulate);
        if (!simulate && remaining != amount && access instanceof UseItemAuraAccess manual) {
            // The store is in nobody's hands, so the place it is at travels with the report instead of being read
            // off the entity - and the item may spend itself in answer, which is a change this stand has to publish.
            manual.onCharged(SpiritSource.placed(this.level, this.worldPosition.getCenter(), entity), this.displayedItem);
            this.markChangedAndSync();
        }
        return remaining;
    }

    @Override
    public int extract(@Nullable LivingEntity entity, Holder<Aura> aura, int amount, boolean simulate) {
        return AuraAccess.requireNonNegative(amount);
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
