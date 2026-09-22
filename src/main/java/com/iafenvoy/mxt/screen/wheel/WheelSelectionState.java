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
 * Which cell the player chose, and which page is up. Both survive the wheel closing, because the use key has a
 * target while no wheel is on screen and the HUD grid names the page the slot keys address.
 *
 * <p>The chosen <strong>number</strong> is what is stored, on both sides: cells are numbered across the whole
 * wheel, so a number is a place and nothing more. It is never rewritten to match what happens to be there - a
 * number whose page is gone keeps pointing past the end, and the page coming back puts the same entry under it
 * again. What that number stands for <em>now</em> is {@link WheelMenuContent#effective}, resolved fresh every
 * tick; the two are deliberately different values, and only the number is ever sent or saved.</p>
 *
 * <p>The page is a view: opening the wheel always moves it to the first page, and the switch keys walk it. The
 * pages themselves are resolved here once per client tick, because the HUD grid draws every cell of every page
 * every frame and resolving them walks the aura registry.</p>
 */
@EventBusSubscriber(Dist.CLIENT)
public final class WheelSelectionState {
    /** Nothing chosen in this session yet, which is also what "the use key has no target" means. */
    private static final int NONE = WheelMenuContent.NONE;
    private static int page;
    private static int number = NONE;
    private static List<WheelPage> pages = List.of();
    /** The cell {@link #number} stands for right now, resolved by {@link #refresh}. */
    private static int effective = NONE;

    private WheelSelectionState() {
    }

    /**
     * The chosen cell as stored, or {@code -1} when nothing has been chosen yet. This is the value that travels
     * and the value that is remembered; it is not necessarily a cell that holds anything.
     */
    public static int number() {
        return number;
    }

    /** The cell the choice stands for right now, or {@code -1} when there is nothing to spend. */
    public static int effective() {
        return effective;
    }

    /** Which page is up: what the ring draws and what the slot keys address. */
    public static int page() {
        return page;
    }

    /** Every page as of the last refresh, in numbering order; never modified. */
    public static List<WheelPage> pages() {
        return pages;
    }

    /** The source of the page that is up, which is what the page's own label names. */
    public static WheelSource pageSource() {
        return page < pages.size() ? pages.get(page).source() : WheelSource.PAGES.getFirst();
    }

    /** The source of the cell the choice stands for, which is what a trigger has to name. */
    public static WheelSource selectedSource() {
        return WheelMenuContent.source(pages, effective);
    }

    /** The entry the choice stands for, or {@code null} when there is nothing to spend. */
    public static @Nullable WheelMenuEntry selected() {
        return WheelMenuContent.entry(pages, effective);
    }

    /** The entry one number addresses right now, for a caller that has just picked a cell. */
    public static @Nullable WheelMenuEntry entry(int number) {
        return WheelMenuContent.entry(pages, number);
    }

    /** The number of one cell of the page that is up, which is what pointing and the slot keys write. */
    public static int numberAt(int sector) {
        return page * WheelMenuContent.SECTORS + sector;
    }

    /** Re-resolves the pages for a player; called once per client tick, before anything draws or acts on them. */
    public static void refresh(@Nullable Player player) {
        pages = WheelMenuContent.pages(player);
        // A page can go away under the player's feet (the item it read was put away), so the view is clamped
        // rather than trusted; the chosen number is left exactly as it was.
        page = Mth.clamp(page, 0, Math.max(0, pages.size() - 1));
        effective = WheelMenuContent.effective(pages, number);
    }

    /**
     * Remembers a cell as chosen. A number out of range is kept as it is: the pages it was counted on may come
     * back, and until then {@link #effective} answers with the last cell that does hold something.
     */
    public static void select(int number) {
        WheelSelectionState.number = number;
        effective = WheelMenuContent.effective(pages, number);
    }

    /**
     * Remembers one cell of the page that is up - pointing at it, or pressing its slot key.
     *
     * <p>A cell that page does not have is not a cell at all: the empty tail of a page read from what the player
     * carries has no frame to point at, so aiming there leaves the choice where it was rather than moving it to a
     * number that resolves to the "last skill" fallback. A cell that exists and is empty - which only the
     * configured page has - is still remembered as itself, because "pointing at nothing is choosing nothing" is
     * worth saying out loud.</p>
     */
    public static void selectSector(int sector) {
        if (sector < 0 || sector >= WheelGeometry.SECTORS)
            throw new IllegalArgumentException("Sector out of range: " + sector);
        int number = numberAt(sector);
        if (!WheelMenuContent.exists(pages, number)) return;
        select(number);
    }

    /** Moves to another page, wrapping at both ends; the chosen number is not touched. */
    public static void stepPage(int delta) {
        if (pages.isEmpty()) return;
        page = Math.floorMod(page + delta, pages.size());
    }

    /** Back to the first page, which is where the wheel key always opens. */
    public static void firstPage() {
        page = 0;
    }

    /**
     * Puts back a number the server remembered. The page is not moved with it: where the wheel opens is the
     * player's, and the HUD grid shows every page anyway, with the choice marked on whichever one holds it.
     */
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
