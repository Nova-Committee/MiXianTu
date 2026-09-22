package com.iafenvoy.mxt.screen.wheel;

import com.iafenvoy.mxt.api.WheelMenuEntry;
import com.iafenvoy.mxt.runtime.wheel.WheelSource;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * One page of the wheel: twelve cells reading from one source, padded so indexing by sector is always in range.
 * Page {@code p} owns cells {@code p * 12}..{@code p * 12 + 11}; {@code shown} is twelve on the configured page
 * (its empty cells are part of the arranged layout) and only what the source contributes on any other page.
 */
public record WheelPage(WheelSource source, List<@Nullable WheelMenuEntry> sectors, int shown) {
}
