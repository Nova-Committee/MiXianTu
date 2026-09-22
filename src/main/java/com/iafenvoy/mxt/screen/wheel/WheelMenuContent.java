package com.iafenvoy.mxt.screen.wheel;

import com.iafenvoy.mxt.api.WheelMenuEntry;
import com.iafenvoy.mxt.runtime.wheel.WheelLayout;
import com.iafenvoy.mxt.runtime.wheel.WheelSource;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The wheel as the framework sees it: the registered {@link WheelMenuProvider}'s entries for every source, cut
 * into pages of twelve. Cells are numbered straight through those pages - the configured page is {@code 0..11}
 * and what the player carries follows from {@code 12} on - which is the number the player's selection is stored
 * as and the number the HUD grid draws.
 *
 * <p>A source contributes as many pages as it currently fills and no page at all while it holds nothing, so the
 * numbering follows what exists: put an item away and the pages behind it move down, take it out again and they
 * move back. Nothing here is stored - the pages are read again every tick, which is what makes them follow the
 * gear.</p>
 *
 * <p>Only the configured page keeps its empty cells. That page's twelve cells are the layout the player
 * arranged, so an empty one is something to see and fill; every other page holds exactly what its source
 * contributes, which is what {@link WheelPage#shown()} counts.</p>
 */
public final class WheelMenuContent {
    /** Cells per page: taken from the layout that stores the configured page, so the two cannot disagree. */
    public static final int SECTORS = WheelLayout.SLOTS;
    /** No cell: what a number that addresses nothing resolves to. */
    public static final int NONE = -1;
    private static WheelMenuProvider provider = (player, source) -> List.of();

    private WheelMenuContent() {
    }

    /** The last registration wins; {@code null} resets to the empty provider. */
    public static void register(@Nullable WheelMenuProvider content) {
        provider = content == null ? (player, source) -> List.of() : content;
    }

    /**
     * Every page in numbering order. Each source gets one page per twelve entries it holds, and none while it
     * holds none; the configured source always gets its one page, since its twelve cells exist whether or not
     * anything sits in them.
     *
     * <p>{@code null} is a value here - it is how an empty cell is spelled - so the pages are wrapped with
     * {@link Collections#unmodifiableList} rather than {@code List.copyOf}, which rejects nulls.</p>
     */
    public static List<WheelPage> pages(@Nullable Player player) {
        List<WheelPage> pages = new ArrayList<>();
        for (WheelSource source : WheelSource.PAGES) {
            List<WheelMenuEntry> entries = provider.entries(player, source);
            int count = Math.max(source.configured() ? 1 : 0, (entries.size() + SECTORS - 1) / SECTORS);
            for (int page = 0; page < count; page++) {
                int shown = source.configured() ? SECTORS : Math.min(SECTORS, entries.size() - page * SECTORS);
                List<WheelMenuEntry> sectors = new ArrayList<>(SECTORS);
                for (int sector = 0; sector < SECTORS; sector++) {
                    int entry = page * SECTORS + sector;
                    sectors.add(entry < entries.size() ? entries.get(entry) : null);
                }
                pages.add(new WheelPage(source, Collections.unmodifiableList(sectors), shown));
            }
        }
        return Collections.unmodifiableList(pages);
    }

    /** One page's twelve cells, or twelve empty ones when there is no such page. */
    public static List<@Nullable WheelMenuEntry> sectors(List<WheelPage> pages, int page) {
        return page >= 0 && page < pages.size() ? pages.get(page).sectors() : Collections.nCopies(SECTORS, null);
    }

    /** How many cells the whole wheel actually holds, which is what the HUD grid lays out. */
    public static int shown(List<WheelPage> pages) {
        int total = 0;
        for (WheelPage page : pages) total += page.shown();
        return total;
    }

    /** The cell one number addresses, or {@code null} when nothing is there or the number is past the end. */
    public static @Nullable WheelMenuEntry entry(List<WheelPage> pages, int number) {
        if (number < 0) return null;
        int page = number / SECTORS;
        if (page >= pages.size()) return null;
        return pages.get(page).sectors().get(number % SECTORS);
    }

    /** Which source one number reads from; the first source when the number addresses nothing. */
    public static WheelSource source(List<WheelPage> pages, int number) {
        int page = number / SECTORS;
        if (number < 0 || page >= pages.size()) return WheelSource.PAGES.getFirst();
        return pages.get(page).source();
    }

    /** Whether any cell of any page holds anything; the wheel key opens only then. */
    public static boolean hasAnyEntry(List<WheelPage> pages) {
        for (WheelPage page : pages) for (WheelMenuEntry entry : page.sectors()) if (entry != null) return true;
        return false;
    }

    /**
     * The cell a stored number stands for right now.
     *
     * <p>A number that still addresses a cell which exists and holds something is itself - pointing at an empty
     * cell of the configured page is pointing at nothing, and stays silent. A number that addresses nothing is
     * not: either its page is gone (the item was put away) or the page holds fewer cells than it used to, and
     * what the player gets in both cases is the last cell that does hold something, so a stored place keeps
     * working instead of dropping the selection. The number itself is never rewritten, so the same place is
     * found again the moment the cells come back.</p>
     */
    public static int effective(List<WheelPage> pages, int number) {
        if (number < 0) return NONE;
        if (!exists(pages, number)) return lastHeld(pages);
        return entry(pages, number) == null ? NONE : number;
    }

    /** Whether this number addresses a cell that exists, which is not the same as one that holds anything. */
    public static boolean exists(List<WheelPage> pages, int number) {
        if (number < 0) return false;
        int page = number / SECTORS;
        return page < pages.size() && number % SECTORS < pages.get(page).shown();
    }

    /** The highest-numbered cell that holds anything, or {@link #NONE} when the wheel is empty. */
    public static int lastHeld(List<WheelPage> pages) {
        for (int page = pages.size() - 1; page >= 0; page--) {
            WheelPage current = pages.get(page);
            for (int sector = Math.min(current.shown(), current.sectors().size()) - 1; sector >= 0; sector--)
                if (current.sectors().get(sector) != null) return page * SECTORS + sector;
        }
        return NONE;
    }
}
