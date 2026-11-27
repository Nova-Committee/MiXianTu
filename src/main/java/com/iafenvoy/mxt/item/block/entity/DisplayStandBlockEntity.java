package com.iafenvoy.mxt.item.block.entity;

import com.iafenvoy.mxt.api.AuraHandler;
import com.iafenvoy.mxt.api.UseItemAuraAccess;
import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.registry.MxtBlockEntities;
import com.iafenvoy.mxt.registry.MxtCapabilities;
import com.iafenvoy.mxt.runtime.spirit.AuraHandlerUtil;
import com.iafenvoy.mxt.runtime.spirit.SpiritSource;
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
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.item.ItemStackResourceHandler;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import java.util.Optional;
import java.util.Set;

/**
 * Persistent displayed stack shared by every wooden display stand variant.
 */
public final class DisplayStandBlockEntity extends BlockEntity {
    private ItemStack displayedItem = ItemStack.EMPTY;
    private final ItemStackResourceHandler slot = new ItemStackResourceHandler() {
        @Override
        protected @NonNull ItemStack getStack() {
            return DisplayStandBlockEntity.this.displayedItem;
        }

        @Override
        protected void setStack(@NonNull ItemStack stack) {
            DisplayStandBlockEntity.this.displayedItem = stack;
        }

        @Override
        protected void onRootCommit(ItemStack original) {
            if (DisplayStandBlockEntity.this.isRemoved() || ItemStack.matches(original, DisplayStandBlockEntity.this.displayedItem))
                return;
            if (DisplayStandBlockEntity.this.pendingCharge == null || DisplayStandBlockEntity.this.pendingCharge.stack() != DisplayStandBlockEntity.this.displayedItem)
                DisplayStandBlockEntity.this.markChangedAndSync();
        }
    };
    private final ItemAccess displayedAccess = ItemAccess.forHandlerIndexStrict(this.slot, 0);
    private @Nullable Charge pendingCharge;
    private final SnapshotJournal<Optional<Charge>> chargeJournal = new SnapshotJournal<>() {
        @Override
        protected Optional<Charge> createSnapshot() {
            return Optional.ofNullable(DisplayStandBlockEntity.this.pendingCharge);
        }

        @Override
        protected void revertToSnapshot(@NotNull Optional<Charge> snapshot) {
            DisplayStandBlockEntity.this.pendingCharge = snapshot.orElse(null);
        }

        @Override
        protected void onRootCommit(Optional<Charge> original) {
            Charge charge = DisplayStandBlockEntity.this.pendingCharge;
            DisplayStandBlockEntity.this.pendingCharge = null;
            if (charge == null || DisplayStandBlockEntity.this.displayedItem != charge.stack() || DisplayStandBlockEntity.this.isRemoved() || DisplayStandBlockEntity.this.level == null
                    || !(DisplayStandBlockEntity.this.displayedItem.getItem() instanceof UseItemAuraAccess manual))
                return;
            try {
                manual.onCharged(SpiritSource.placed(DisplayStandBlockEntity.this.level, DisplayStandBlockEntity.this.worldPosition.getCenter(), charge.actor()), DisplayStandBlockEntity.this.displayedItem);
            } finally {
                DisplayStandBlockEntity.this.markChangedAndSync();
            }
        }
    };

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

    // The stand intercepts bursts even with an empty slot.
    public AuraHandler auraHandler(@Nullable LivingEntity actor) {
        return new StandAuraHandler(actor);
    }

    private @Nullable AuraHandler itemHandler(@Nullable LivingEntity actor) {
        if (this.level == null || this.level.isClientSide() || this.isRemoved() || this.displayedItem.isEmpty())
            return null;
        return MxtCapabilities.findItem(this.displayedItem,
                new MxtCapabilities.ItemContext(this.displayedAccess, this.level, actor, this));
    }

    private record Charge(ItemStack stack, @Nullable LivingEntity actor) {
    }

    private final class StandAuraHandler implements AuraHandler {
        private final @Nullable LivingEntity actor;

        private StandAuraHandler(@Nullable LivingEntity actor) {
            this.actor = actor;
        }

        @Override
        public Set<Holder<Aura>> getAuras() {
            AuraHandler handler = DisplayStandBlockEntity.this.itemHandler(this.actor);
            return handler == null ? Set.of() : handler.getAuras();
        }

        @Override
        public double getAmount(Holder<Aura> aura) {
            AuraHandler handler = DisplayStandBlockEntity.this.itemHandler(this.actor);
            return handler == null ? 0.0D : handler.getAmount(aura);
        }

        @Override
        public double getCapacity(Holder<Aura> aura) {
            AuraHandler handler = DisplayStandBlockEntity.this.itemHandler(this.actor);
            return handler == null ? 0.0D : handler.getCapacity(aura);
        }

        @Override
        public Object storageIdentity(Holder<Aura> aura) {
            return DisplayStandBlockEntity.this;
        }

        @Override
        public double insert(Holder<Aura> aura, double amount, TransactionContext transaction) {
            AuraHandlerUtil.requireNonNegative(amount);
            AuraHandler handler = DisplayStandBlockEntity.this.itemHandler(this.actor);
            if (handler == null) return 0.0D;
            ItemStack before = DisplayStandBlockEntity.this.displayedItem;
            double inserted = handler.insert(aura, amount, transaction);
            if (inserted > 0.0D && DisplayStandBlockEntity.this.displayedItem.getItem() instanceof UseItemAuraAccess) {
                DisplayStandBlockEntity.this.chargeJournal.updateSnapshots(transaction);
                DisplayStandBlockEntity.this.pendingCharge = new Charge(DisplayStandBlockEntity.this.displayedItem,
                        DisplayStandBlockEntity.this.pendingCharge != null && DisplayStandBlockEntity.this.pendingCharge.stack() == before ? DisplayStandBlockEntity.this.pendingCharge.actor() : this.actor);
            }
            return inserted;
        }

        @Override
        public double extract(Holder<Aura> aura, double amount, TransactionContext transaction) {
            AuraHandlerUtil.requireNonNegative(amount);
            return 0.0D;
        }
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
