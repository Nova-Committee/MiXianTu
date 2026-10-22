package com.iafenvoy.mxt.screen.menu;

import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.network.payload.SpiritCraftingStateS2CPayload;
import com.iafenvoy.mxt.network.payload.SpiritCraftingStateS2CPayload.Row;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.ArrayList;
import com.iafenvoy.mxt.data.cost.Cost;
import com.iafenvoy.mxt.data.cost.Costs;
import com.iafenvoy.mxt.data.cost.context.CostContext;
import com.iafenvoy.mxt.data.cost.context.CostOrigins;
import com.iafenvoy.mxt.item.block.entity.SpiritCraftingTableBlockEntity;
import com.iafenvoy.mxt.recipe.SpiritCraftingInput;
import com.iafenvoy.mxt.recipe.SpiritRecipe;
import com.iafenvoy.mxt.registry.MxtBlocks;
import com.iafenvoy.mxt.registry.MxtMenus;
import com.iafenvoy.mxt.registry.MxtRecipeTypes;
import com.iafenvoy.mxt.screen.aui.AuiPages;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.profiling.ProfilerScope;
import com.iafenvoy.mxt.util.profiling.ServerProfiling;
import com.sighs.apricityui.screen.ApricityContainerMenu;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import org.jspecify.annotations.NonNull;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.IntStream;

/**
 * The crafting menu restricted to the two spirit recipe types. The recipe is matched here while the aura buffer
 * lives on the block, so the server half alone publishes the progress rows.
 * <p>
 * The slot order is the page's container order - result, the nine grid cells, then the player inventory - because
 * ApricityUI maps a page slot to a menu slot by container, and each container is one contiguous range.
 */
public final class SpiritCraftingMenu extends ApricityContainerMenu {
    private static final int MAX_PROGRESS_ENTRIES = SpiritCraftingStateS2CPayload.MAX_ROWS;
    private static final int RESULT_SLOT = 0;
    private static final int GRID_START = 1;
    private static final int PLAYER_START = 10;
    private final Setup setup;
    private final Player player;
    private final ContainerLevelAccess access;
    private List<Row> progress = List.of();
    private List<Row> sentProgress;
    private RecipeMatch current;

    public SpiritCraftingMenu(int id, Inventory inventory) {
        this(id, inventory, ContainerLevelAccess.NULL);
    }

    public SpiritCraftingMenu(int id, Inventory inventory, ContainerLevelAccess access) {
        this(id, inventory, access, new Setup(access));
    }

    private SpiritCraftingMenu(int id, Inventory inventory, ContainerLevelAccess access, Setup setup) {
        super(id, inventory, setup.page().layout(), setup.page().sources(), Map.of(), null);
        this.setup = setup;
        this.player = inventory.player;
        this.access = access;
        this.updateResult();
    }

    /**
     * ApricityUI's base menu reports its own type to the open packet, and the client picks its screen factory
     * from that.
     */
    @Override
    public @NonNull MenuType<?> getType() {
        return MxtMenus.SPIRIT_CRAFTING_TABLE.get();
    }

    // Runs the action against the table, or does nothing when there is none: broken block, unloaded chunk, or
    // the client half.
    private void withTable(Consumer<SpiritCraftingTableBlockEntity> action) {
        this.access.execute((level, pos) -> {
            if (level.getBlockEntity(pos) instanceof SpiritCraftingTableBlockEntity table) action.accept(table);
        });
    }

    public Holder<Aura> progressAura(int index) {
        return index < 0 || index >= this.progress.size() ? null : this.progress.get(index).aura();
    }

    public double progressAmount(int index) {
        return index < 0 || index >= this.progress.size() ? 0.0D : this.progress.get(index).stored();
    }

    public double progressRequirement(int index) {
        return index < 0 || index >= this.progress.size() ? 0.0D : this.progress.get(index).required();
    }

    public void acceptProgress(List<Row> rows) {
        this.progress = List.copyOf(rows);
    }

    @Override
    public void slotsChanged(@NonNull Container changed) {
        this.updateResult();
    }

    @Override
    public void broadcastChanges() {
        this.updateResult();
        super.broadcastChanges();
    }

    private void updateResult() {
        try (ProfilerScope _ = ServerProfiling.scope(ServerProfiling.Source.CRAFTING)) {
            this.current = this.findRecipe();
            Map<Holder<Aura>, Double> costs = this.current == null ? Map.of() : this.current.costs();
            this.withTable(table -> table.configureAuraCosts(costs));
            this.syncProgress();
        }
    }

    private void syncProgress() {
        if (!(this.player instanceof ServerPlayer serverPlayer)) return;
        List<Row> rows = new ArrayList<>();
        this.withTable(table -> {
            if (this.current == null) return;
            for (Entry<Holder<Aura>, Double> entry : this.current.costs().entrySet()) {
                if (rows.size() >= MAX_PROGRESS_ENTRIES) break;
                rows.add(new Row(entry.getKey(), table.aura(entry.getKey()), entry.getValue()));
            }
        });
        this.progress = List.copyOf(rows);
        // Wait until the open-screen packet has installed this menu before sending its first double snapshot.
        if (!this.progress.equals(this.sentProgress) && serverPlayer.containerMenu == this) {
            PacketDistributor.sendToPlayer(serverPlayer, new SpiritCraftingStateS2CPayload(this.containerId, this.progress));
            this.sentProgress = this.progress;
        }
    }

