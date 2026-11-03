package com.iafenvoy.mxt.screen.profiling;

import com.iafenvoy.saturn.profiling.ProfilerSource;
import com.iafenvoy.saturn.profiling.ServerProfiling;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The hover text of a row's source label: the name in full - the label itself is cut to the column - and the one
 * line saying what that bucket counts. A source the client was not built with falls back to its id and answers no
 * line, so a newer server cannot make the page show a raw key.
 * <p>
 * Client-agnostic on purpose: it touches no document and no screen, so a probe can read it where {@link ProfilerChart}
 * is read as well.
 */
public final class ProfilerSourceTips {
    private ProfilerSourceTips() {
    }

    public static List<Component> lines(ProfilerSource source) {
        List<Component> lines = new ArrayList<>(2);
        lines.add(Component.translatableWithFallback(source.translationKey(), source.id()));
        Component tip = Component.translatableWithFallback(source.tooltipKey(), "");
        if (!tip.getString().isBlank()) lines.add(tip.copy().withStyle(ChatFormatting.GRAY));
        return lines;
    }
}
