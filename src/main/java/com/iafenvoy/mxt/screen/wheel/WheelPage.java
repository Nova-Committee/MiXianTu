package com.iafenvoy.mxt.screen.wheel;

import com.iafenvoy.mxt.api.WheelMenuEntry;
import com.iafenvoy.mxt.runtime.wheel.WheelSource;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * One page of the wheel: twelve cells that all read from the same source, padded so that indexing by sector is
 * always in range, plus how many of those cells actually exist.
 *
 * <p>Pages are numbered from zero in the order they appear, which is also how cells are numbered - page
 * {@code p} owns cells {@code p * 12} through {@code p * 12 + 11}. The first page is always the configured one;
 * a source that holds more than a page of entries simply takes another page, so the wheel grows instead of
 * dropping what does not fit.</p>
 *
 * @param source  where this page's entries come from
 * @param sectors the page's twelve cells, {@code null} for one that holds nothing
 * @param shown   how many of them are real: twelve on the configured page, whose empty cells are part of the
 *                layout the player arranged, and only as many as the source actually contributes on any other
 *                page, so a page with three skills is three cells rather than three and nine empty frames
 */
public record WheelPage(WheelSource source, List<@Nullable WheelMenuEntry> sectors, int shown) {
}
