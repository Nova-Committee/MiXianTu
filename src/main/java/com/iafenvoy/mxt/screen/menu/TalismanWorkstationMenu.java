package com.iafenvoy.mxt.screen.menu;

import com.iafenvoy.mxt.config.MxtServerConfig;
import com.iafenvoy.mxt.item.block.entity.TalismanWorkstationBlockEntity;
import com.iafenvoy.mxt.network.payload.TalismanDrawingListS2CPayload;
import com.iafenvoy.mxt.network.payload.TalismanDrawingStartS2CPayload;
import com.iafenvoy.mxt.network.payload.TalismanResultS2CPayload;
import com.iafenvoy.mxt.network.payload.TalismanStrokeAckS2CPayload;
import com.iafenvoy.mxt.recipe.TalismanDrawingRecipe;
import com.iafenvoy.mxt.registry.MxtBlocks;
import com.iafenvoy.mxt.registry.MxtMenus;
import com.iafenvoy.mxt.runtime.talisman.BrushPigmentService;
import com.iafenvoy.mxt.runtime.talisman.TalismanDrawingScorer.Stroke;
import com.iafenvoy.mxt.runtime.talisman.TalismanDrawingSession;
import com.iafenvoy.mxt.runtime.talisman.TalismanWorkstationService;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * The workstation's menu, and the only place a drawing session lives: two players at the same station keep two
 * sessions, and {@link #removed(Player)} is where an abandoned one settles. The two station slots are the block
 * entity's, so the paper and the pigment are shared and saved; everything else travels to this side as payloads.
 *
 * <p>The client builds this menu with placeholder containers, the way the vanilla furnace does: slot contents
 * arrive through the vanilla slot packets, not through the block entity.
 */
public final class TalismanWorkstationMenu extends AbstractContainerMenu {
    public static final int PAPER_SLOT = 0;
    public static final int PIGMENT_SLOT = 1;
    private static final int PLAYER_INVENTORY_START = 2;
    private static final int PLAYER_HOTBAR_END = PLAYER_INVENTORY_START + 36;
    /**
     * Ticks between two unconditional list pushes, for what the inputs below cannot see.
     */
    private static final int LIST_REFRESH_TICKS = 20;

    private final Container paper;
    private final Container pigment;
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

    public TalismanWorkstationMenu(int containerId, Inventory inventory, TalismanWorkstationBlockEntity station) {
        this(containerId, inventory, station.paper(), station.pigment(),
                ContainerLevelAccess.create(station.getLevel(), station.getBlockPos()), true);
        this.opener = inventory.player instanceof ServerPlayer player ? player : null;
    }

    public TalismanWorkstationMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, new SimpleContainer(1), new SimpleContainer(1), ContainerLevelAccess.NULL, false);
    }

    private TalismanWorkstationMenu(int containerId, Inventory inventory, Container paper, Container pigment,
                                    ContainerLevelAccess access, boolean hasStation) {
        super(MxtMenus.TALISMAN_WORKSTATION.get(), containerId);
        checkContainerSize(paper, 1);
        checkContainerSize(pigment, 1);
        this.paper = paper;
        this.pigment = pigment;
        this.access = access;
        this.hasStation = hasStation;
        // The geometry here is a placeholder: the page's cells rewrite every slot's x/y each frame.
        this.addSlot(new FilteredSlot(paper, 0, 24, 108, stack -> stack.is(TalismanDrawingRecipe.paper())));
        this.addSlot(new FilteredSlot(pigment, 0, 44, 108, BrushPigmentService::isPigment));
        for (int row = 0; row < 3; row++)
            for (int column = 0; column < 9; column++)
                this.addSlot(new Slot(inventory, column + row * 9 + 9,
                        8 + column * 18, 220 + row * 18));
        for (int column = 0; column < 9; column++)
            this.addSlot(new Slot(inventory, column, 8 + column * 18, 278));
    }

    public Container paper() {
        return this.paper;
    }

    public Container pigment() {
        return this.pigment;
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
            TalismanWorkstationService.settle(player, this.session);
            this.session = null;
        }
        TalismanWorkstationService.start(player, this.paper, recipeId).ifPresent(started -> {
            this.session = started;
            TalismanDrawingRecipe.Pattern pattern = started.recipe().pattern();
            PacketDistributor.sendToPlayer(player, new TalismanDrawingStartS2CPayload(this.containerId, recipeId,
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
     * Ending the drawing by hand; with no strokes drawn the server hands everything back.
     */
    public void cancel(ServerPlayer player) {
        if (this.session == null) return;
        TalismanWorkstationService.settle(player, this.session);
        this.session = null;
        PacketDistributor.sendToPlayer(player, new TalismanResultS2CPayload(this.containerId,
                TalismanResultS2CPayload.CANCELLED, 0.0D, false,
                Component.empty(), ItemStack.EMPTY, 0));
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

    @Override
    public void removed(@NonNull Player player) {
        super.removed(player);
        if (this.session != null && player instanceof ServerPlayer serverPlayer)
            TalismanWorkstationService.settle(serverPlayer, this.session);
        this.session = null;
    }

    @Override
    public @NonNull ItemStack quickMoveStack(@NonNull Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack original = slot.getItem().copy();
        boolean moved = index == PAPER_SLOT || index == PIGMENT_SLOT
                ? this.moveItemStackTo(slot.getItem(), PLAYER_INVENTORY_START, PLAYER_HOTBAR_END, true)
                : this.moveItemStackTo(slot.getItem(), PAPER_SLOT, PIGMENT_SLOT + 1, false);
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
}
