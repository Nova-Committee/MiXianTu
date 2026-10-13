package com.iafenvoy.mxt.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.WritableBookContent;

/**
 * Reads what a book-like item carries into the chat log. A slip and a manual are read the same way, so the two items
 * share this instead of each writing its own page loop. The vanilla book page component is reused as-is: it is already
 * plain multi-line text, carries no tooltip, and packs write it with {@code mxt:merge_components}.
 */
public final class ItemReading {
    private ItemReading() {
    }

    // Whether there was anything to read; a caller with nothing else to say reports an empty book instead.
    public static boolean readInscription(ServerPlayer player, ItemStack stack) {
        WritableBookContent content = stack.get(DataComponents.WRITABLE_BOOK_CONTENT);
        if (content == null || content.pages().isEmpty()) return false;
        // One chat line per page: a book holds more than the action bar could ever show.
        for (String page : content.getPages(player.isTextFilteringEnabled()).toList())
            player.sendSystemMessage(Component.literal(page));
        return true;
    }
}
