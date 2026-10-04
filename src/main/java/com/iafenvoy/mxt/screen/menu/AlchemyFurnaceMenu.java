package com.iafenvoy.mxt.screen.menu;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.api.AlchemyWorkstation;
import com.iafenvoy.mxt.data.alchemy.AlchemyFurnaceDefinition;
import com.iafenvoy.mxt.item.block.entity.AlchemyFurnaceBlockEntity;
import com.iafenvoy.mxt.item.block.entity.AlchemyFurnaceInventoryBlockEntity;
import com.iafenvoy.mxt.network.payload.AlchemyActionC2SPayload;
import com.iafenvoy.mxt.network.payload.AlchemyStateS2CPayload;
import com.iafenvoy.mxt.registry.MxtBlocks;
import com.iafenvoy.mxt.registry.MxtMenus;
import com.iafenvoy.mxt.runtime.alchemy.*;
import com.iafenvoy.mxt.runtime.alchemy.AlchemyWorkstationService.AlchemyPreview;
import com.iafenvoy.mxt.runtime.alchemy.AlchemyWorkstationService.Parameters;
import com.iafenvoy.mxt.runtime.item.QualityService;
import com.iafenvoy.mxt.screen.aui.AuiPages;
import com.iafenvoy.mxt.screen.menu.AlchemyFurnaceView.Numbers;
import com.iafenvoy.mxt.screen.menu.AlchemyFurnaceView.Status;
import com.sighs.apricityui.screen.ApricityContainerMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import java.util.Locale;
import java.util.Map;

/**
 * One menu type, four views. The slot order is the page's container order - the machine container, as wide as the
 * view, then the player inventory - because ApricityUI maps a page cell to a menu slot by container.
 */
public final class AlchemyFurnaceMenu extends ApricityContainerMenu {
    public enum View {
        MONITOR("monitor", 0),
        MAIN("main_input", 2),
        AUXILIARY("auxiliary_input", 3),
        OUTPUT("output", 4);
        private static final Identifier ID = Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "alchemy");
        private final String slug;
        private final int machineSlots;

        View(String slug, int machineSlots) {
            this.slug = slug;
            this.machineSlots = machineSlots;
        }

        public String getSlug() {
            return this.slug;
        }

        public String getTranslation() {
            return ID.toLanguageKey("screen", this.slug);
        }

        public int getMachineSlots() {
            return this.machineSlots;
        }

