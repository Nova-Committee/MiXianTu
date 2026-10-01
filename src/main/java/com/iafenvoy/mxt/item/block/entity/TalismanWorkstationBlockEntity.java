package com.iafenvoy.mxt.item.block.entity;

import com.iafenvoy.mxt.registry.MxtBlockEntities;
import com.iafenvoy.mxt.screen.menu.TalismanWorkstationMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.NonNull;

import java.util.List;

/**
 * Placed drawing workstation: two shared, persistent station slots, one for paper and one for pigment. The drawing
 * session itself is not stored here - it lives in the menu for as long as a player has the screen open.
 */
public class TalismanWorkstationBlockEntity extends BlockEntity implements MenuProvider {
    private final SimpleContainer paper = new SimpleContainer(1) {
        @Override
        public void setChanged() {
            TalismanWorkstationBlockEntity.this.markChangedAndSync();
        }
    };
    private final SimpleContainer pigment = new SimpleContainer(1) {
        @Override
        public void setChanged() {
            TalismanWorkstationBlockEntity.this.markChangedAndSync();
        }
    };

    public TalismanWorkstationBlockEntity(BlockPos pos, BlockState state) {
        super(MxtBlockEntities.TALISMAN_WORKSTATION.get(), pos, state);
    }

    public SimpleContainer paper() {
        return this.paper;
    }

    public SimpleContainer pigment() {
        return this.pigment;
    }

    @Override
    public @NonNull Component getDisplayName() {
        return Component.translatable("block.mxt.talisman_workstation");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, @NonNull Inventory inventory, @NonNull Player player) {
        return new TalismanWorkstationMenu(containerId, inventory, this);
    }

    @Override
    protected void loadAdditional(@NonNull ValueInput input) {
        super.loadAdditional(input);
        load(input, "paper", this.paper);
        load(input, "pigment", this.pigment);
    }

    @Override
    protected void saveAdditional(@NonNull ValueOutput output) {
        super.saveAdditional(output);
        output.store("paper", ItemStack.OPTIONAL_CODEC.listOf(), this.paper.getItems());
        output.store("pigment", ItemStack.OPTIONAL_CODEC.listOf(), this.pigment.getItems());
    }

    // An empty slot has to survive the round trip as empty: the optional codec is what keeps a list item from
    // failing the whole block entity encode.
    private static void load(ValueInput input, String key, SimpleContainer container) {
        List<ItemStack> values = input.read(key, ItemStack.OPTIONAL_CODEC.listOf()).orElse(List.of());
        container.clearContent();
        for (int index = 0; index < Math.min(container.getContainerSize(), values.size()); index++)
            container.setItem(index, values.get(index));
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
