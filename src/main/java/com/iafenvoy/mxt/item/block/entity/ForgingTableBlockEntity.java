package com.iafenvoy.mxt.item.block.entity;

import com.iafenvoy.mxt.data.forging.ForgingBlueprint;
import com.iafenvoy.mxt.registry.MxtBlockEntities;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.forging.ForgingSurface;
import com.iafenvoy.mxt.runtime.forging.ForgingTableState;
import com.iafenvoy.mxt.runtime.forging.ForgingWorkstationService;
import com.iafenvoy.mxt.screen.menu.ForgingMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import java.util.List;

/**
 * Placed forge table: the slot surface plus the shared forging session.
 *
 * <p>The block entity owns both the container and the session, and it is the authority for both. It is
 * not, however, what the open menu reads: the menu half that runs on a client cannot reach a block
 * entity at all, so everything the screen shows is published by the server into the menu's data slots
 * and the two selector lists are derived from the client's own copy of the slot contents. See
 * {@code ForgingMenu}.</p>
 *
 * <p>{@link #forgingChanged()} still sends the block entity update packet, which keeps the client's
 * copy of the session current for anything that inspects the block rather than the menu. Nothing on
 * the forge screen depends on it any more.</p>
 *
 * <p>The menu and the screen are vanilla: see {@code ForgingMenu} and {@code ForgingScreen}, wired
 * up by {@link #createMenu} and {@code MxtRenderers}.</p>
 */
public final class ForgingTableBlockEntity extends BlockEntity implements ForgingSurface, WorldlyContainer, MenuProvider {
    private final SimpleContainer inventory = new SimpleContainer(TOTAL_SLOTS) {
        @Override
        public void setChanged() {
            super.setChanged();
            ForgingTableBlockEntity.this.forgingChanged();
        }
    };
    private final ForgingTableState forging = new ForgingTableState();

    public ForgingTableBlockEntity(BlockPos pos, BlockState state) {
        super(MxtBlockEntities.FORGING_TABLE.get(), pos, state);
    }

    // ------------------------------------------------------------------ ForgingSurface

    @Override
    public Container forgingContainer() {
        return this.inventory;
    }

    @Override
    public ForgingTableState forgingState() {
        return this.forging;
    }

    @Override
    public void forgingChanged() {
        this.setChanged();
        if (this.level != null && !this.level.isClientSide())
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
    }
    /**
     * The blueprint this table's session locked in, resolved live so a datapack reload is picked up
     * outside a session. Null when no session is running.
     */
    public ForgingBlueprint selectedBlueprint() {
        return this.forging.blueprint().flatMap(id -> MxtDatapackRegistries.get(MxtResourceKeys.FORGING_BLUEPRINT, id)).orElse(null);
    }

    /**
     * Blueprint ids offered by the surface, for the selector list.
     */
    public List<Identifier> selectableBlueprintIds() {
        return ForgingWorkstationService.selectableBlueprintIds(this.inventory);
    }

    /**
     * Method ids offered by the surface, for the selector list: the session's blueprint narrowed by the
     * tools, or every unlocked method when no session is running. See
     * {@link ForgingWorkstationService#availableMethodIds}.
     */
    public List<Identifier> availableMethodIds() {
        return this.level == null ? List.of()
                : ForgingWorkstationService.availableMethodIds(this.inventory, this.level.registryAccess(), this.forging.blueprint().orElse(null));
    }

    // ------------------------------------------------------------------ MenuProvider

    @Override
    public @NonNull Component getDisplayName() {
        return Component.translatable("screen.mxt.forging_table");
    }

