package com.iafenvoy.mxt.screen.menu;

import com.iafenvoy.mxt.config.MxtServerConfig;
import com.iafenvoy.mxt.network.payload.TalismanDrawingListS2CPayload;
import com.iafenvoy.mxt.network.payload.TalismanDrawingStartS2CPayload;
import com.iafenvoy.mxt.network.payload.TalismanResultS2CPayload;
import com.iafenvoy.mxt.network.payload.TalismanStrokeAckS2CPayload;
import com.iafenvoy.mxt.recipe.TalismanDrawingRecipe;
import com.iafenvoy.mxt.registry.MxtBlocks;
import com.iafenvoy.mxt.registry.MxtMenus;
import com.iafenvoy.mxt.runtime.talisman.TalismanDrawingScorer.Stroke;
import com.iafenvoy.mxt.runtime.talisman.TalismanDrawingSession;
import com.iafenvoy.mxt.runtime.talisman.TalismanWorkstationService;
import com.iafenvoy.mxt.screen.aui.AuiPages;
import com.sighs.apricityui.screen.ApricityContainerMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * The workstation's menu, and the only place a drawing session lives: two players at the same station keep two
 * sessions of their own, and {@link #removed(Player)} is where an abandoned one ends. Nothing here is stored in the
 * world - the one slot is this menu's own, the way a crafting table's grid is, so closing the screen hands whatever
 * it still holds back to the player. The pigment has no slot: it is the brush's own store, and dipping works in any
 * container.
 *
 * <p>The client builds this menu with its own placeholder slot, the way the vanilla furnace does: slot contents
 * arrive through the vanilla slot packets.
 */
public final class TalismanWorkstationMenu extends ApricityContainerMenu {
    public static final int PAPER_SLOT = 0;
    private static final int PLAYER_INVENTORY_START = 1;
    private static final int PLAYER_HOTBAR_END = PLAYER_INVENTORY_START + 36;
    /**
     * Ticks between two unconditional list pushes, for what the inputs below cannot see.
     */
    private static final int LIST_REFRESH_TICKS = 20;

    /**
     * The transient paper slot. It exists only for as long as the screen does, which is exactly the crafting table's
     * contract: {@link #removed(Player)} returns it, so nothing a player puts here can be stranded in the world.
     */
    private final Container paper;
    private final ContainerLevelAccess access;
    private final boolean hasStation;
    private @Nullable TalismanDrawingSession session;
    private @Nullable ServerPlayer opener;
    private boolean listSent;
    /**
     * What the list last pushed was computed from; see {@link #listInputsChanged()}.
     */
    private ItemStack listedPaper = ItemStack.EMPTY;
    private int listAge;

    // What the server has told this side, read by the screen every frame it is open.
    private List<TalismanDrawingListS2CPayload.Row> rows = List.of();
    private @Nullable TalismanDrawingStartS2CPayload drawing;
    private @Nullable TalismanStrokeAckS2CPayload lastAck;
    private @Nullable TalismanResultS2CPayload result;

    /**
     * The server half: the block hands its own position in, which is what {@link #stillValid} checks.
     */
    public TalismanWorkstationMenu(int containerId, Inventory inventory, ContainerLevelAccess access) {
        this(containerId, inventory, access, true, new Setup());
        this.opener = inventory.player instanceof ServerPlayer player ? player : null;
    }

    /**
     * The client half: the same shape, with no world to check and a slot the packets fill.
     */
    public TalismanWorkstationMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, ContainerLevelAccess.NULL, false, new Setup());
    }

    private TalismanWorkstationMenu(int containerId, Inventory inventory, ContainerLevelAccess access, boolean hasStation, Setup setup) {
        super(containerId, inventory, setup.page().layout(), setup.page().sources(), Map.of(), null);
        this.paper = setup.paper();
        this.access = access;
        this.hasStation = hasStation;
    }

    /**
     * The menu type ApricityUI's base menu would report is its own; the open packet carries whatever this answers,
     * and the client picks its screen factory from that.
     */
    @Override
    public MenuType<?> getType() {
        return MxtMenus.TALISMAN_WORKSTATION.get();
    }

    public Container paper() {
        return this.paper;
    }

    public @Nullable TalismanDrawingSession session() {
        return this.session;
    }

    public List<TalismanDrawingListS2CPayload.Row> rows() {
        return this.rows;
    }

    public @Nullable TalismanDrawingStartS2CPayload drawing() {
        return this.drawing;
    }

    public @Nullable TalismanStrokeAckS2CPayload lastAck() {
        return this.lastAck;
    }

    public @Nullable TalismanResultS2CPayload result() {
        return this.result;
    }

    // The list is pushed on the first tick the menu is open: a payload sent while the menu is being created would
    // race the client's own menu, which does not exist yet.
    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (this.opener == null) return;
        if (!this.listSent) {
            this.listSent = true;
            this.sendList(this.opener);
            return;
        }
        // Whether a formula is affordable moves with the station slot, so the list is pushed again whenever that
        // slot changed; anything else the rows depend on (the payer's own accounts, unlock conditions, a data pack
        // reload) is covered by the slow tick.
        if (++this.listAge >= LIST_REFRESH_TICKS || this.listInputsChanged()) this.sendList(this.opener);
    }

    private boolean listInputsChanged() {
        return !ItemStack.matches(this.paper.getItem(0), this.listedPaper);
    }

    /**
     * The formulas this player may pick, recomputed on demand rather than cached across a data pack reload.
     */
    public void sendList(ServerPlayer player) {
        this.listAge = 0;
        this.listedPaper = this.paper.getItem(0).copy();
        List<TalismanDrawingListS2CPayload.Row> rows = TalismanWorkstationService
                .entries(player, this.paper, this.heldPaper()).stream()
                .map(entry -> new TalismanDrawingListS2CPayload.Row(entry.id(), entry.name(), entry.affordable()))
                .toList();
        PacketDistributor.sendToPlayer(player, new TalismanDrawingListS2CPayload(this.containerId, rows));
    }

    /**
     * The paper an unpainted session is holding: changing formula hands it back, so it still counts as spendable.
     */
    private ItemStack heldPaper() {
        if (this.session == null || this.session.drewAnything()) return ItemStack.EMPTY;
        List<ItemStack> charged = this.session.chargedItems();
        return charged.isEmpty() ? ItemStack.EMPTY : charged.getFirst();
    }

    public void acceptList(List<TalismanDrawingListS2CPayload.Row> rows) {
        this.rows = List.copyOf(rows);
    }

    public void acceptStart(TalismanDrawingStartS2CPayload payload) {
        this.drawing = payload;
        this.result = null;
        this.lastAck = null;
    }

    public void acceptAck(TalismanStrokeAckS2CPayload payload) {
        this.lastAck = payload;
    }

    public void acceptResult(TalismanResultS2CPayload payload) {
        this.result = payload;
        this.drawing = null;
    }

    /**
     * Opens a session, or changes the formula of one that has drawn nothing yet. Changing is free - the open
     * session hands the paper and everything else back - and after the first stroke it is refused.
     */
    public void select(ServerPlayer player, Identifier recipeId) {
        boolean replacing = this.session != null;
        if (replacing) {
            if (this.session.recipeId().equals(recipeId) || this.session.drewAnything()) return;
            TalismanWorkstationService.abandon(player, this.session);
            this.session = null;
        }
        TalismanWorkstationService.start(player, this.paper, recipeId).ifPresent(started -> {
            this.session = started;
            TalismanDrawingRecipe.Pattern pattern = started.recipe().pattern();
            PacketDistributor.sendToPlayer(player, new TalismanDrawingStartS2CPayload(this.containerId, recipeId,
                    started.recipe().backgroundColor(), started.recipe().foregroundColor(),
                    pattern.strokes(), pattern.guide().getSerializedName(), pattern.showOrder(), pattern.tolerance(),
                    started.recipe().judgement(), MxtServerConfig.INSTANCE.talisman.minStrokeInterval.getValue()));
        });
        // The old formula is already handed back by now, so a replacement that will not start leaves nothing open:
        // the screen has to hear about it, or it would keep drawing against a session the server no longer has.
        if (replacing && this.session == null)
            PacketDistributor.sendToPlayer(player, new TalismanResultS2CPayload(this.containerId,
                    TalismanResultS2CPayload.CANCELLED, 0.0D, false,
                    Component.empty(), ItemStack.EMPTY, 0));
    }

    public void stroke(ServerPlayer player, Identifier recipeId, Stroke stroke) {
        if (this.session == null || !this.session.recipeId().equals(recipeId)) {
            PacketDistributor.sendToPlayer(player, new TalismanStrokeAckS2CPayload(this.containerId,
                    this.session == null ? 0 : this.session.strokeCount(), false,
                    TalismanWorkstationService.StrokeRefusal.SESSION_OVER.ordinal(), 0));
            return;
        }
        int index = this.session.strokeCount();
        TalismanWorkstationService.StrokeResult result = TalismanWorkstationService.stroke(player, this.session, stroke.points());
        PacketDistributor.sendToPlayer(player, new TalismanStrokeAckS2CPayload(this.containerId, index,
                result.accepted(), result.refusal() == null
                ? TalismanStrokeAckS2CPayload.ACCEPTED : result.refusal().ordinal(), result.charged()));
        if (this.session.failed()) this.endFailed(player);
    }

    public void submit(ServerPlayer player, Identifier recipeId, List<Stroke> strokes) {
        if (this.session == null || !this.session.recipeId().equals(recipeId)) return;
        TalismanDrawingSession ended = this.session;
        this.session = null;
        TalismanWorkstationService.Outcome outcome =
                TalismanWorkstationService.submit(player, ended, strokes.stream().map(Stroke::points).toList());
        PacketDistributor.sendToPlayer(player, new TalismanResultS2CPayload(this.containerId,
                TalismanResultS2CPayload.SETTLED, outcome.completion(), outcome.success(), outcome.text(),
                outcome.product(), outcome.pigmentSpent()));
        // What a formula costs changed with the paper the session took, so the list is recomputed for the next pick.
        this.sendList(player);
    }

    /**
     * Ending the drawing by hand. A session that drew nothing is handed everything back and answers {@code
     * CANCELLED}; one that drew is the same failure {@link #removed(Player)} reaches, so the screen is told
     * {@code ABANDONED} rather than pretending the attempt never happened.
     */
    public void cancel(ServerPlayer player) {
        if (this.session == null) return;
        TalismanDrawingSession ended = this.session;
        this.session = null;
        boolean failed = ended.drewAnything();
        TalismanWorkstationService.abandon(player, ended);
        PacketDistributor.sendToPlayer(player, new TalismanResultS2CPayload(this.containerId,
                failed ? TalismanResultS2CPayload.ABANDONED : TalismanResultS2CPayload.CANCELLED, 0.0D, false,
                Component.empty(), ItemStack.EMPTY, ended.pigmentSpent()));
        this.sendList(player);
    }

    private void endFailed(ServerPlayer player) {
        TalismanDrawingSession ended = this.session;
        this.session = null;
        if (ended == null) return;
        TalismanWorkstationService.fail(player, ended);
        PacketDistributor.sendToPlayer(player, new TalismanResultS2CPayload(this.containerId,
                TalismanResultS2CPayload.FAILED, 0.0D, false,
                Component.empty(), ItemStack.EMPTY, ended.pigmentSpent()));
        this.sendList(player);
    }

    /**
     * The crafting table's exit, in the crafting table's order: the abandoned drawing gets its verdict first, and
     * then the transient slot goes back to the player - including what a refund just wrote into it. The access wraps
     * the sweep the way {@code CraftingMenu.removed} does: the client's menu holds {@code ContainerLevelAccess.NULL},
     * whose {@code evaluate} is always empty, so the slot is only swept on the server.
     */
    @Override
    public void removed(@NonNull Player player) {
        super.removed(player);
        if (this.session != null) {
            if (player instanceof ServerPlayer serverPlayer) TalismanWorkstationService.abandon(serverPlayer, this.session);
            this.session = null;
        }
        this.access.execute((level, pos) -> this.clearContainer(player, this.paper));
    }

    @Override
    public @NonNull ItemStack quickMoveStack(@NonNull Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack original = slot.getItem().copy();
        boolean moved = index == PAPER_SLOT
                ? this.moveItemStackTo(slot.getItem(), PLAYER_INVENTORY_START, PLAYER_HOTBAR_END, true)
                : this.moveItemStackTo(slot.getItem(), PAPER_SLOT, PAPER_SLOT + 1, false);
        if (moved) {
            if (slot.getItem().isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
            else slot.setChanged();
        }
        return original;
    }

    @Override
    public boolean stillValid(@NonNull Player player) {
        if (!this.hasStation) return true;
        return stillValid(this.access, player, MxtBlocks.TALISMAN_WORKSTATION.get());
    }

    /**
     * The station container and the layout it is opened with; the container has to exist before the menu, so the
     * public constructors build this and hand it to the private one.
     */
    private static final class Setup {
        private final SimpleContainer paper = new SimpleContainer(1);
        private final PageSlots.Layout page;

        private Setup() {
            // The page's station container declares one cell, and the menu keeps one station-side slot behind it;
            // the page's inventory container is the player's own 36.
            this.page = PageSlots.of(AuiPages.talismanPage())
                    .container("station", this.paper, (container, index, x, y) ->
                            new FilteredSlot(container, index, x, y, stack -> stack.is(TalismanDrawingRecipe.paper())))
                    .player("inventory")
                    .build();
        }

        private PageSlots.Layout page() {
            return this.page;
        }

        private SimpleContainer paper() {
            return this.paper;
        }
    }
}
