package com.iafenvoy.mxt.compat.ftb;

import com.iafenvoy.mxt.event.FriendEvent.Relation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * FTB Teams as a friend judgement source, kept behind a mod-list check so the bus can load this class
 * without FTB installed; every FTB type lives in {@link FtbTeamsRelation}. The team data is the server's
 * rather than an entity's, so FTB answers for an offline player.
 */
@EventBusSubscriber
public final class FtbTeamsCompat {
    private static final String FTB_TEAMS = "ftbteams";

    private FtbTeamsCompat() {
    }

    /**
     * Not cached: the mod list is fixed once the game has started, so this is a plain map lookup.
     */
    public static boolean loaded() {
        return ModList.get().isLoaded(FTB_TEAMS);
    }

    @SubscribeEvent
    public static void onRelation(Relation event) {
        if (loaded()) FtbTeamsRelation.judge(event);
    }
}
