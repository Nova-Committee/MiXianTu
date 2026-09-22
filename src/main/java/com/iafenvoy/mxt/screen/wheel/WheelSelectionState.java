package com.iafenvoy.mxt.screen.wheel;

import com.iafenvoy.mxt.api.WheelMenuEntry;
import com.iafenvoy.mxt.runtime.wheel.WheelSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Which cell the player chose, and which page is up; both survive the wheel closing, because the use key has a
 * target while no wheel is on screen. The chosen <em>number</em> is what is stored on both sides - a place in
 * the wheel's numbering, never rewritten - and what it stands for now is resolved fresh every tick.
 */
@EventBusSubscriber(Dist.CLIENT)
public final class WheelSelectionState {
    // Nothing chosen in this session yet, which is also what "the use key has no target" means.
    private static final int NONE = WheelMenuContent.NONE;
    private static int page;
    private static int number = NONE;
    private static List<WheelPage> pages = List.of();
    // The cell number stands for right now, resolved by refresh(); never the same value as number.
    private static int effective = NONE;

    private WheelSelectionState() {
    }

    // The chosen cell as stored, or -1 when nothing has been chosen yet. This is the value that travels and the
    // value that is remembered; it is not necessarily a cell that holds anything.
    public static int number() {
        return number;
    }

    public static int effective() {
        return effective;
    }

    public static int page() {
        return page;
    }

    public static List<WheelPage> pages() {
        return pages;
    }

    public static WheelSource pageSource() {
        return page < pages.size() ? pages.get(page).source() : WheelSource.PAGES.getFirst();
    }

    public static WheelSource selectedSource() {
        return WheelMenuContent.source(pages, effective);
    }

    public static @Nullable WheelMenuEntry selected() {
        return WheelMenuContent.entry(pages, effective);
    }

    public static @Nullable WheelMenuEntry entry(int number) {
        return WheelMenuContent.entry(pages, number);
    }

    public static int numberAt(int sector) {
        return page * WheelMenuContent.SECTORS + sector;
    }

    // Called once per client tick, before anything draws or acts on the pages. A page can go away under the
    // player's feet (the item it read was put away), so only the view is clamped - the chosen number is kept.
    public static void refresh(@Nullable Player player) {
        pages = WheelMenuContent.pages(player);
        page = Mth.clamp(page, 0, Math.max(0, pages.size() - 1));
        effective = WheelMenuContent.effective(pages, number);
    }

    // A number out of range is kept as it is: the pages it was counted on may come back, and until then
    // effective() answers with the last cell that does hold something.
    public static void select(int number) {
        WheelSelectionState.number = number;
        effective = WheelMenuContent.effective(pages, number);
    }

    // A cell the page does not have is not a cell at all - the empty tail of a gear page has no frame to point
    // at - so aiming there leaves the choice where it was; an existing but empty cell is still remembered.
    public static void selectSector(int sector) {
        if (sector < 0 || sector >= WheelGeometry.SECTORS)
            throw new IllegalArgumentException("Sector out of range: " + sector);
        int number = numberAt(sector);
        if (!WheelMenuContent.exists(pages, number)) return;
        select(number);
    }

    public static void stepPage(int delta) {
        if (pages.isEmpty()) return;
        page = Math.floorMod(page + delta, pages.size());
    }

    public static void firstPage() {
        page = 0;
    }

    // Only the number: where the wheel opens is the player's, and the HUD grid marks the choice on whichever
    // page holds it.
    public static void restore(int number) {
        WheelSelectionState.number = number;
    }

    @SubscribeEvent
    public static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        page = 0;
        number = NONE;
        pages = List.of();
        effective = NONE;
    }
}
