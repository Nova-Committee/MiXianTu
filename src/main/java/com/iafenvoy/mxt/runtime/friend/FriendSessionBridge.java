package com.iafenvoy.mxt.runtime.friend;

import com.iafenvoy.mxt.attachment.FriendAttachment;
import com.iafenvoy.mxt.registry.MxtAttachments;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent;

/**
 * The two ends of a friend session: the session list is cleared when its owner comes back, and the mirror
 * other players' judgements read is refreshed on the way in and on the way out.
 *
 * <p>The attachment saves both lists so that a death does not take the session one with it, which leaves
 * exactly one thing that has to end a session, and clearing at login is it. Doing that at login rather than
 * at logout is deliberate: a logout hook can be missed — a crash, a killed JVM, a player moved between
 * servers — and a list that survives because the server died is a list that outstays its welcome. A login
 * cannot be missed.</p>
 *
 * <p>The mirror is refreshed at both ends, and that pair is what lets {@link FriendCache} stay correct
 * without every writer of friend data having to remember it: the mirror is unused while its owner is
 * online, so its contents only have to be right for the offline stretch, and the logout refresh is the last
 * word before that stretch begins.</p>
 *
 * <p>Reads the attachment without creating it, so a player who has never used the friend system is not
 * handed an empty one for the privilege of joining. The mirror still records them, with an empty list —
 * "no friends" and "unknown player" are different answers.</p>
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