        public static View read(RegistryFriendlyByteBuf buffer) {
            return buffer.readEnum(View.class);
        }
    }

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
        this(containerId, inventory, accessPos, view, new Setup(inventory, accessPos, view));
    }

    private AlchemyFurnaceMenu(int containerId, Inventory inventory, BlockPos accessPos, View view, Setup setup) {
        super(containerId, inventory, setup.page().layout(), setup.page().sources(), Map.of(), null);
        setup.attach(this);
        this.level = inventory.player.level();
        this.accessPos = accessPos.immutable();
        this.view = view;
        this.player = inventory.player;
        this.owner = setup.owner();
        this.addDataSlot(this.temperatureAccepted);
        // Epoch commits the acknowledgement, after the view and acceptance value have arrived.
        this.addDataSlot(this.temperatureEpoch);
    }

    /**
     * The menu type ApricityUI's base menu would report is its own; the open packet carries whatever this answers,
     * and the client picks its screen factory from that.
     */
    @Override
    public @NonNull MenuType<?> getType() {
        return MxtMenus.ALCHEMY_FURNACE.get();
    }

    /**
     * Authoritative temperature result. Initial epoch is 0; later values are the signed short counter, including wrap.
     * Target is the stored value, including a rejected or unchanged write.
     */
    public record TemperatureAck(int epoch, boolean accepted, double target) {
    }

    /**
     * Null when the owner is missing, removed, or its kind/slot count does not match the view.
     */
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
            return this.view.getMachineSlots() + (vanilla >= 9 ? vanilla - 9 : 27 + vanilla);
        }
        String[] ids = switch (this.view) {
            case MONITOR -> new String[0];
            case MAIN -> MAIN_SLOTS;
            case AUXILIARY -> AUXILIARY_SLOTS;
            case OUTPUT -> OUTPUT_SLOTS;
        };
        for (int index = 0; index < ids.length; index++) if (ids[index].equals(id)) return index;
        return -1;
    }

    public void handleAction(ServerPlayer player, AlchemyActionC2SPayload payload) {
        if (player.containerMenu != this || payload.containerId() != this.containerId || !this.stillValid(player))
            return;
        if (this.view != View.MONITOR || !(this.owner instanceof AlchemyFurnaceBlockEntity furnace)) return;
        switch (payload.action()) {
            case TEMPERATURE -> this.acknowledgeTemperature(furnace, payload.temperature());
            case START -> {
                AlchemyWorkstationService.StartResult result = AlchemyWorkstationService.start(player, furnace);
                if (!result.started() && result.failure() != null)
                    player.sendSystemMessage(failure(result.failure()), true);
            }
            case ABORT -> AlchemyWorkstationService.abort(player.level(), furnace.getBlockPos(), furnace);
        }
        this.forceRefresh = true;
        this.broadcastChanges();
    }

    @Override
    public void broadcastChanges() {
        if (this.player instanceof ServerPlayer serverPlayer && this.view == View.MONITOR) this.publish(serverPlayer);
        super.broadcastChanges();
    }

    @Override
    public @NonNull ItemStack quickMoveStack(@NonNull Player player, int index) {
        if (!this.stillValid(player)) return ItemStack.EMPTY;
        if (index < 0 || index >= this.slots.size()) return ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (!slot.hasItem() || !slot.mayPickup(player)) return ItemStack.EMPTY;
        ItemStack original = slot.getItem().copy();
        int machine = this.view.getMachineSlots();
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

    /**
     * Machine destinations must not use the vanilla merge loop: it writes occupied stacks before mayPlace.
     */
    private boolean moveIntoMachine(ItemStack stack) {
        int end = this.view.getMachineSlots();
        boolean moved = false;
        if (stack.isStackable()) {
            for (int index = 0; index < end && !stack.isEmpty(); index++) {
                Slot slot = this.slots.get(index);
                ItemStack existing = slot.getItem();
                if (existing.isEmpty() || !ItemStack.isSameItemSameComponents(stack, existing) || !slot.mayPlace(stack))
                    continue;
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
        if (owner instanceof AlchemyFurnaceInventoryBlockEntity part) return part.inventory();
        throw new IllegalStateException("Alchemy furnace menu owner is not a furnace part");
    }

    private static boolean owns(BlockEntity entity, View view) {
        if (view == View.MONITOR) return entity instanceof AlchemyFurnaceBlockEntity;
        if (!(entity instanceof AlchemyFurnaceInventoryBlockEntity part)) return false;
        AlchemyInventoryKind expected = switch (view) {
            case MAIN -> AlchemyInventoryKind.MAIN;
            case AUXILIARY -> AlchemyInventoryKind.AUXILIARY;
            case OUTPUT -> AlchemyInventoryKind.OUTPUT;
            default -> throw new IllegalStateException("Unexpected value: " + view);
        };
        return part.kind() == expected && part.getContainerSize() == view.getMachineSlots()
                && part.inventory().getContainerSize() == view.getMachineSlots();
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
        AlchemyFurnaceDefinition spec = furnace.furnaceDefinition().map(Holder::value).orElse(null);
        AlchemySession session = furnace.state().session().orElse(null);
        AlchemyPreview preview = session == null ? AlchemyWorkstationService.preview(player, furnace) : null;
        Parameters parameters = session != null ? session.parameters() : preview.parameters().orElse(null);
        AlchemyFurnaceStructure.Status structure = furnace.structureStatus();
        Component name = furnace.furnaceDefinition().map(holder -> holder.value().name()).orElse(text("no_furnace"));
        Component quality = QualityService.find(player.registryAccess(), furnace.furnaceItem())
                .map(holder -> QualityService.coloredName(holder, holder.value().name())).orElse(text("no_quality"));
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

    /**
     * One machine cell: the physical owner decides what may sit in it and when it may be taken. The layout builds
     * the cells before the menu exists, so they reach the owner and the menu back through the setup that built them.
     */
    private static final class PartSlot extends Slot {
        private final Setup setup;

        private PartSlot(Setup setup, Container container, int index, int x, int y) {
            super(container, index, x, y);
            this.setup = setup;
        }

        @Override
        public boolean mayPlace(@NonNull ItemStack stack) {
            BlockEntity owner = this.setup.owner();
            return owner instanceof AlchemyFurnaceInventoryBlockEntity part
                    && part.canPlaceItem(this.getContainerSlot(), stack);
        }

        @Override
        public boolean mayPickup(@NonNull Player player) {
            BlockEntity owner = this.setup.owner();
            return owner instanceof AlchemyFurnaceInventoryBlockEntity part
                    && part.canTakeItem(this.getContainerSlot(), this.getItem());
        }

        @Override
        public void setChanged() {
            super.setChanged();
            this.setup.menu().forceRefresh = true;
        }
    }

    /**
     * The machine container and the layout the page is opened with: ApricityUI's menu builds the slots inside its own
     * constructor, so both exist before this menu's {@code super}, and the machine width is the view's slot count.
     */
    private static final class Setup {
        @Nullable
        private final BlockEntity owner;
        private final PageSlots.Layout page;
        private AlchemyFurnaceMenu menu;

        private Setup(Inventory inventory, BlockPos accessPos, View view) {
            Level level = inventory.player.level();
            BlockEntity found = physicalOwner(level, accessPos, view);
            // The monitor has no machine cell: the heat source is a block in the world, so its layout carries an
            // empty container and only the player inventory holds slots.
            // Client slots are a vanilla sync mirror. The server binds only the captured owner's container.
            Container machine;
            if (view == View.MONITOR) machine = new SimpleContainer(0);
            else if (level.isClientSide()) machine = new SimpleContainer(view.getMachineSlots());
            else if (found == null)
                throw new IllegalStateException("Alchemy furnace menu has no physical owner at " + accessPos + " for " + view);
            else machine = storage(found);
            this.owner = found;
            this.page = PageSlots.of(AuiPages.alchemyPage(view.getSlug()))
                    .container("machine", machine, (container, index, x, y) -> new PartSlot(this, container, index, x, y))
                    .player("player_inventory")
                    .build();
        }

        private void attach(AlchemyFurnaceMenu menu) {
            this.menu = menu;
        }

        private @Nullable BlockEntity owner() {
            return this.owner;
        }

        private AlchemyFurnaceMenu menu() {
            return this.menu;
        }

        private PageSlots.Layout page() {
            return this.page;
        }
    }
}