    /**
     * Hands out an access, not the entity.
     *
     * <p>{@code ContainerLevelAccess} is how a menu reaches the block it belongs to: the server half
     * resolves the entity on demand, and the client half gets {@code ContainerLevelAccess.NULL}, whose
     * every lookup is empty. Nothing about the table can therefore leak into the menu's client half,
     * and nothing has to be null-checked there either - the client reads the data slots the server
     * publishes instead. Vanilla does the same thing for every workstation menu.
     *
     * <p>This is also where a session that is already finished gets settled. A session can be complete
     * without having been settled - a listener cancelled the settlement, or the world was written before
     * settlement was automatic - and opening the menu is the one moment the server holds the table and
     * its player together outside a strike. Without it, such a table would sit locked forever with a
     * finished piece that nothing ever produces.</p>
     */
    @Override
    public AbstractContainerMenu createMenu(int containerId, @NonNull Inventory inventory, @NonNull Player player) {
        ContainerLevelAccess access = ContainerLevelAccess.create(player.level(), this.getBlockPos());
        if (player instanceof ServerPlayer server) ForgingWorkstationService.settleIfComplete(server, this);
        return new ForgingMenu(containerId, inventory, access);
    }

    // ------------------------------------------------------------------ Container

    @Override
    public int getContainerSize() {
        return TOTAL_SLOTS;
    }

    @Override
    public boolean isEmpty() {
        return this.inventory.isEmpty();
    }

    @Override
    public @NonNull ItemStack getItem(int index) {
        return this.inventory.getItem(index);
    }

    @Override
    public @NonNull ItemStack removeItem(int index, int count) {
        return this.inventory.removeItem(index, count);
    }

    @Override
    public @NonNull ItemStack removeItemNoUpdate(int index) {
        return this.inventory.removeItemNoUpdate(index);
    }

    @Override
    public void setItem(int index, @NonNull ItemStack stack) {
        this.inventory.setItem(index, stack);
    }

    @Override
    public boolean stillValid(@NonNull Player player) {
        return this.level != null && !this.isRemoved() && player.distanceToSqr(this.worldPosition.getCenter()) <= 64.0D;
    }

    @Override
    public void clearContent() {
        this.inventory.clearContent();
    }

    /**
     * Slot filter: the surface is not a generic chest.
     *
     * <p>Blueprint slots take blueprint-bound items, tool slots take tool-bound items, input slots
     * take only materials the selected blueprint declares, and the output slot never accepts. The rule
     * itself lives on {@link ForgingSurface} so the menu cannot state a different one.</p>
     */
    @Override
    public boolean canPlaceItem(int index, @NonNull ItemStack stack) {
        return ForgingSurface.canPlace(index, stack, this.forging.active(), this.selectedBlueprint());
    }

    @Override
    public int @NonNull [] getSlotsForFace(@NonNull Direction side) {
        int[] slots = new int[INPUT_SLOTS + 1];
        for (int index = 0; index < INPUT_SLOTS; index++) slots[index] = INPUT_START + index;
        slots[INPUT_SLOTS] = OUTPUT_SLOT;
        return slots;
    }

    @Override
    public boolean canPlaceItemThroughFace(int index, @NonNull ItemStack stack, @Nullable Direction side) {
        return this.canPlaceItem(index, stack);
    }

    @Override
    public boolean canTakeItemThroughFace(int index, @NonNull ItemStack stack, @NonNull Direction side) {
        return index == OUTPUT_SLOT && side == Direction.DOWN;
    }

    // ------------------------------------------------------------------ persistence

    @Override
    protected void loadAdditional(@NonNull ValueInput input) {
        super.loadAdditional(input);
        List<ItemStack> values = input.read("items", ItemStack.OPTIONAL_CODEC.listOf()).orElse(List.of());
        this.inventory.clearContent();
        for (int index = 0; index < Math.min(TOTAL_SLOTS, values.size()); index++)
            this.inventory.setItem(index, values.get(index));
        input.read("forging", ForgingTableState.CODEC).ifPresent(this.forging::copyFrom);
    }

    @Override
    protected void saveAdditional(@NonNull ValueOutput output) {
        super.saveAdditional(output);
        output.store("items", ItemStack.OPTIONAL_CODEC.listOf(), this.inventory.getItems());
        output.store("forging", ForgingTableState.CODEC, this.forging);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public @NonNull CompoundTag getUpdateTag(@NonNull Provider registries) {
        return this.saveWithoutMetadata(registries);
    }
}
