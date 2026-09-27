package com.iafenvoy.mxt.screen.menu;

import com.iafenvoy.mxt.api.AlchemyWorkstation;
import com.iafenvoy.mxt.data.alchemy.AlchemyFurnaceDefinition;
import com.iafenvoy.mxt.item.block.entity.AlchemyFurnaceBlockEntity;
import com.iafenvoy.mxt.item.block.entity.AlchemyFurnaceInventoryBlockEntity;
import com.iafenvoy.mxt.network.payload.AlchemyActionC2SPayload;
import com.iafenvoy.mxt.network.payload.AlchemyStateS2CPayload;
import com.iafenvoy.mxt.registry.MxtBlocks;
import com.iafenvoy.mxt.registry.MxtMenus;
import com.iafenvoy.mxt.runtime.alchemy.AlchemyFailure;
import com.iafenvoy.mxt.runtime.alchemy.AlchemyFurnaceStructure;
import com.iafenvoy.mxt.runtime.alchemy.AlchemyInventoryKind;
import com.iafenvoy.mxt.runtime.alchemy.AlchemySession;
import com.iafenvoy.mxt.runtime.alchemy.AlchemyWorkstationService;
import com.iafenvoy.mxt.runtime.alchemy.AlchemyWorkstationService.AlchemyPreview;
import com.iafenvoy.mxt.runtime.alchemy.AlchemyWorkstationService.Parameters;
import com.iafenvoy.mxt.runtime.item.ItemQualityService;
import com.iafenvoy.mxt.screen.menu.AlchemyFurnaceView.Numbers;
import com.iafenvoy.mxt.screen.menu.AlchemyFurnaceView.Status;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import java.util.Locale;

/**
 * One menu type, four views. Slot indices are fixed here: machine slots, then player 9-35, then hotbar 0-8.
 * Template order never decides that.
 */
public final class AlchemyFurnaceMenu extends AbstractContainerMenu {
    public enum View {
        MONITOR(1), MAIN(2), AUXILIARY(3), OUTPUT(4);

        private final int machineSlots;

        View(int machineSlots) {
            this.machineSlots = machineSlots;
        }

        public int machineSlots() {
            return this.machineSlots;
        }

        public static View read(RegistryFriendlyByteBuf buffer) {
            return buffer.readEnum(View.class);
        }
    }

    public static final String FIRE = "fire";
    public static final String[] MAIN_SLOTS = {"main_0", "main_1"};
    public static final String[] AUXILIARY_SLOTS = {"aux_0", "aux_1", "catalyst"};
    public static final String[] OUTPUT_SLOTS = {"output_0", "output_1", "output_2", "output_3"};

    private final Level level;
    private final BlockPos accessPos;
    private final View view;
    private final Player player;
    @Nullable
    private final BlockEntity owner;
    private final DataSlot temperatureEpoch = DataSlot.standalone();
    private final DataSlot temperatureAccepted = DataSlot.standalone();
    private AlchemyFurnaceView snapshot = AlchemyFurnaceView.EMPTY;
    private TemperatureAck temperatureAck = new TemperatureAck(0, false, 0.0D);
    private long nextRefresh;
    private boolean forceRefresh = true;
    private boolean forceTemperatureAck;

