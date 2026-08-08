package com.iafenvoy.mxt.item.block.entity;

import com.iafenvoy.mxt.screen.menu.StationMenu;
import com.iafenvoy.mxt.screen.menu.StationMenu.Mode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.UUID;

/**
 * Shared persistent offer state for system and player-owned trade stations.
 */
public abstract class StationBlockEntity extends BlockEntity implements MenuProvider {
    private final SimpleContainer costs = this.tracked(12);
    private final SimpleContainer rewards = this.tracked(12);
    private final SimpleContainer display = this.tracked(1);
    private final SimpleContainer stock;
    private UUID owner;

    protected StationBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, boolean keepsStock) {
        super(type, pos, state);
        this.stock = keepsStock ? this.tracked(21) : null;
    }

    private SimpleContainer tracked(int size) {
        return new SimpleContainer(size) {
            @Override
            public void setChanged() {
                StationBlockEntity.this.markChangedAndSync();
            }
        };
    }

    public final SimpleContainer costs() {
        return this.costs;
    }

    public final SimpleContainer rewards() {
        return this.rewards;
    }

    public final SimpleContainer display() {
        return this.display;
    }

    public final SimpleContainer stock() {
        return this.stock;
    }

    public final ItemStack displayStack() {
        return this.isSystemStation() ? this.rewards.getItem(0) : this.display.getItem(0);
    }

    public final UUID owner() {
        return this.owner;
    }

    public final void setOwner(UUID owner) {
        this.owner = owner;
        this.markChangedAndSync();
    }

    public abstract boolean isSystemStation();

    @Override
    public @NonNull Component getDisplayName() {
        return Component.translatable(this.isSystemStation() ? "screen.mxt.system_station" : "screen.mxt.trade_station");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, @NonNull Inventory inventory, @NonNull Player player) {
        Mode mode;
        if (this.isSystemStation()) mode = player instanceof ServerPlayer serverPlayer
                && serverPlayer.level().getServer().getPlayerList().isOp(serverPlayer.nameAndId())
                && player.isCreative() && player.isShiftKeyDown()
                ? Mode.SYSTEM_OWNER : Mode.SYSTEM_CUSTOMER;
        else mode = player.getUUID().equals(this.owner) ? Mode.TRADE_OWNER : Mode.TRADE_CUSTOMER;
        return new StationMenu(mode, containerId, inventory, this.costs, this.rewards, this.stock, this.display,
                ContainerLevelAccess.create(player.level(), this.worldPosition));
    }

    @Override
    protected void loadAdditional(@NonNull ValueInput input) {
        super.loadAdditional(input);
        input.read("owner", UUIDUtil.CODEC).ifPresent(value -> this.owner = value);
        load(this.costs, input.read("costs", ItemStack.OPTIONAL_CODEC.listOf()).orElse(List.of()));
        load(this.rewards, input.read("rewards", ItemStack.OPTIONAL_CODEC.listOf()).orElse(List.of()));
        load(this.display, input.read("display", ItemStack.OPTIONAL_CODEC.listOf()).orElse(List.of()));
        if (this.stock != null)
            load(this.stock, input.read("stock", ItemStack.OPTIONAL_CODEC.listOf()).orElse(List.of()));
    }

    @Override
    protected void saveAdditional(@NonNull ValueOutput output) {
        super.saveAdditional(output);
        if (this.owner != null) output.store("owner", UUIDUtil.CODEC, this.owner);
        output.store("costs", ItemStack.OPTIONAL_CODEC.listOf(), this.costs.getItems());
        output.store("rewards", ItemStack.OPTIONAL_CODEC.listOf(), this.rewards.getItems());
        output.store("display", ItemStack.OPTIONAL_CODEC.listOf(), this.display.getItems());
        if (this.stock != null) output.store("stock", ItemStack.OPTIONAL_CODEC.listOf(), this.stock.getItems());
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
        if (this.level != null && !this.level.isClientSide()) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
        }
    }

    private static void load(SimpleContainer container, List<ItemStack> values) {
        container.clearContent();
        for (int index = 0; index < Math.min(container.getContainerSize(), values.size()); index++)
            container.setItem(index, values.get(index));
    }
}
