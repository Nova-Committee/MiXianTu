package com.iafenvoy.mxt.item.block.entity;

import com.iafenvoy.mxt.screen.menu.StationMenu;
import com.iafenvoy.mxt.screen.menu.StationMenu.Mode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

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
    private @Nullable EntityReference<Player> owner;
    private ItemStack syncedDisplay = ItemStack.EMPTY;

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
        return this.level != null && this.level.isClientSide() ? this.syncedDisplay : this.displayTemplate();
    }

    private ItemStack displayTemplate() {
        return this.isSystemStation() ? this.rewards.getItem(0) : this.display.getItem(0);
    }

    public final @Nullable UUID owner() {
        return this.owner == null ? null : this.owner.getUUID();
    }

    public final void setOwner(@Nullable UUID owner) {
        this.owner = owner == null ? null : EntityReference.of(owner);
        this.markChangedAndSync();
    }

    public abstract boolean isSystemStation();

    @Override
    public void preRemoveSideEffects(@NonNull BlockPos pos, @NonNull BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (this.stock == null || !(this.level instanceof ServerLevel level)) return;
        for (int slot = 0; slot < this.stock.getContainerSize(); slot++) {
            ItemStack stack = this.stock.removeItemNoUpdate(slot);
            if (!stack.isEmpty()) Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack);
        }
    }

    @Override
    public @NonNull Component getDisplayName() {
        return Component.translatable(this.isSystemStation() ? "screen.mxt.system_station" : "screen.mxt.trade_station");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, @NonNull Inventory inventory, @NonNull Player player) {
        Mode mode = this.isSystemStation() ? (player.isShiftKeyDown() && this.mayManage(player)
                ? Mode.SYSTEM_OWNER : Mode.SYSTEM_CUSTOMER) : (this.mayManage(player) ? Mode.TRADE_OWNER : Mode.TRADE_CUSTOMER);
        return new StationMenu(mode, containerId, inventory, this);
    }

    public boolean mayUseMenu(Player player, Mode mode) {
        if (mode.isSystem() != this.isSystemStation()) return false;
        if (this.isSystemStation()) return !mode.isOwner() || this.mayManage(player);
        return mode.isOwner() == this.mayManage(player);
    }

    private boolean mayManage(Player player) {
        if (!this.isSystemStation()) return this.owner != null && this.owner.matches(player);
        return player instanceof ServerPlayer serverPlayer && player.isCreative()
                && serverPlayer.level().getServer().getPlayerList().isOp(serverPlayer.nameAndId());
    }

    @Override
    protected void loadAdditional(@NonNull ValueInput input) {
        super.loadAdditional(input);
        this.owner = EntityReference.read(input, "owner");
        load(this.costs, input.read("costs", ItemStack.OPTIONAL_CODEC.listOf()).orElse(List.of()));
        load(this.rewards, input.read("rewards", ItemStack.OPTIONAL_CODEC.listOf()).orElse(List.of()));
        load(this.display, input.read("display", ItemStack.OPTIONAL_CODEC.listOf()).orElse(List.of()));
        if (this.stock != null)
            load(this.stock, input.read("stock", ItemStack.OPTIONAL_CODEC.listOf()).orElse(List.of()));
    }

    @Override
    protected void saveAdditional(@NonNull ValueOutput output) {
        super.saveAdditional(output);
        EntityReference.store(this.owner, output, "owner");
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
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
        output.store("display_stack", ItemStack.OPTIONAL_CODEC, this.displayTemplate());
        return output.buildResult();
    }

    @Override
    public void handleUpdateTag(ValueInput input) {
        this.syncedDisplay = input.read("display_stack", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
    }

    @Override
    public void onDataPacket(@NonNull Connection connection, @NonNull ValueInput input) {
        this.handleUpdateTag(input);
    }

    private void markChangedAndSync() {
        this.setChanged();
        if (this.level != null && !this.level.isClientSide() && !ItemStack.matches(this.syncedDisplay, this.displayTemplate())) {
            this.syncedDisplay = this.displayTemplate().copy();
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
        }
    }

    private static void load(SimpleContainer container, List<ItemStack> values) {
        container.clearContent();
        for (int index = 0; index < Math.min(container.getContainerSize(), values.size()); index++)
            container.setItem(index, values.get(index));
    }
}