    public AlchemyFurnaceMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(containerId, inventory, buffer.readBlockPos(), View.read(buffer));
    }

    public AlchemyFurnaceMenu(int containerId, Inventory inventory, BlockPos accessPos, View view) {
        super(MxtMenus.ALCHEMY_FURNACE.get(), containerId);
        this.level = inventory.player.level();
        this.accessPos = accessPos.immutable();
        this.view = view;
        this.player = inventory.player;
        BlockEntity found = physicalOwner(this.level, accessPos, view);
        this.owner = found;
        this.addDataSlot(this.temperatureAccepted);
        // Epoch commits the acknowledgement, after the view and acceptance value have arrived.
        this.addDataSlot(this.temperatureEpoch);
        // Client slots are a vanilla sync mirror. The server binds only the captured owner's container.
        Container machine;
        if (this.level.isClientSide()) machine = new SimpleContainer(view.machineSlots());
        else if (found == null) throw new IllegalStateException("Alchemy furnace menu has no physical owner at " + accessPos + " for " + view);
        else machine = storage(found);
        for (int index = 0; index < view.machineSlots(); index++) this.addSlot(new PartSlot(machine, index));
        for (int index = 9; index < 36; index++) this.addSlot(new Slot(inventory, index, 0, 0));
        for (int index = 0; index < 9; index++) this.addSlot(new Slot(inventory, index, 0, 0));
    }

    /**
     * Authoritative temperature result. Initial epoch is 0; later values are the signed short counter, including wrap.
     * Target is the stored value, including a rejected or unchanged write.
     */
    public record TemperatureAck(int epoch, boolean accepted, double target) {}

    /** Null when the owner is missing, removed, or its kind/slot count does not match the view. */
    public static @Nullable BlockEntity physicalOwner(Level level, BlockPos pos, View view) {
        if (!level.isLoaded(pos) || !roleMatches(level.getBlockState(pos), view)) return null;
        BlockEntity entity = level.getBlockEntity(pos);
        if (entity == null || entity.isRemoved() || !owns(entity, view)) return null;
        return entity;
    }

    public @Nullable BlockEntity owner() {
        return this.owner;
    }

    public Level openingLevel() {
        return this.level;
    }

    public TemperatureAck temperatureAck() {
        int epoch = this.temperatureEpoch.get();
        boolean accepted = this.temperatureAccepted.get() != 0;
        double target = this.snapshot.numbers().target();
        if (this.temperatureAck.epoch() != epoch || this.temperatureAck.accepted() != accepted
                || Double.compare(this.temperatureAck.target(), target) != 0)
            this.temperatureAck = new TemperatureAck(epoch, accepted, target);
        return this.temperatureAck;
    }

    public View view() {
        return this.view;
    }

    public BlockPos accessPos() {
        return this.accessPos;
    }

    public AlchemyFurnaceView viewSnapshot() {
        return this.snapshot;
    }

    public void acceptView(AlchemyFurnaceView view) {
        if (this.player.level().isClientSide()) this.snapshot = view;
    }

    public int menuIndex(String id) {
        if (id.startsWith("inventory_")) {
            int vanilla = Integer.parseInt(id.substring("inventory_".length()));
            if (vanilla < 0 || vanilla > 35) return -1;
            return this.view.machineSlots() + (vanilla >= 9 ? vanilla - 9 : 27 + vanilla);
        }
        String[] ids = switch (this.view) {
            case MONITOR -> new String[]{FIRE};
            case MAIN -> MAIN_SLOTS;
            case AUXILIARY -> AUXILIARY_SLOTS;
            case OUTPUT -> OUTPUT_SLOTS;
        };
        for (int index = 0; index < ids.length; index++) if (ids[index].equals(id)) return index;
        return -1;
    }

    public void handleAction(ServerPlayer player, AlchemyActionC2SPayload payload) {
        if (player.containerMenu != this || payload.containerId() != this.containerId || !this.stillValid(player)) return;
        if (this.view != View.MONITOR || !(this.owner instanceof AlchemyFurnaceBlockEntity furnace)) return;
        switch (payload.action()) {
            case TEMPERATURE -> this.acknowledgeTemperature(furnace, payload.temperature());
            case START -> {
                AlchemyWorkstationService.StartResult result = AlchemyWorkstationService.start(player, furnace);
                if (!result.started() && result.failure() != null) player.sendSystemMessage(failure(result.failure()), true);
            }
            case ABORT -> AlchemyWorkstationService.abort(player.level(), furnace.getBlockPos(), furnace);
        }
        this.forceRefresh = true;
        this.broadcastChanges();
    }

    @Override
    public void broadcastChanges() {
        if (this.player instanceof ServerPlayer player && this.view == View.MONITOR) this.publish(player);
        super.broadcastChanges();
    }

    @Override
    public @NonNull ItemStack quickMoveStack(@NonNull Player player, int index) {
        if (!this.stillValid(player)) return ItemStack.EMPTY;
        if (index < 0 || index >= this.slots.size()) return ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (!slot.hasItem() || !slot.mayPickup(player)) return ItemStack.EMPTY;
        ItemStack original = slot.getItem().copy();
        int machine = this.view.machineSlots();
        int inventoryStart = machine + 27;
        boolean moved = index < machine
                ? this.moveItemStackTo(slot.getItem(), machine, this.slots.size(), true)
                : this.moveIntoMachine(slot.getItem());
        if (!moved) {
            moved = index < machine || index >= inventoryStart
                    ? this.moveItemStackTo(slot.getItem(), machine, inventoryStart, false)
                    : this.moveItemStackTo(slot.getItem(), inventoryStart, this.slots.size(), false);
        }
        if (!moved) return ItemStack.EMPTY;
        if (slot.getItem().isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return original;
    }

    @Override
    public boolean stillValid(@NonNull Player player) {
        if (player.level() != this.level || player.distanceToSqr(this.accessPos.getCenter()) > 64.0D) return false;
        if (!this.level.isLoaded(this.accessPos)) return false;
        if (!roleMatches(this.level.getBlockState(this.accessPos), this.view)) return false;
        if (this.owner == null) return this.level.isClientSide();
        return !this.owner.isRemoved() && this.level.getBlockEntity(this.accessPos) == this.owner && owns(this.owner, this.view);
    }

    private void publish(ServerPlayer player) {
        long now = player.level().getGameTime();
        if (!this.forceRefresh && !this.forceTemperatureAck && now < this.nextRefresh) return;
        this.forceRefresh = false;
        this.nextRefresh = now + 5L;
        boolean acknowledge = this.forceTemperatureAck;
        this.forceTemperatureAck = false;
        if (!(this.owner instanceof AlchemyFurnaceBlockEntity furnace) || !this.stillValid(player)) return;
        AlchemyFurnaceView next = this.snapshot(player, furnace);
        if (!acknowledge && next.equals(this.snapshot)) return;
        this.snapshot = next;
        PacketDistributor.sendToPlayer(player, new AlchemyStateS2CPayload(this.containerId, next));
    }

    private void acknowledgeTemperature(AlchemyFurnaceBlockEntity furnace, double submitted) {
        boolean accepted = Double.isFinite(submitted) && furnace.setTargetTemperature(submitted);
        this.temperatureEpoch.set((short) (this.temperatureEpoch.get() + 1));
        this.temperatureAccepted.set(accepted ? 1 : 0);
        this.forceTemperatureAck = true;
    }

    /** Machine destinations must not use the vanilla merge loop: it writes occupied stacks before mayPlace. */
    private boolean moveIntoMachine(ItemStack stack) {
        int end = this.view.machineSlots();
        boolean moved = false;
        if (stack.isStackable()) {
            for (int index = 0; index < end && !stack.isEmpty(); index++) {
                Slot slot = this.slots.get(index);
                ItemStack existing = slot.getItem();
                if (existing.isEmpty() || !ItemStack.isSameItemSameComponents(stack, existing) || !slot.mayPlace(stack)) continue;
                int limit = slot.getMaxStackSize(existing);
                int combined = existing.getCount() + stack.getCount();
                if (combined <= limit) {
                    stack.setCount(0);
                    existing.setCount(combined);
                } else if (existing.getCount() < limit) {
                    stack.shrink(limit - existing.getCount());
                    existing.setCount(limit);
                } else continue;
                slot.setChanged();
                moved = true;
            }
        }
        for (int index = 0; index < end && !stack.isEmpty(); index++) {
            Slot slot = this.slots.get(index);
            if (!slot.getItem().isEmpty() || !slot.mayPlace(stack)) continue;
            int limit = slot.getMaxStackSize(stack);
            slot.setByPlayer(stack.split(Math.min(stack.getCount(), limit)));
            slot.setChanged();
            return true;
        }
        return moved;
    }

    private static Container storage(BlockEntity owner) {
        if (owner instanceof AlchemyFurnaceBlockEntity furnace) return furnace.fireContainer();
        if (owner instanceof AlchemyFurnaceInventoryBlockEntity part) return part.inventory();
        throw new IllegalStateException("Alchemy furnace menu owner is not a furnace part");
    }

    private static boolean owns(BlockEntity entity, View view) {
        if (view == View.MONITOR) {
            return entity instanceof AlchemyFurnaceBlockEntity furnace && furnace.fireContainer().getContainerSize() == view.machineSlots();
        }
        if (!(entity instanceof AlchemyFurnaceInventoryBlockEntity part)) return false;
        AlchemyInventoryKind expected = switch (view) {
            case MAIN -> AlchemyInventoryKind.MAIN;
            case AUXILIARY -> AlchemyInventoryKind.AUXILIARY;
            case OUTPUT -> AlchemyInventoryKind.OUTPUT;
            case MONITOR -> null;
        };
        return part.kind() == expected && part.getContainerSize() == view.machineSlots()
                && part.inventory().getContainerSize() == view.machineSlots();
    }

    private static boolean roleMatches(BlockState state, View view) {
        return switch (view) {
            case MONITOR -> state.is(MxtBlocks.ALCHEMY_FURNACE.get());
            case MAIN -> state.is(MxtBlocks.ALCHEMY_MAIN_INPUT.get());
            case AUXILIARY -> state.is(MxtBlocks.ALCHEMY_AUXILIARY_INPUT.get());
            case OUTPUT -> state.is(MxtBlocks.ALCHEMY_OUTPUT.get());
        };
    }

    private AlchemyFurnaceView snapshot(ServerPlayer player, AlchemyWorkstation furnace) {
        AlchemyFurnaceDefinition spec = furnace.furnaceDefinition().map(holder -> holder.value()).orElse(null);
        AlchemySession session = furnace.state().session().orElse(null);
        AlchemyPreview preview = session == null ? AlchemyWorkstationService.preview(player, furnace) : null;
        Parameters parameters = session != null ? session.parameters() : preview.parameters().orElse(null);
        AlchemyFurnaceStructure.Status structure = furnace.structureStatus();
        Component name = furnace.furnaceDefinition().map(holder -> holder.value().name()).orElse(text("no_furnace"));
        Component quality = ItemQualityService.find(player.registryAccess(), furnace.furnaceItem())
                .map(holder -> ItemQualityService.coloredName(holder, holder.value().name())).orElse(text("no_quality"));
        boolean canStart = session == null && spec != null && structure.complete() && preview.blocker().isEmpty() && parameters != null;
        Component message = session != null
                ? session.failed() ? text("failed_batch")
                : !structure.complete() ? text("structure", structure.missing().size(), structure.unloaded().size(), structure.conflicts().size())
                : text("phase." + furnace.phase().name().toLowerCase(Locale.ROOT))
                : spec == null ? text("no_furnace")
                : !structure.complete() ? text("structure", structure.missing().size(), structure.unloaded().size(), structure.conflicts().size())
                : preview.qualityFailure().map(reason -> text("failure." + reason.name().toLowerCase(Locale.ROOT)))
                .or(() -> preview.blocker().map(AlchemyFurnaceMenu::failure))
                .orElse(text("ready"));
        Status status = new Status(name, quality, furnace.phase().name(), message,
                canStart, furnace.state().busy(), structure.formed(),
                spec == null ? 0 : spec.mainSlots(), spec == null ? 0 : spec.auxiliarySlots());
        Numbers numbers = new Numbers(furnace.temperature(), furnace.targetTemperature(), furnace.maximumTemperature(),
                session != null ? session.frozenTarget() : parameters == null ? 0 : parameters.targetTemperature(),
                session != null ? session.frozenTolerance() : parameters == null ? 0 : parameters.temperatureTolerance(),
                session == null ? 0 : session.remainingTicks(),
                session == null ? 0 : session.totalTicks(),
                session == null ? 0 : session.badTicks());
        return new AlchemyFurnaceView(status, numbers);
    }

    private static Component text(String suffix, Object... arguments) {
        return Component.translatable("screen.mxt.alchemy." + suffix, arguments);
    }

    private static Component failure(AlchemyFailure failure) {
        return text("failure." + failure.name().toLowerCase(Locale.ROOT));
    }

    private final class PartSlot extends Slot {
        private PartSlot(Container container, int index) {
            super(container, index, 0, 0);
        }

        @Override
        public boolean mayPlace(@NonNull ItemStack stack) {
            BlockEntity owner = AlchemyFurnaceMenu.this.owner;
            if (owner instanceof AlchemyFurnaceBlockEntity furnace) return furnace.canPlaceFire(stack);
            if (owner instanceof AlchemyFurnaceInventoryBlockEntity part) return part.canPlaceItem(this.getContainerSlot(), stack);
            return false;
        }

        @Override
        public boolean mayPickup(@NonNull Player player) {
            BlockEntity owner = AlchemyFurnaceMenu.this.owner;
            if (owner instanceof AlchemyFurnaceBlockEntity furnace) return furnace.canTakeFire();
            if (owner instanceof AlchemyFurnaceInventoryBlockEntity part) return part.canTakeItem(this.getContainerSlot(), this.getItem());
            return false;
        }

        @Override
        public void setChanged() {
            super.setChanged();
            AlchemyFurnaceMenu.this.forceRefresh = true;
        }
    }
}
