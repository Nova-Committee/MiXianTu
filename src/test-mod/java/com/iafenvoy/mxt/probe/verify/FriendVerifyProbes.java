package com.iafenvoy.mxt.probe.verify;

import com.iafenvoy.mxt.attachment.FriendAttachment;
import com.iafenvoy.mxt.probe.Probe;
import com.iafenvoy.mxt.probe.ProbeContext;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.runtime.friend.FriendCache;
import com.iafenvoy.mxt.runtime.friend.FriendSessionBridge;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.util.TriState;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.util.List;

/**
 * The friend mirror is memory of one server session, not stored data. What it has to answer is "is this one of mine",
 * and for an owner nobody has logged in for yet the honest answer is "unknown" - so a stopped session drops it and the
 * next session does not inherit the friendship of a world it never loaded.
 */
public final class FriendVerifyProbes {
    private FriendVerifyProbes() {
    }

    public static List<Probe> probes() {
        return List.of(Probe.of("verify/friend_session_scope", FriendVerifyProbes::mirrorDoesNotOutliveTheSession));
    }

    private static void mirrorDoesNotOutliveTheSession(ProbeContext context) {
        ServerPlayer owner = context.player();
        // A second body, only needed as an id to be on the list; it stays in the level so the id is a real one.
        ServerPlayer friend = context.joinBeside(owner, 2.0D, 0.0D);
        FriendAttachment attachment = owner.getData(MxtAttachments.FRIEND);
        attachment.addPermanent(new NameAndId(friend.getUUID(), friend.getScoreboardName()));
        FriendCache.refresh(owner);
        context.check("a refreshed owner answers from the mirror",
                FriendCache.lookup(owner.getUUID(), friend.getUUID()) == TriState.TRUE);
        // Stopping the server is what ends the session the mirror describes, and it is run through the real handler so
        // the clearing is the bridge's own and not a call the probe makes on the cache's behalf.
        FriendSessionBridge.onServerStopped(new ServerStoppedEvent(context.server()));
        context.check("a stopped session keeps no list at all", !mirrored(owner));
        // The same owner, in the next session, before any login: unknown rather than the list from the world before.
        context.check("the next session does not inherit the old list",
                FriendCache.lookup(owner.getUUID(), friend.getUUID()) == TriState.DEFAULT);
        // And once the owner is really seen again, the answer is the list of this world.
        FriendCache.refresh(owner);
        context.check("a login in the new session answers again",
                FriendCache.lookup(owner.getUUID(), friend.getUUID()) == TriState.TRUE);
        // Leaves the process-wide mirror as the run found it, since every probe in the batch shares one.
        FriendCache.clear();
    }

    // An owner the mirror holds answers TRUE or FALSE; one it does not hold at all answers DEFAULT, which is what a
    // dropped session has to look like from the outside.
    private static boolean mirrored(ServerPlayer owner) {
        return FriendCache.lookup(owner.getUUID(), owner.getUUID()) != TriState.DEFAULT;
    }
}
