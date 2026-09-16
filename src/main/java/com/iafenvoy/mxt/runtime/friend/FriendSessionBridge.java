package com.iafenvoy.mxt.runtime.friend;

import com.iafenvoy.mxt.attachment.FriendAttachment;
import com.iafenvoy.mxt.registry.MxtAttachments;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent;

/**
 * The two ends of a friend session: the session list is cleared at login, and {@link FriendCache} is refreshed
 * on the way in and on the way out. Clearing at login rather than at logout is deliberate, because a logout
 * hook can be missed by a crash or a killed JVM; the attachment is read without creating it.
 */
@EventBusSubscriber
public final class FriendSessionBridge {
    private FriendSessionBridge() {
    }

    @SubscribeEvent
    public static void onLogin(PlayerLoggedInEvent event) {
        // Fired once per connection, after the player's own data has been read, which is why the login
        // list is the one that was just loaded rather than the one from the session before it.
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        player.getExistingData(MxtAttachments.FRIEND).ifPresent(FriendAttachment::clearTemporary);
        // After the clear, so the mirror describes the session that is starting rather than the one that
        // just ended.
        FriendCache.refresh(player);
    }

    @SubscribeEvent
    public static void onLogout(PlayerLoggedOutEvent event) {
        // The attachment is still on the entity object at this point, and it is the last moment the lists
        // can be read from it: from here until the next login they are only reachable through the mirror.
        if (event.getEntity() instanceof ServerPlayer player) FriendCache.refresh(player);
    }
}
