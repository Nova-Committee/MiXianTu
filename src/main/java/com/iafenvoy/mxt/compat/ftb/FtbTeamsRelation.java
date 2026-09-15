package com.iafenvoy.mxt.compat.ftb;

import com.iafenvoy.mxt.config.MxtServerConfig;
import com.iafenvoy.mxt.event.FriendEvent;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.api.TeamRank;
import net.minecraft.util.TriState;

import java.util.UUID;

/**
 * The FTB Teams half of the friend judgement: a player's team members and their allies count as their own.
 *
 * <p><b>An ally is a rank, not an allied team.</b> FTB Teams has no relationship between teams; adding an
 * ally means giving an outside player the {@link TeamRank#ALLY} rank inside your own team, and that rank is
 * stored in the same per-team map as the members. There is therefore no {@code getAllies()} to call: the
 * answer is read off {@link Team#getRankForPlayer(UUID)}.</p>
 *
 * <p>The predicate starts from FTB Chunks' own reading of "friendly", copied rather than invented:
 * member-or-better, or exactly {@code ALLY} — the latter two widenable by the server (see
 * {@code config.mxt.server.friends}). It deliberately does <em>not</em> use
 * {@link TeamRank#isAllyOrBetter()}, which is the tempting one-liner and is wrong here for two reasons —
 * it counts {@code INVITED}, which sits above {@code ALLY} in the ordering, and
 * {@link Team#getRankForPlayer(UUID)} returns {@code INVITED} for <em>anybody</em> when the team is
 * free-to-join. Using it would turn every stranger on such a server into a friend, which is why counting
 * {@code INVITED} is its own option, off by default.</p>
 *
 * <p>Only ever claims {@code TRUE}. FTB Teams knows who its members and allies are; it does not know who a
 * player's friends are, and an explicit {@code FALSE} would override the player's own list rather than add
 * to it. Silence means "not by my rules", which leaves the question to the other sources.</p>
 *
 * <p>Reads straight from the team manager on every call: the data is already resident (teams are loaded at
 * server start and keyed by UUID), so the lookup is a couple of map reads. A cache would buy nothing and
 * would need invalidating on every join, leave, promotion and ally change.</p>
 */
final class FtbTeamsRelation {
    private FtbTeamsRelation() {
    }

    /**
     * Answers one judgement, if FTB Teams has anything to say about the pair.
     *
     * <p>Called only with FTB Teams present. The API instance itself is initialised while mods are being
     * constructed, and a friend judgement only happens once a server is running, so the mod-list check in
     * {@link FtbTeamsCompat} is the whole of what this method needs to assume.</p>
     */
    static void judge(FriendEvent.Relation event) {
        // The manager does not exist until the server is about to start; before that there is nothing to
        // read and no opinion to offer.
        if (!FTBTeamsAPI.api().isManagerLoaded()) return;
        // An id FTB Teams has never seen — a player who never joined — has no team, and neither does a
        // formation whose owner was never recorded.
        Team team = FTBTeamsAPI.api().getManager().getTeamForPlayerID(event.judgeId()).orElse(null);
        if (team != null && friendly(team, event.candidate().getUUID())) event.setResult(TriState.TRUE);
    }

    /**
     * Whether the team counts this player as its own: a member of it, or a rank the server counts as an
     * alliance.
     *
     * <p>Membership is not configurable — being on the team <em>is</em> being on the team — while the two
     * outsider ranks are, because which of FTB Teams' tiers a pack means by "with us" is a server's call.
     * The predicate starts from FTB Chunks' own reading, member-or-better or exactly {@code ALLY}, and only
     * widens from there.</p>
     */
    private static boolean friendly(Team team, UUID candidate) {
        TeamRank rank = team.getRankForPlayer(candidate);
        return rank.isMemberOrBetter()
                || (MxtServerConfig.ftbTeamsAllyCounts() && rank == TeamRank.ALLY)
                || (MxtServerConfig.ftbTeamsInvitedCounts() && rank == TeamRank.INVITED);
    }
}
