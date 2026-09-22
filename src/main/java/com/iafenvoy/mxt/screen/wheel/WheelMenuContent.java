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
 * The wheel as the framework sees it: the registered provider's entries for every source, cut into pages of
 * twelve and numbered straight through them - the number a player's selection is stored as. Nothing is stored
 * here; the pages are read again every tick, so the numbering follows what the player carries.
 */
public final class WheelMenuContent {
    // Cells per page, taken from the layout that stores the configured page, so the two cannot disagree.
    public static final int SECTORS = WheelLayout.SLOTS;
    // What a number that addresses nothing resolves to.
    public static final int NONE = -1;
    private static WheelMenuProvider provider = (player, source) -> List.of();

    private WheelMenuContent() {
    }

    // The last registration wins; null resets to the empty provider.
    public static void register(@Nullable WheelMenuProvider content) {
        provider = content == null ? (player, source) -> List.of() : content;
    }

    // null is a value here - it is how an empty cell is spelled - so the lists are wrapped with
    // Collections.unmodifiableList: List.copyOf would reject them.
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

    public static List<@Nullable WheelMenuEntry> sectors(List<WheelPage> pages, int page) {
        return page >= 0 && page < pages.size() ? pages.get(page).sectors() : Collections.nCopies(SECTORS, null);
    }

    public static int shown(List<WheelPage> pages) {
        int total = 0;
        for (WheelPage page : pages) total += page.shown();
        return total;
    }

    public static @Nullable WheelMenuEntry entry(List<WheelPage> pages, int number) {
        if (number < 0) return null;
        int page = number / SECTORS;
        if (page >= pages.size()) return null;
        return pages.get(page).sectors().get(number % SECTORS);
    }

    public static WheelSource source(List<WheelPage> pages, int number) {
        int page = number / SECTORS;
        if (number < 0 || page >= pages.size()) return WheelSource.PAGES.getFirst();
        return pages.get(page).source();
    }

    public static boolean hasAnyEntry(List<WheelPage> pages) {
        for (WheelPage page : pages) for (WheelMenuEntry entry : page.sectors()) if (entry != null) return true;
        return false;
    }

    // A number that addresses nothing - its page is gone, or the page holds fewer cells than it used to -
    // resolves to the last cell that does hold something, so a stored place keeps working; the number stays.
    public static int effective(List<WheelPage> pages, int number) {
        if (number < 0) return NONE;
        if (!exists(pages, number)) return lastHeld(pages);
        return entry(pages, number) == null ? NONE : number;
    }

    // Whether the number addresses a cell that exists, which is not the same as one that holds anything.
    public static boolean exists(List<WheelPage> pages, int number) {
        if (number < 0) return false;
        int page = number / SECTORS;
        return page < pages.size() && number % SECTORS < pages.get(page).shown();
    }

    public static int lastHeld(List<WheelPage> pages) {
        for (int page = pages.size() - 1; page >= 0; page--) {
            WheelPage current = pages.get(page);
            for (int sector = Math.min(current.shown(), current.sectors().size()) - 1; sector >= 0; sector--)
                if (current.sectors().get(sector) != null) return page * SECTORS + sector;
        }
        return NONE;
    }
}
