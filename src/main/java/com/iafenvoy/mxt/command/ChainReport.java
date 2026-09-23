package com.iafenvoy.mxt.command;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.List;

// The shared shape of a chain report: the two diagnostic `chain` subcommands must colour a ladder the same way.
public final class ChainReport {
    private ChainReport() {
    }

    // A current index of -1 leaves every entry white, which is what a chain that does not contain the named entry
    // looks like; callers report that case themselves instead of drawing it here.
    public static Component line(List<? extends Component> entries, int current) {
        MutableComponent text = Component.empty();
        for (int index = 0; index < entries.size(); index++) {
            if (index > 0) text.append(Component.literal(" → ").withStyle(ChatFormatting.DARK_GRAY));
            text.append(entries.get(index).copy().withStyle(index == current ? ChatFormatting.GREEN
                    : index < current ? ChatFormatting.GRAY : ChatFormatting.WHITE));
        }
        return text;
    }
}
