package com.iafenvoy.mxt.compat.ftb;

import com.iafenvoy.mxt.event.FriendEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * FTB Teams as a friend judgement source, kept behind a mod-list check so that neither this class nor the
 * event bus ever touches an FTB type when FTB Teams is not installed.
 *
 * <p>The split between this class and {@link FtbTeamsRelation} <em>is</em> the soft dependency: this one
 * declares nothing from FTB, so the bus can load it and register its listener unconditionally, and the class
 * that does reference FTB is reached only from inside the guard, where the JVM loads it on first use. If a
 * single FTB type ever appears in this file the whole server stops starting without FTB Teams — on every
 * instance that does not use it — which is why the extra file is worth it.</p>
 *
 * <p>FTB Teams answers for a player who is offline, because its team data belongs to the server rather than
 * to an entity. That is the half of the identification problem a per-entity friend list cannot cover, and
 * the reason {@link FriendEvent.Relation} carries a judge id next to the optional judge entity.</p>
 */
@EventBusSubscriber
public final class FtbTeamsCompat {
    private static final String FTB_TEAMS = "ftbteams";

    private FtbTeamsCompat() {
    }

    /**
     * Whether FTB Teams is installed.
     *
     * <p>Asked per judgement rather than cached: the mod list is fixed once the game has started, so this is
     * a plain map lookup with nothing that could go stale, and a cache would be one more thing to reason
     * about for no gain worth measuring next to the lookups the answer itself costs.</p>
     */
    public static boolean loaded() {
        return ModList.get().isLoaded(FTB_TEAMS);
    }

    /**
     * Offers FTB Teams' answer for a pair it knows about, and stays silent about every other pair.
     */
    @SubscribeEvent
    public static void onRelation(FriendEvent.Relation event) {
        if (loaded()) FtbTeamsRelation.judge(event);
    }
}
