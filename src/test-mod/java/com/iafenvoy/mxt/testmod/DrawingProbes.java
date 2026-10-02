package com.iafenvoy.mxt.testmod;

import com.iafenvoy.mxt.data.Talisman;
import com.iafenvoy.mxt.data.aura.SpiritStorageComponent;
import com.iafenvoy.mxt.data.item.TalismanComponent;
import com.iafenvoy.mxt.item.block.TalismanWorkstationBlock;
import com.iafenvoy.mxt.registry.MxtBlocks;
import com.iafenvoy.mxt.registry.MxtDataComponents;
import com.iafenvoy.mxt.registry.MxtItems;
import com.iafenvoy.mxt.recipe.TalismanDrawingRecipe;
import com.iafenvoy.mxt.runtime.talisman.BrushPigmentService;
import com.iafenvoy.mxt.runtime.talisman.TalismanDrawingScorer;
import com.iafenvoy.mxt.runtime.talisman.TalismanDrawingScorer.Point;
import com.iafenvoy.mxt.runtime.talisman.TalismanDrawingScorer.Stroke;
import com.iafenvoy.mxt.runtime.talisman.TalismanDrawingSession;
import com.iafenvoy.mxt.runtime.talisman.TalismanWorkstationService;
import com.iafenvoy.mxt.runtime.talisman.TalismanWorkstationService.Entry;
import com.iafenvoy.mxt.runtime.talisman.TalismanWorkstationService.Outcome;
import com.iafenvoy.mxt.runtime.talisman.TalismanWorkstationService.StrokeRefusal;
import com.iafenvoy.mxt.runtime.talisman.TalismanWorkstationService.StrokeResult;
import com.iafenvoy.mxt.screen.menu.TalismanWorkstationMenu;
import com.iafenvoy.mxt.util.HolderHelper;
import com.mojang.authlib.GameProfile;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ServerLevelData;
import net.neoforged.neoforge.common.util.FakePlayer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * The drawing workstation's session and settlement path, driven from a dedicated server with no client: the formula
 * list and what makes a formula affordable, opening a session (which takes the paper out of the menu's own slot),
 * charging strokes to the brush, the refusals, the settlement (both the carrier it pours and the reconciliation
 * that rejects a doctored drawing), and the two ways a session ends without being submitted.
 *
 * <p>Every step prints one {@code OK} / {@code MISMATCH} line and every failure names what was actually read. A
 * console invocation has no player, so the leg builds a {@link FakePlayer} of its own; nothing on this path needs a
 * real one, because the carrier's pour is metered from the carrier's own store rather than from a holder's account.
 * The scratch station is a real block of this mod, placed {@code (4, 4, 4)} from the source, and the state that was
 * there before is put back when the leg ends. The station stores nothing, so every fixture goes into the slot of a
 * menu this leg opened itself.
 */
public final class DrawingProbes {
    private static final Identifier SIGIL = Identifier.fromNamespaceAndPath(MxtTestMod.MOD_ID, "talisman/drawing_sigil");
    private static final Identifier WARD = Identifier.fromNamespaceAndPath(MxtTestMod.MOD_ID, "talisman/drawing_ward");
    private static final Identifier CARRIER = Identifier.fromNamespaceAndPath(MxtTestMod.MOD_ID, "drawing_sigil");
    private static final Identifier QUALITY = Identifier.fromNamespaceAndPath(MxtTestMod.MOD_ID, "excellent");
    private static final int PIGMENT = 1_000;
    private static final double PERFECT_FLOOR = 0.90D;

    private DrawingProbes() {
    }

    public static int run(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            FakePlayer fallback = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "DrawingProbe"));
            fallback.setPos(source.getPosition());
            player = fallback;
        }
        Probe probe = new Probe(source, level, player);
        try {
            probe.setup();
            probe.dip();
            probe.list();
            probe.start();
            probe.swap();
            probe.strokes();
            probe.submit();
            probe.cancelAndRefund();
            probe.reconciliation();
            probe.stackRefund();
        } finally {
            probe.restore();
        }
        ServerPlayer finalPlayer = player;
        source.sendSuccess(() -> Component.literal("drawing probe [" + finalPlayer.getName().getString() + "]: "
                + (probe.failed == 0 ? "OK" : "MISMATCH") + " " + probe.passed + " checks passed, "
                + probe.failed + " mismatched"), probe.failed > 0);
        return probe.failed == 0 ? 1 : 0;
    }

    /**
     * One run of the leg: the scratch station, the lines it reports and the state it has to give back.
     */
    private static final class Probe {
        private final CommandSourceStack source;
        private final ServerLevel level;
        private final ServerPlayer player;
        private final BlockPos at;
        private final BlockState previousState;
        private final AbstractContainerMenu previousMenu;
        private final long previousGameTime;
        private final List<Stroke> reference = List.of(
                new Stroke(List.of(new Point(0.50D, 0.06D), new Point(0.50D, 0.94D))),
                new Stroke(List.of(new Point(0.22D, 0.28D), new Point(0.50D, 0.10D), new Point(0.78D, 0.28D))),
                new Stroke(List.of(new Point(0.30D, 0.72D), new Point(0.70D, 0.72D))));
        private int passed;
        private int failed;

        private Probe(CommandSourceStack source, ServerLevel level, ServerPlayer player) {
            this.source = source;
            this.level = level;
            this.player = player;
            this.at = BlockPos.containing(source.getPosition()).offset(4, 4, 4);
            this.previousState = level.getBlockState(this.at);
            if (!level.setBlockAndUpdate(this.at, MxtBlocks.TALISMAN_WORKSTATION.get().defaultBlockState()
                    .setValue(TalismanWorkstationBlock.FACING, Direction.NORTH)))
                throw new IllegalStateException("The drawing probe could not place its workstation");
            this.previousMenu = player.containerMenu;
            this.previousGameTime = this.clock().getGameTime();
        }

        // The leg moves the shared clock to satisfy the minimum interval between strokes, so it puts it back: the
        // ticks it hands itself are not the server's to lose.
        private ServerLevelData clock() {
            return this.level.getServer().getWorldData().overworldData();
        }

        private void setup() {
            this.open();
            ItemStack carried = this.carried();
            this.check("setup-station-and-brush",
                    this.paper().getContainerSize() == 1 && this.paper().getItem(0).isEmpty()
                            && BrushPigmentService.isBrush(carried) && BrushPigmentService.pigment(carried) == PIGMENT,
                    "slots " + this.paper().getContainerSize() + ", slot "
                            + read(this.paper().getItem(0)) + ", carried " + read(carried) + ", pigment "
                            + BrushPigmentService.pigment(carried));
        }

        // Dipping, in the vanilla bundle's shape: a click on a pigment stack feeds the brush one portion, whichever
        // mouse button it is. The click goes through the menu, which is the same path a client's click takes, and
        // only the server half of it changes anything. The station has no pigment slot, so the stack sits in the
        // player's own inventory: the hook is on the brush, not on a station cell, and that is the whole point of it.
        private void dip() {
            TalismanWorkstationMenu menu = this.menu();
            int per = BrushPigmentService.perPortion();
            int capacity = BrushPigmentService.capacity();
            Slot paper = menu.slots.get(TalismanWorkstationMenu.PAPER_SLOT);
            this.check("paper-slot-takes-only-paper",
                    paper.mayPlace(MxtItems.BLANK_TALISMAN.toStack()) && !paper.mayPlace(MxtItems.CINNABAR.toStack()),
                    "paper " + paper.mayPlace(MxtItems.BLANK_TALISMAN.toStack()) + ", cinnabar "
                            + paper.mayPlace(MxtItems.CINNABAR.toStack()));

            Slot bag = this.inventorySlot(menu, 0);
            if (bag == null) {
                this.mismatch("dip-takes-one-portion", "the player's first inventory slot is not in the menu");
                return;
            }
            ItemStack previous = bag.getItem().copy();

            // A brush on the cursor, clicked onto a pigment stack: one portion, one item, whatever the button.
            bag.set(MxtItems.CINNABAR.toStack(4));
            menu.setCarried(brush(0));
            menu.clicked(bag.index, 1, ContainerInput.PICKUP, this.player);
            this.check("dip-takes-one-portion",
                    bag.getItem().getCount() == 3
                            && BrushPigmentService.pigment(this.carried()) == per
                            && this.paper().getItem(0).isEmpty(),
                    "bag " + read(bag.getItem()) + ", pigment "
                            + BrushPigmentService.pigment(this.carried()) + " of " + capacity);

            menu.clicked(bag.index, 0, ContainerInput.PICKUP, this.player);
            this.check("dip-one-portion-per-click-either-button",
                    bag.getItem().getCount() == 2 && BrushPigmentService.pigment(this.carried()) == 2 * per,
                    "bag " + read(bag.getItem()) + ", pigment "
                            + BrushPigmentService.pigment(this.carried()) + " of " + capacity);

            // A full brush takes nothing at all.
            menu.setCarried(brush(capacity));
            menu.clicked(bag.index, 1, ContainerInput.PICKUP, this.player);
            this.check("dip-when-full-takes-nothing",
                    bag.getItem().getCount() == 2 && BrushPigmentService.pigment(this.carried()) == capacity,
                    "bag " + read(bag.getItem()) + ", pigment "
                            + BrushPigmentService.pigment(this.carried()));

            // Without a brush on the cursor the click stays the vanilla one: the pigment moves instead of being
            // eaten one portion at a time, which is what a hook that fired anyway would show.
            menu.setCarried(MxtItems.BLANK_TALISMAN.toStack());
            menu.clicked(bag.index, 1, ContainerInput.PICKUP, this.player);
            this.check("dip-needs-the-brush-on-the-cursor",
                    this.carried().is(MxtItems.CINNABAR.get()) && this.carried().getCount() == 2
                            && bag.getItem().is(MxtItems.BLANK_TALISMAN.get()),
                    "carried " + read(this.carried()) + ", bag " + read(bag.getItem()));

            // The same gesture from the other side: the brush sits in a slot and the pigment is on the cursor.
            bag.set(brush(0));
            menu.setCarried(MxtItems.CINNABAR.toStack(2));
            menu.clicked(bag.index, 0, ContainerInput.PICKUP, this.player);
            this.check("dip-into-a-stowed-brush",
                    BrushPigmentService.pigment(bag.getItem()) == per && this.carried().getCount() == 1,
                    "brush " + BrushPigmentService.pigment(bag.getItem()) + " of " + capacity + ", carried "
                            + read(this.carried()));

            bag.set(previous);
            menu.setCarried(brush(PIGMENT));
        }

        /**
         * The menu slot holding one player inventory stack, or null when the menu has no such slot.
         */
        private Slot inventorySlot(TalismanWorkstationMenu menu, int inventoryIndex) {
            return menu.slots.stream()
                    .filter(candidate -> candidate.container == this.player.getInventory()
                            && candidate.getContainerSlot() == inventoryIndex)
                    .findFirst().orElse(null);
        }

        // The formula list: what the paper in the station slot makes affordable, and what taking it out takes away.
        private void list() {
            this.paper().setItem(0, MxtItems.BLANK_TALISMAN.toStack());
            List<Entry> stocked = TalismanWorkstationService.entries(this.player, this.paper());
            Entry entry = entry(stocked);
            this.check("list-names-the-formula", entry != null, "the list holds " + names(stocked));
            if (entry == null) {
                this.mismatch("list-affordable-with-paper", "the formula is not in the list");
                this.mismatch("list-not-affordable-without-paper", "the formula is not in the list");
                return;
            }
            this.check("list-affordable-with-paper", entry.affordable(), "affordable " + entry.affordable());
            ItemStack lifted = this.paper().removeItem(0, 1);
            Entry emptied = entry(TalismanWorkstationService.entries(this.player, this.paper()));
            this.check("list-not-affordable-without-paper", emptied != null && !emptied.affordable(),
                    emptied == null ? "the formula left the list" : "affordable " + emptied.affordable());
            this.paper().setItem(0, lifted);

            // The brush is charged per stroke, so an empty cursor must not grey the list.
            ItemStack brush = this.carried();
            this.menu().setCarried(ItemStack.EMPTY);
            Entry unbrushed = entry(TalismanWorkstationService.entries(this.player, this.paper()));
            this.check("list-affordable-without-brush", unbrushed != null && unbrushed.affordable(),
                    unbrushed == null ? "the formula left the list" : "affordable " + unbrushed.affordable());
            this.menu().setCarried(brush);
        }

        // Opening a session: the paper leaves the slot with it, and the session remembers which formula it is.
        private void start() {
            // The menu opened during setup is still the one open, and the session will belong to that same object.
            this.menu().select(this.player, SIGIL);
            TalismanDrawingSession session = this.session();
            this.check("start-took-the-paper", session != null && this.paper().getItem(0).isEmpty(),
                    session == null ? "no session" : "slot " + read(this.paper().getItem(0)));
            this.check("start-recorded-the-formula", session != null && SIGIL.equals(session.recipeId()),
                    session == null ? "no session" : String.valueOf(session.recipeId()));
            // The test formulas name no colours, so what the session's recipe carries are the codec defaults.
            this.check("start-reads-the-formula-colours",
                    session != null && session.recipe().backgroundColor() == TalismanDrawingRecipe.DEFAULT_BACKGROUND_COLOR
                            && session.recipe().foregroundColor() == TalismanDrawingRecipe.DEFAULT_FOREGROUND_COLOR,
                    session == null ? "no session" : "paper " + session.recipe().backgroundColor()
                            + ", ink " + session.recipe().foregroundColor());
        }

        // Two rules the service alone cannot answer: picking a formula does not need the brush (the ink is charged
        // per stroke), and until the first stroke the formula may be swapped - which only works because the paper
        // an unpainted session holds still counts as spendable.
        private void swap() {
            TalismanDrawingSession opened = this.session();
            if (opened == null || !SIGIL.equals(opened.recipeId())) {
                this.mismatch("swap-starts-from-the-first-formula",
                        opened == null ? "no session" : String.valueOf(opened.recipeId()));
                return;
            }
            ItemStack brush = this.carried();
            this.menu().setCarried(ItemStack.EMPTY);
            this.menu().select(this.player, WARD);
            TalismanDrawingSession swapped = this.session();
            this.check("swap-needs-no-brush",
                    swapped != null && WARD.equals(swapped.recipeId()) && this.paper().getItem(0).isEmpty(),
                    swapped == null ? "no session" : "formula " + swapped.recipeId() + ", slot "
                            + read(this.paper().getItem(0)));

            List<Entry> listed = swapped == null ? List.of() : TalismanWorkstationService.entries(this.player,
                    this.paper(), swapped.chargedItems().isEmpty()
                            ? ItemStack.EMPTY : swapped.chargedItems().getFirst());
            Entry held = entry(listed);
            this.check("swap-keeps-the-next-formula-pickable", held != null && held.affordable(),
                    held == null ? "the formula left the list" : "affordable " + held.affordable());

            this.menu().setCarried(brush);
            this.menu().select(this.player, SIGIL);
            TalismanDrawingSession back = this.session();
            this.check("swap-back-is-also-free",
                    back != null && SIGIL.equals(back.recipeId()) && this.paper().getItem(0).isEmpty(),
                    back == null ? "no session" : "formula " + back.recipeId() + ", slot "
                            + read(this.paper().getItem(0)));
        }

        // Charging one stroke at a time, the two refusals a stroke can meet, and nothing else moving.
        private void strokes() {            TalismanDrawingSession session = this.session();
            if (session == null) {
                this.mismatch("strokes-one-at-a-time", "there is no session to draw in");
                return;
            }
            // The first stroke is measured against the tick the session opened, so even the first one needs the
            // interval to have passed; the rest are held apart by the same rule.
            List<Stroke> bitmap = this.bitmap();
            int charges = 0;
            for (int index = 0; index < bitmap.size(); index++) {
                this.advance();
                List<Point> points = bitmap.get(index).points();
                int before = BrushPigmentService.pigment(this.carried());
                int recorded = session.strokeCount();
                long when = this.level.getGameTime();
                StrokeResult result = TalismanWorkstationService.stroke(this.player, session, points);
                int expected = BrushPigmentService.chargeForStroke(points);
                charges += expected;
                this.check("stroke-" + (index + 1) + "-accepted", result.accepted(), "refused " + result.refusal());
                this.check("stroke-" + (index + 1) + "-charged",
                        result.charged() == expected && before - BrushPigmentService.pigment(this.carried()) == expected,
                        "charged " + result.charged() + ", expected " + expected + ", pigment " + before
                                + " -> " + BrushPigmentService.pigment(this.carried()));
                this.check("stroke-" + (index + 1) + "-recorded",
                        session.strokeCount() == recorded + 1 && session.lastStrokeAt() == when,
                        "count " + recorded + " -> " + session.strokeCount() + ", last stroke at "
                                + session.lastStrokeAt() + ", tick " + when);
            }

            // Two strokes inside one tick are under any minimum interval, so the refusal is met without writing a
            // configuration value: nothing is charged, nothing is recorded and the refusal is counted.
            int pigment = BrushPigmentService.pigment(this.carried());
            int recorded = session.strokeCount();
            int refusals = session.rejected();
            long when = this.level.getGameTime();
            StrokeResult fast = TalismanWorkstationService.stroke(this.player, session, bitmap.getFirst().points());
            this.check("stroke-refused-too-fast",
                    !fast.accepted() && fast.refusal() == StrokeRefusal.TOO_FAST
                            && BrushPigmentService.pigment(this.carried()) == pigment
                            && session.strokeCount() == recorded && session.rejected() == refusals + 1,
                    "refusal " + fast.refusal() + ", pigment " + pigment + " -> "
                            + BrushPigmentService.pigment(this.carried()) + ", count " + recorded + " -> "
                            + session.strokeCount() + ", refusals " + refusals + " -> " + session.rejected()
                            + ", last stroke at " + session.lastStrokeAt() + ", tick " + when);

            // An empty brush is refused after the interval has been satisfied, so the reason can only be the pigment.
            this.advance();
            this.carried().set(MxtDataComponents.BRUSH_PIGMENT, 0);
            int emptyRecords = session.strokeCount();
            int emptyRefusals = session.rejected();
            StrokeResult dry = TalismanWorkstationService.stroke(this.player, session, bitmap.get(1).points());
            this.check("stroke-refused-no-pigment",
                    !dry.accepted() && dry.refusal() == StrokeRefusal.NO_PIGMENT
                            && BrushPigmentService.pigment(this.carried()) == 0
                            && session.strokeCount() == emptyRecords && session.rejected() == emptyRefusals + 1,
                    "refusal " + dry.refusal() + ", pigment " + BrushPigmentService.pigment(this.carried())
                            + ", count " + emptyRecords + " -> " + session.strokeCount() + ", refusals "
                            + emptyRefusals + " -> " + session.rejected());
            this.carried().set(MxtDataComponents.BRUSH_PIGMENT, PIGMENT);
            this.note("stroke-charges", "total " + charges + " of " + PIGMENT + " pigment");

            // Once something has been drawn the formula is fixed: a swap would have to throw that stroke away.
            TalismanDrawingSession drawn = this.session();
            int drawnStrokes = drawn == null ? -1 : drawn.strokeCount();
            this.menu().select(this.player, WARD);
            TalismanDrawingSession after = this.session();
            this.check("swap-refused-after-a-stroke",
                    after != null && SIGIL.equals(after.recipeId()) && after.strokeCount() == drawnStrokes,
                    after == null ? "no session" : "formula " + after.recipeId() + ", strokes "
                            + after.strokeCount() + " (was " + drawnStrokes + ")");
        }

        // Settlement: the whole drawing submitted turns into the carrier the top grade describes.
        private void submit() {
            TalismanDrawingSession session = this.session();
            if (session == null) {
                this.mismatch("submit-succeeded", "there is no session to submit");
                return;
            }
            List<Stroke> bitmap = this.bitmap();
            List<List<Point>> drawn = bitmap.stream().map(Stroke::points).toList();
            int expected = 0;
            for (Stroke stroke : bitmap) expected += BrushPigmentService.chargeForStroke(stroke.points());
            Outcome outcome = TalismanWorkstationService.submit(this.player, session, drawn);
            this.check("submit-succeeded", outcome.success(), "success " + outcome.success());
            this.check("submit-completion-at-least-0.90",
                    outcome.completion() >= PERFECT_FLOOR && outcome.completion() <= 1.0D,
                    "completion " + outcome.completion());
            this.check("submit-pigment-spent", outcome.pigmentSpent() == expected,
                    "spent " + outcome.pigmentSpent() + ", expected " + expected);
            ItemStack held = this.paper().getItem(0);
            this.check("submit-slot-holds-a-carrier", held.is(MxtItems.TALISMAN.get()), read(held));
            TalismanComponent inscribed = held.get(MxtDataComponents.TALISMAN);
            this.check("submit-carrier-names-the-formula",
                    inscribed != null && inscribed.talismans().size() == 1
                            && HolderHelper.id(inscribed.talismans().getFirst()).equals(CARRIER),
                    inscribed == null ? "no inscription" : String.valueOf(inscriptions(inscribed.talismans())));
            Holder<?> quality = held.get(MxtDataComponents.QUALITY);
            this.check("submit-carrier-is-the-top-grade-quality",
                    quality != null && HolderHelper.id(quality).equals(QUALITY), String.valueOf(quality));
            SpiritStorageComponent charge = held.get(MxtDataComponents.SPIRIT_STORAGE);
            this.check("submit-carrier-was-poured", charge != null && !charge.isEmpty(),
                    charge == null ? "no spirit storage" : charge.amounts().toString());
            this.check("submit-names-the-quality", !outcome.text().getString().isEmpty(),
                    "text '" + outcome.text().getString() + "'");
            this.note("submit-measured", "completion " + outcome.completion() + ", pigment spent "
                    + outcome.pigmentSpent() + ", quality " + (quality == null ? "none" : HolderHelper.id(quality))
                    + ", spirit storage " + (charge == null ? "none" : charge.amounts().size() + " aura(s), "
                    + charge.amounts().values().doubleStream().sum() + " unit(s)"));
            this.paper().setItem(0, ItemStack.EMPTY);
        }

        // Ending a session without submitting it: nothing drawn hands everything back, one stroke does not.
        private void cancelAndRefund() {
            this.advance();
            int perStroke = BrushPigmentService.chargeForStroke(this.bitmap().getFirst().points());
            TalismanWorkstationMenu cancelMenu = this.open();
            this.paper().setItem(0, MxtItems.BLANK_TALISMAN.toStack());
            cancelMenu.select(this.player, SIGIL);
            cancelMenu.cancel(this.player);
            ItemStack back = this.paper().getItem(0);
            this.check("cancel-with-no-strokes-returns-the-paper",
                    back.is(MxtItems.BLANK_TALISMAN.get()) && back.getCount() == 1 && cancelMenu.session() == null,
                    cancelMenu.session() == null ? read(back) : "the session is still open");
            this.check("cancel-with-no-strokes-keeps-the-pigment", BrushPigmentService.pigment(this.carried()) == PIGMENT,
                    "pigment " + BrushPigmentService.pigment(this.carried()));

            this.advance();
            TalismanWorkstationMenu spentMenu = this.open();
            this.paper().setItem(0, MxtItems.BLANK_TALISMAN.toStack());
            spentMenu.select(this.player, SIGIL);
            TalismanDrawingSession session = spentMenu.session();
            if (session == null) {
                this.mismatch("cancel-after-one-stroke-consumes-the-materials", "the session did not open");
                return;
            }
            int before = BrushPigmentService.pigment(this.carried());
            // A stroke is only taken once the minimum interval has passed since the session opened.
            this.advance();
            TalismanWorkstationService.stroke(this.player, session, this.bitmap().getFirst().points());
            spentMenu.cancel(this.player);
            this.check("cancel-after-one-stroke-consumes-the-materials",
                    this.paper().getItem(0).isEmpty() && spentMenu.session() == null,
                    "slot " + read(this.paper().getItem(0)));
            this.check("cancel-after-one-stroke-spent-the-pigment",
                    before - BrushPigmentService.pigment(this.carried()) == perStroke,
                    "pigment " + before + " -> " + BrushPigmentService.pigment(this.carried())
                            + ", expected a charge of " + perStroke);
        }

        // A stack in the slot: each session takes one sheet, so each refund has to put exactly one back - and a close
        // has to hand the whole slot to the player, which is the crafting table's contract this station now keeps.
        private void stackRefund() {
            TalismanWorkstationMenu menu = this.open();
            this.paper().setItem(0, MxtItems.BLANK_TALISMAN.toStack(3));
            int bag = this.carriedPapers();
            menu.select(this.player, SIGIL);
            this.check("stack-start-takes-one-sheet", this.paper().getItem(0).getCount() == 2 && this.carriedPapers() == bag,
                    "slot " + read(this.paper().getItem(0)) + ", carried papers " + this.carriedPapers()
                            + " (was " + bag + ")");

            menu.select(this.player, WARD);
            TalismanDrawingSession swapped = menu.session();
            this.check("stack-swap-returns-what-it-took",
                    swapped != null && WARD.equals(swapped.recipeId())
                            && this.paper().getItem(0).getCount() == 2 && this.carriedPapers() == bag,
                    "formula " + (swapped == null ? "none" : swapped.recipeId()) + ", slot "
                            + read(this.paper().getItem(0)) + ", carried papers " + this.carriedPapers()
                            + " (was " + bag + ")");

            // Closing the screen: the unpainted session hands its sheet back into the slot, and the slot then goes to
            // the player. Nothing of it may stay in the world, so what is counted is the player's own inventory.
            if (swapped == null) return;
            this.menu().removed(this.player);
            this.check("stack-close-hands-the-slot-to-the-player",
                    this.paper().getItem(0).isEmpty() && this.carriedPapers() == bag + 3,
                    "slot " + read(this.paper().getItem(0)) + ", carried papers " + this.carriedPapers()
                            + " (wanted " + (bag + 3) + ")");
        }

        /** Blank talismans in the player's own inventory; a refund must never add to this. */
        private int carriedPapers() {
            Inventory bag = this.player.getInventory();
            int total = 0;
            for (int slot = 0; slot < bag.getContainerSize(); slot++) {
                ItemStack stack = bag.getItem(slot);
                if (stack.is(MxtItems.BLANK_TALISMAN.get())) total += stack.getCount();
            }
            return total;
        }

        // A drawing that does not match the strokes that arrived one at a time is discarded and produces nothing.
        // Each way of doctoring it gets a session of its own, because the rejection consumes what that session took.
        private void reconciliation() {
            this.advance();
            this.checkRejected("reconciliation-dropped-point", 1, 1);
            this.advance();
            this.checkRejected("reconciliation-extra-stroke", 1, 2);
            this.advance();
            this.checkRejected("reconciliation-swapped-strokes", 2, 2);
        }

        private void checkRejected(String step, int drawn, int submitted) {
            TalismanWorkstationMenu menu = this.open();
            this.paper().setItem(0, MxtItems.BLANK_TALISMAN.toStack());
            menu.select(this.player, SIGIL);
            TalismanDrawingSession session = menu.session();
            if (session == null) {
                this.mismatch(step, "the session did not open");
                return;
            }
            List<Stroke> bitmap = this.bitmap();
            List<List<Point>> recorded = new ArrayList<>();
            for (int index = 0; index < drawn; index++) {
                this.advance();
                List<Point> points = bitmap.get(index).points();
                recorded.add(points);
                TalismanWorkstationService.stroke(this.player, session, points);
            }
            boolean success = TalismanWorkstationService.submit(this.player, session, this.doctored(recorded, submitted)).success();
            this.check(step, !success && this.paper().getItem(0).isEmpty(),
                    "success " + success + ", slot " + read(this.paper().getItem(0)));
        }

        // Dropping a point, adding a stroke and swapping two are the three ways the whole-drawing comparison can
        // disagree with what arrived one stroke at a time.
        private List<List<Point>> doctored(List<List<Point>> recorded, int submitted) {
            List<List<Point>> doctored = new ArrayList<>();
            for (List<Point> stroke : recorded) doctored.add(new ArrayList<>(stroke));
            if (recorded.size() == 1) {
                if (submitted == 1) doctored.getFirst().remove(1);
                else doctored.add(this.bitmap().get(2).points());
            } else {
                Collections.swap(doctored, 0, 1);
            }
            return doctored;
        }

        /** The three reference strokes of the test pack's formula, in the bitmap pixels a player's strokes arrive in. */
        private List<Stroke> bitmap() {
            List<Stroke> converted = new ArrayList<>();
            for (Stroke stroke : this.reference) {
                List<Point> points = new ArrayList<>();
                for (Point point : stroke.points())
                    points.add(new Point(point.x() * TalismanDrawingScorer.CANVAS_WIDTH,
                            point.y() * TalismanDrawingScorer.CANVAS_HEIGHT));
                converted.add(new Stroke(List.copyOf(points)));
            }
            return converted;
        }

        private TalismanWorkstationMenu open() {
            TalismanWorkstationMenu menu = new TalismanWorkstationMenu(0, this.player.getInventory(),
                    ContainerLevelAccess.create(this.level, this.at));
            menu.setCarried(brush(PIGMENT));
            this.player.containerMenu = menu;
            return menu;
        }

        private TalismanWorkstationMenu menu() {
            return (TalismanWorkstationMenu) this.player.containerMenu;
        }

        /**
         * The open menu's own slot. Nothing is stored in the world, so a fixture has to be put in after its menu is
         * open - a new menu starts empty, exactly as it does for a player.
         */
        private Container paper() {
            return this.menu().paper();
        }

        private TalismanDrawingSession session() {
            return this.player.containerMenu instanceof TalismanWorkstationMenu open ? open.session() : null;
        }

        private ItemStack carried() {
            return this.player.containerMenu.getCarried();
        }

        private void advance() {
            ServerLevelData clock = this.clock();
            clock.setGameTime(clock.getGameTime() + 10L);
        }

        private void restore() {
            try {
                if (this.player.containerMenu instanceof TalismanWorkstationMenu open) open.removed(this.player);
                if (this.previousMenu != null) this.player.containerMenu = this.previousMenu;
                this.clock().setGameTime(this.previousGameTime);
            } finally {
                this.level.setBlockAndUpdate(this.at, this.previousState);
            }
        }

        private void check(String step, boolean ok, String actual) {
            if (ok) {
                this.passed++;
                this.source.sendSuccess(() -> Component.literal("drawing " + step + ": OK"), false);
            } else {
                this.failed++;
                this.source.sendSuccess(() -> Component.literal("drawing " + step + ": MISMATCH " + actual), false);
            }
        }

        private void mismatch(String step, String actual) {
            this.check(step, false, actual);
        }

        /** A measured number a passing run would otherwise not show; informational, never an assertion. */
        private void note(String step, String detail) {
            this.source.sendSuccess(() -> Component.literal("[info] drawing " + step + ": " + detail), false);
        }
    }

    private static Entry entry(List<Entry> entries) {
        for (Entry entry : entries) if (SIGIL.equals(entry.id())) return entry;
        return null;
    }

    private static List<String> names(List<Entry> entries) {
        List<String> names = new ArrayList<>();
        for (Entry entry : entries) names.add(entry.id() + (entry.affordable() ? " (affordable)" : " (locked)"));
        return names;
    }

    private static List<String> inscriptions(List<Holder<Talisman>> holders) {
        List<String> names = new ArrayList<>();
        for (Holder<Talisman> holder : holders) names.add(HolderHelper.id(holder).toString());
        return names;
    }

    private static ItemStack brush(int pigment) {
        ItemStack stack = MxtItems.TALISMAN_BRUSH.toStack();
        stack.set(MxtDataComponents.BRUSH_PIGMENT, pigment);
        return stack;
    }

    private static String read(ItemStack stack) {
        return stack.isEmpty() ? "empty" : stack.getCount() + " x " + stack.getItem();
    }
}
