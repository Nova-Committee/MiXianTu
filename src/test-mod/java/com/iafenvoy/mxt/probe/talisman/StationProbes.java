package com.iafenvoy.mxt.probe.talisman;

import com.iafenvoy.mxt.probe.Probe;
import com.iafenvoy.mxt.probe.ProbeContext;
import com.iafenvoy.mxt.registry.MxtBlocks;
import com.iafenvoy.mxt.registry.MxtDataComponents;
import com.iafenvoy.mxt.registry.MxtItems;
import com.iafenvoy.mxt.runtime.talisman.BrushPigmentService;
import com.iafenvoy.mxt.screen.menu.TalismanWorkstationMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NonNull;

import java.util.List;

/**
 * The drawing station's own entry point, which is the leg the port exists to unlock: {@code player.openMenu} is a
 * no-op on the {@code FakePlayer} the old command driven harness fell back to, so the menu the block opens could
 * never be reached from a test before.
 *
 * <p>Every part stands a real workstation in front of the probe's real player and opens it through the same
 * provider the block's own use calls, then checks what the menu holds, drives a click through it, or closes it and
 * checks the transient slot reaches the player.
 */
public final class StationProbes {
    private static final int PIGMENT = 1_000;

    private StationProbes() {
    }

    public static List<Probe> probes() {
        return List.of(
                Probe.of("talisman/station/open", StationProbes::opened),
                Probe.of("talisman/station/paper_slot", StationProbes::paperSlot),
                Probe.of("talisman/station/close", StationProbes::closed));
    }

    // What the station was opened with: the slot the paper went into, and where it was stood.
    private record Station(TalismanWorkstationMenu menu, BlockPos at) {
    }

    // Stands a real workstation in front of the caller and opens it, with a sheet of paper in its slot and a
    // loaded brush on the cursor: the pieces a client needs to drive a drawing through the real screen.
    private static Station openStation(ProbeContext context) {
        ServerPlayer player = context.player();
        ServerLevel level = context.level();
        BlockPos at = player.blockPosition().relative(player.getDirection().getOpposite());
        level.setBlockAndUpdate(at, MxtBlocks.TALISMAN_WORKSTATION.get().defaultBlockState());
        ItemStack brush = new ItemStack(MxtItems.TALISMAN_BRUSH.get());
        brush.set(MxtDataComponents.BRUSH_PIGMENT, PIGMENT);
        TalismanWorkstationMenu[] created = new TalismanWorkstationMenu[1];
        player.openMenu(new MenuProvider() {
            @Override
            public @NonNull Component getDisplayName() {
                return Component.translatable("block.mxt.talisman_workstation");
            }

            @Override
            public AbstractContainerMenu createMenu(int containerId, @NonNull Inventory inventory, @NonNull Player openedBy) {
                TalismanWorkstationMenu menu = new TalismanWorkstationMenu(containerId, inventory,
                        ContainerLevelAccess.create(level, at));
                created[0] = menu;
                return menu;
            }
        });
        context.check("openMenu opens the workstation menu the provider built",
                created[0] != null && player.containerMenu == created[0]);
        // The cursor is the menu's carried stack, so it can only be filled once the menu exists; the list follows
        // on its own from the next tick (the menu re-pushes whenever the cursor or the station slot changed).
        player.containerMenu.setCarried(brush);
        // The station stores nothing: the paper goes into the slot the menu owns, and closing hands it back.
        if (created[0] != null) created[0].paper().setItem(0, new ItemStack(MxtItems.BLANK_TALISMAN.get()));
        return new Station(created[0], at);
    }

    private static void opened(ProbeContext context) {
        Station station = openStation(context);
        TalismanWorkstationMenu menu = station.menu();
        if (menu == null) return;
        context.check("the carried brush holds the 1000 pigment the station was opened with",
                BrushPigmentService.pigment(context.player().containerMenu.getCarried()) == PIGMENT);
        context.check("the station slot holds the blank talisman the station was opened with",
                menu.paper().getItem(0).is(MxtItems.BLANK_TALISMAN.get()));
        context.check("the station menu owns one slot", menu.paper().getContainerSize() == 1);
        context.note("drawing_station: opened at " + station.at().toShortString()
                + " with one blank talisman and a brush holding 1000 pigment");
        context.note("drawing_station: carried=" + context.player().containerMenu.getCarried() + " slot="
                + menu.paper().getItem(0) + " brush=" + BrushPigmentService.pigment(menu.getCarried()));
    }

    private static void paperSlot(ProbeContext context) {
        Station station = openStation(context);
        TalismanWorkstationMenu menu = station.menu();
        if (menu == null) return;
        // A real click on the station's own slot, the way a client's click arrives. The brush on the cursor cannot go
        // into it - the slot only takes paper - so this one moves nothing at all.
        context.player().containerMenu.clicked(TalismanWorkstationMenu.PAPER_SLOT, 0, ContainerInput.PICKUP,
                context.player());
        context.check("the station slot refuses the brush on the cursor",
                menu.paper().getItem(0).is(MxtItems.BLANK_TALISMAN.get())
                        && BrushPigmentService.isBrush(context.player().containerMenu.getCarried()));
        // With an empty cursor the same click picks the sheet up, and the one after that puts it back.
        context.player().containerMenu.setCarried(ItemStack.EMPTY);
        context.player().containerMenu.clicked(TalismanWorkstationMenu.PAPER_SLOT, 0, ContainerInput.PICKUP,
                context.player());
        ItemStack carried = context.player().containerMenu.getCarried();
        context.check("clicking the station slot picks the blank talisman up",
                menu.paper().getItem(0).isEmpty() && carried.is(MxtItems.BLANK_TALISMAN.get())
                        && carried.getCount() == 1);
        context.player().containerMenu.clicked(TalismanWorkstationMenu.PAPER_SLOT, 0, ContainerInput.PICKUP,
                context.player());
        context.check("clicking the station slot again puts the blank talisman back",
                menu.paper().getItem(0).is(MxtItems.BLANK_TALISMAN.get())
                        && context.player().containerMenu.getCarried().isEmpty());
    }

    private static void closed(ProbeContext context) {
        Station station = openStation(context);
        TalismanWorkstationMenu menu = station.menu();
        if (menu == null) return;
        int before = carriedPapers(context);
        menu.paper().setItem(0, MxtItems.BLANK_TALISMAN.toStack(2));
        // Closing the screen is the crafting table's exit: the transient slot goes to the player.
        menu.removed(context.player());
        context.check("closing the station hands its own slot to the player",
                menu.paper().getItem(0).isEmpty() && carriedPapers(context) == before + 2);
    }

    /**
     * Blank talismans in the player's own inventory, which is where a handed back slot has to end up.
     */
    private static int carriedPapers(ProbeContext context) {
        Inventory bag = context.player().getInventory();
        int total = 0;
        for (int slot = 0; slot < bag.getContainerSize(); slot++) {
            ItemStack stack = bag.getItem(slot);
            if (stack.is(MxtItems.BLANK_TALISMAN.get())) total += stack.getCount();
        }
        return total;
    }
}
