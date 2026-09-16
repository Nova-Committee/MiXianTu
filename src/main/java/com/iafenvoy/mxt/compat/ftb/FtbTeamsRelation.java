package com.iafenvoy.mxt.compat.ftb;

import com.iafenvoy.mxt.config.MxtServerConfig;
import com.iafenvoy.mxt.event.FriendEvent.Relation;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.api.TeamRank;
import net.minecraft.util.TriState;

import java.util.UUID;

/**
 * The FTB Teams half of the friend judgement: a player's team members and their allies count as their own.
 * An ally is an {@link TeamRank#ALLY} rank in the same per-team map as the members, not an allied team. The
 * predicate is member-or-better or exactly {@code ALLY}, never {@link TeamRank#isAllyOrBetter()}, which
 * counts {@code INVITED}.
 */
final class FtbTeamsRelation {
    private FtbTeamsRelation() {
    }

    /**
     * Only possible while the server is running, so FTB Teams is present.
     */
    static void judge(Relation event) {
        // The manager does not exist until the server is about to start.
        if (!FTBTeamsAPI.api().isManagerLoaded()) return;
        // An id FTB Teams has never seen has no team, and neither has a formation with no recorded owner.
        Team team = FTBTeamsAPI.api().getManager().getTeamForPlayerID(event.judgeId()).orElse(null);
        if (team != null && friendly(team, event.candidate().getUUID())) event.setResult(TriState.TRUE);
    }

    /**
     * Membership is not configurable, while the two outsider ranks are, because which of FTB Teams' tiers
     * a pack means by "with us" is a server's call.
     */
    private static boolean friendly(Team team, UUID candidate) {
        TeamRank rank = team.getRankForPlayer(candidate);
        return rank.isMemberOrBetter()
                || (MxtServerConfig.INSTANCE.compat.ftbTeamsAlly.getValue() && rank == TeamRank.ALLY)
                || (MxtServerConfig.INSTANCE.compat.ftbTeamsInvited.getValue() && rank == TeamRank.INVITED);
    }
}