    private RecipeMatch findRecipe() {
        if (!(this.player.level() instanceof ServerLevel level)) return null;
        SpiritCraftingInput input = this.input();
        RecipeManager manager = level.getServer().getRecipeManager();
        RecipeHolder<? extends SpiritRecipe> shaped = manager.getRecipeFor(MxtRecipeTypes.SPIRIT_SHAPED.get(), input, level).orElse(null);
        if (shaped != null) return this.match(shaped.value());
        RecipeHolder<? extends SpiritRecipe> shapeless = manager.getRecipeFor(MxtRecipeTypes.SPIRIT_SHAPELESS.get(), input, level).orElse(null);
        return shapeless == null ? null : this.match(shapeless.value());
    }

    private RecipeMatch match(SpiritRecipe recipe) {
        return new RecipeMatch(recipe, this.costs(recipe.aura()));
    }

    private Map<Holder<Aura>, Double> costs(List<Cost> aura) {
        Map<Holder<Aura>, Double> amounts = Costs.auras(aura,
                CostContext.of(this.player, FormulaContext.of(this.player), CostOrigins.RECIPE));
        if (amounts == null) return Map.of();
        return Collections.unmodifiableMap(new LinkedHashMap<>(amounts));
    }

    private SpiritCraftingInput input() {
        return new SpiritCraftingInput(IntStream.range(0, 9).mapToObj(this.setup.gridContainer()::getItem).toList());
    }

    /**
     * The same movement the vanilla crafting table performs, over the reordered ranges: a cell on the grid dumps
     * into the player inventory, anything in it merges into the grid, and the result is taken into the inventory.
     * The grid range stops at {@code PLAYER_START}, so the result cell is never a merge target.
     */
    @Override
    public @NonNull ItemStack quickMoveStack(@NonNull Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack original = slot.getItem().copy();
        if (index == RESULT_SLOT) {
            if (!this.moveItemStackTo(slot.getItem(), PLAYER_START, this.slots.size(), true)) return ItemStack.EMPTY;
            slot.onQuickCraft(slot.getItem(), original);
            slot.onTake(player, slot.getItem());
        } else if (index >= GRID_START && index < PLAYER_START) {
            if (!this.moveItemStackTo(slot.getItem(), PLAYER_START, this.slots.size(), false)) return ItemStack.EMPTY;
        } else if (!this.moveItemStackTo(slot.getItem(), GRID_START, PLAYER_START, false)) {
            return ItemStack.EMPTY;
        }
        if (slot.getItem().isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return original;
    }

    @Override
    public boolean stillValid(@NonNull Player player) {
        return stillValid(this.access, player, MxtBlocks.SPIRIT_CRAFTING_TABLE.get());
    }

    // The block entity's own containers, read at construction so that the layout's capacity is the size the
    // client half also reports from the layout alone; without a table it is the page's own size.
    private static Container gridContainer(ContainerLevelAccess access) {
        return resolve(access, SpiritCraftingTableBlockEntity::grid, 9);
    }

    private static Container resultContainer(ContainerLevelAccess access) {
        return resolve(access, SpiritCraftingTableBlockEntity::result, 1);
    }

    private static Container resolve(ContainerLevelAccess access, Function<SpiritCraftingTableBlockEntity, Container> reader, int fallbackSize) {
        return access
                .evaluate((level, pos) -> level.getBlockEntity(pos) instanceof SpiritCraftingTableBlockEntity table
                        ? Optional.ofNullable(reader.apply(table))
                        : Optional.<Container>empty())
                .flatMap(Function.identity())
                .orElseGet(() -> new SimpleContainer(fallbackSize));
    }

    /**
     * The page's containers and the layout they are opened with; the containers have to exist before the menu,
     * so the public constructors build this and hand it to the private one.
     */
    private static final class Setup {
        private final Container grid;
        private final PageSlots.Layout page;

        private Setup(ContainerLevelAccess access) {
            this.grid = SpiritCraftingMenu.gridContainer(access);
            // The page draws the nine grid cells and the result cell itself and groups them under these two ids.
            this.page = PageSlots.of(AuiPages.page(AuiPages.SPIRIT_CRAFTING, "spirit_crafting"))
                    .container("result", resultContainer(access), (container, index, x, y) -> new Slot(container, index, x, y) {
                        @Override
                        public boolean mayPlace(@NonNull ItemStack stack) {
                            return false;
                        }
                    })
                    .container("crafting", this.grid, Slot::new)
                    .player("inventory")
                    .build();
        }

        private PageSlots.Layout page() {
            return this.page;
        }

        private Container gridContainer() {
            return this.grid;
        }
    }

    private record RecipeMatch(SpiritRecipe recipe, Map<Holder<Aura>, Double> costs) {
    }
}
