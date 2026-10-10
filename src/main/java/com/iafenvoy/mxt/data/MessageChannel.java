package com.iafenvoy.mxt.data;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * Where a message an action sends goes. Shared by the four action kinds and the formation modules, so one datapack
 * value means the same thing everywhere.
 * <p>
 * Chat is the default at every use site: a message a pack never asked to put in the action bar would otherwise be
 * overwritten by the next one the game sends.
 */
public enum MessageChannel implements StringRepresentable {
    CHAT,
    ACTION_BAR;

    public static final Codec<MessageChannel> CODEC = StringRepresentable.fromEnum(MessageChannel::values);

    // The flag sendSystemMessage takes: true puts the line above the hotbar, false puts it in the chat log.
    public boolean actionBar() {
        return this == ACTION_BAR;
    }

    @Override
    public @NotNull String getSerializedName() {
        return this.name().toLowerCase(Locale.ROOT);
    }
}
