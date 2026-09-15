package com.iafenvoy.mxt.config;

import com.iafenvoy.jupiter.config.container.AutoInitConfigContainer;
import com.iafenvoy.jupiter.config.entry.BooleanEntry;
import com.iafenvoy.jupiter.config.entry.EnumEntry;
import com.iafenvoy.jupiter.config.entry.IntegerEntry;
import com.iafenvoy.mxt.MiXianTu;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Jupiter server configuration shared by MiXianTu gameplay integrations.
 */
public final class MxtServerConfig extends AutoInitConfigContainer {
    public static final MxtServerConfig INSTANCE = new MxtServerConfig();

    public final Curios curios = new Curios();
    public final Cultivation cultivation = new Cultivation();
    public final Aura aura = new Aura();
    public final Commands commands = new Commands();
    public final Formations formations = new Formations();
    public final Friends friends = new Friends();

    private MxtServerConfig() {
        super(Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "server"), "config.mxt.server", "./config/mxt-server.json");
    }

    public static BackMode backMode() {
        return INSTANCE.curios.backMode.getValue();
    }

    public static BeltMode beltMode() {
        return INSTANCE.curios.beltMode.getValue();
    }

    public static boolean forceRenderSlots() {
        return INSTANCE.curios.forceRenderSlots.getValue();
    }

    public static boolean forbidCultivationWithoutEligibleAura() {
        return INSTANCE.cultivation.forbidWithoutEligibleAura.getValue();
    }

    public static boolean allowCultivationMovement() {
        return INSTANCE.cultivation.allowMovement.getValue();
    }

    public static int techniqueLearnCooldown() {
        return INSTANCE.cultivation.techniqueLearnCooldown.getValue();
    }

    public static int blockAuraTickInterval() {
        return INSTANCE.aura.blockAuraTickInterval.getValue();
    }

    public static int auraSyncInterval() {
        return INSTANCE.aura.auraSyncInterval.getValue();
    }

    public static boolean auraQueryStats() {
        return INSTANCE.aura.queryStats.getValue();
    }

    public static int auraEntityRefreshInterval() {
        return INSTANCE.aura.entityRefreshInterval.getValue();
    }

    /**
     * What an empty allow list on a formation plate means.
     *
     * <p>Defaults to true because that is what every plate written before the allow list existed relies
     * on: a plate with no restriction can bind any formation. Turning it off makes an unrestricted plate
     * impossible, so every plate has to name what it may run.</p>
     */
    public static boolean emptyPlateAllowsAll() {
        return INSTANCE.formations.emptyAllowsAll.getValue();
    }

    /**
     * Whether the ambient aura of the ground a formation stands on also pays its upkeep.
     *
     * <p>Off by default, because it makes a formation cheaper to run than what it stands on: the block
     * emitters inside it are already supplying it, and this additionally spends the natural aura of the
     * area. Turned on, a formation over rich ground is cheap or free even with no emitters and no owner
     * resources, which is a different balance from the one the mechanic ships with.</p>
     */
    public static boolean formationDrawsEnvironment() {
        return INSTANCE.formations.drawsEnvironment.getValue();
    }

    /**
     * Whether a hostile formation spares whoever its owner counts as a friend.
     *
     * <p>On by default, and it changes nothing on its own: a formation only becomes hostile by declaring
     * {@code hostile}, which is new and therefore absent from every existing definition. Turning it off is
     * for a server that would rather formations hit everybody than have one player's friend list decide
     * who a fortification does and does not shoot at.</p>
     */
    public static boolean formationRespectsFriends() {
        return INSTANCE.formations.respectFriends.getValue();
    }

    /**
     * Whether handing a formation's protection to claims requires claim protection to actually be active.
     *
     * <p>On by default, and it decides what {@code delegate_to_claims} means when there is nothing to
     * delegate to. On: the delegation is only honoured while a claim plugin is installed <em>and</em> its
     * protection is switched on, and otherwise the formation's own flags stay in force — a ward that
     * silently protects nothing is the one failure worth a server option. Off: the literal reading, where
     * the formation hands its protection over regardless and therefore protects nothing when no claim
     * protection exists.</p>
     *
     * <p>Like {@link #formationRespectsFriends()}, this changes nothing on its own: it only affects
     * definitions that declare the switch.</p>
     */
    public static boolean formationDelegateRequiresClaims() {
        return INSTANCE.formations.delegateRequiresClaims.getValue();
    }

    /**
     * How a protection formation and the server's claim plugin relate to each other.
     *
     * <p>Claim protection is decided per team and per chunk, while a formation protects a sphere of its own,
     * so a server that runs both has to say which of the two is in charge. {@link ClaimLinkage#NONE} leaves
     * them independent — both apply, and a position is refused if either refuses it — and is the default
     * because it changes nothing for a server without a claim plugin. The other two make the claim the
     * unit of jurisdiction: one refuses to let a ward stand outside one, the other lets the claim's own
     * rules take over inside it.</p>
     */
    public static ClaimLinkage formationClaimLinkage() {
        return INSTANCE.formations.claimLinkage.getValue();
    }

    /**
     * Whether raising a protection formation on somebody else's land needs their leave.
     *
     * <p>On by default, and independent of {@link #formationClaimLinkage()}: that option says how the two
     * systems share a position, this one says whose land a ward may stand on at all. A ward is a claim of
     * jurisdiction, not merely a build, so being allowed to place a block somewhere is not the same as being
     * allowed to legislate there — and a structure that is already standing (a template matching the
     * terrain, or one somebody else built) would otherwise let anyone raise one anywhere.</p>
     *
     * <p>Three ways through, and all of them are the landowner's own answer rather than a second rule
     * invented here: the claim plugin's own edit permission for that position, a team with no player owner
     * to ask (an unclaimed position, or a console-owned team), and being recognised as a friend of the
     * team's owner by the friend system — which is where FTB membership and alliances already arrive from
     * {@code FtbTeamsRelation}.</p>
     */
    public static boolean formationWardsNeedClaimPermission() {
        return INSTANCE.formations.wardsNeedClaimPermission.getValue();
    }

    /**
     * Whether FTB Teams' {@code ALLY} rank counts as a friend.
     *
     * <p>On by default, because that rank <em>is</em> FTB Teams' own "this outsider is with us" marker. Off
     * leaves only real team members as friends, which is the right reading for a server that uses allies
     * for something else entirely.</p>
     */
    public static boolean ftbTeamsAllyCounts() {
        return INSTANCE.friends.ftbTeamsAlly.getValue();
    }

    /**
     * Whether FTB Teams' {@code INVITED} rank counts as a friend.
     *
     * <p>Off by default, and worth understanding before turning on: {@code INVITED} is what a team returns
     * for a player it has invited and who has not accepted yet, <em>and</em> what
     * {@code Team#getRankForPlayer} returns for anybody at all while the team is free-to-join. On such a
     * team this option would make every stranger on the server a friend.</p>
     */
    public static boolean ftbTeamsInvitedCounts() {
        return INSTANCE.friends.ftbTeamsInvited.getValue();
    }

    public static final class Curios extends AutoInitConfigCategoryBase {
        public final EnumEntry<BackMode> backMode = EnumEntry.builder("config.mxt.server.curios.back_mode", BackMode.MANUAL).nameProvider(value -> Component.translatable("config.mxt.server.mode." + value.name().toLowerCase())).build();
        public final EnumEntry<BeltMode> beltMode = EnumEntry.builder("config.mxt.server.curios.belt_mode", BeltMode.MANUAL).nameProvider(value -> Component.translatable("config.mxt.server.mode." + value.name().toLowerCase())).build();
        public final BooleanEntry forceRenderSlots = BooleanEntry.builder("config.mxt.server.curios.force_render_slots", false).build();

        private Curios() {
            super("curios", "config.mxt.server.curios");
        }
    }

    public static final class Cultivation extends AutoInitConfigCategoryBase {
        public final BooleanEntry forbidWithoutEligibleAura = BooleanEntry.builder("config.mxt.server.cultivation.forbid_without_eligible_aura", false).build();
        public final BooleanEntry allowMovement = BooleanEntry.builder("config.mxt.server.cultivation.allow_movement", false).build();
        public final IntegerEntry techniqueLearnCooldown = IntegerEntry.builder("config.mxt.server.cultivation.technique_learn_cooldown", 60).range(0, 72_000).build();

        private Cultivation() {
            super("cultivation", "config.mxt.server.cultivation");
        }
    }

    public static final class Aura extends AutoInitConfigCategoryBase {
        public final IntegerEntry blockAuraTickInterval = IntegerEntry.builder("config.mxt.server.aura.block_aura_tick_interval", 10).range(1, 1200).build();
        public final IntegerEntry auraSyncInterval = IntegerEntry.builder("config.mxt.server.aura.sync_interval", 5).range(1, 1200).build();
        public final BooleanEntry queryStats = BooleanEntry.builder("config.mxt.server.aura.query_stats", false).build();
        public final IntegerEntry entityRefreshInterval = IntegerEntry.builder("config.mxt.server.aura.entity_refresh_interval", 10).range(1, 1200).build();

        private Aura() {
            super("aura", "config.mxt.server.aura");
        }
    }

    public static final class Commands extends AutoInitConfigCategoryBase {
        public final BooleanEntry ability = BooleanEntry.builder("config.mxt.server.commands.ability", true).build();
        public final BooleanEntry aura = BooleanEntry.builder("config.mxt.server.commands.aura", true).build();
        public final BooleanEntry display = BooleanEntry.builder("config.mxt.server.commands.display", true).build();
        public final BooleanEntry formation = BooleanEntry.builder("config.mxt.server.commands.formation", true).build();
        public final BooleanEntry friend = BooleanEntry.builder("config.mxt.server.commands.friend", true).build();
        public final BooleanEntry technique = BooleanEntry.builder("config.mxt.server.commands.technique", true).build();
        public final BooleanEntry trade = BooleanEntry.builder("config.mxt.server.commands.trade", true).build();

        private Commands() {
            super("commands", "config.mxt.server.commands");
        }
    }

    public static final class Formations extends AutoInitConfigCategoryBase {
        public final BooleanEntry emptyAllowsAll = BooleanEntry.builder("config.mxt.server.formation.empty_plate_allows_all", true).build();
        public final BooleanEntry drawsEnvironment = BooleanEntry.builder("config.mxt.server.formation.draws_environment", false).build();
        public final BooleanEntry respectFriends = BooleanEntry.builder("config.mxt.server.formation.respect_friends", true).build();
        public final BooleanEntry delegateRequiresClaims = BooleanEntry.builder("config.mxt.server.formation.delegate_requires_claims", true).build();
        public final EnumEntry<ClaimLinkage> claimLinkage = EnumEntry.builder("config.mxt.server.formation.claim_linkage", ClaimLinkage.NONE)
                .nameProvider(value -> Component.translatable("config.mxt.server.formation.claim_linkage." + value.name().toLowerCase())).build();
        public final BooleanEntry wardsNeedClaimPermission = BooleanEntry.builder("config.mxt.server.formation.wards_need_claim_permission", true).build();

        private Formations() {
            super("formation", "config.mxt.server.formation");
        }
    }

    /**
     * What counts as a friend, per source that can answer for one.
     */
    public static final class Friends extends AutoInitConfigCategoryBase {
        public final BooleanEntry ftbTeamsAlly = BooleanEntry.builder("config.mxt.server.friends.ftb_teams_ally", true).build();
        public final BooleanEntry ftbTeamsInvited = BooleanEntry.builder("config.mxt.server.friends.ftb_teams_invited", false).build();

        private Friends() {
            super("friends", "config.mxt.server.friends");
        }
    }

    public enum BackMode {
        MANUAL, WEAPONS, ALL
    }

    /**
     * Which of a protection formation and a claim plugin is in charge where they overlap.
     */
    public enum ClaimLinkage {
        /// A protection formation may only be raised with its centre inside claimed land.
        CLAIMS_ONLY,
        /// A protection formation may be raised anywhere; one standing inside claimed land is governed by the
        /// claim's rules instead of its own.
        CLAIMS_PRECEDENCE,
        /// The two are independent: the formation protects by its own rules wherever it stands.
        NONE
    }

    public enum BeltMode {
        MANUAL, WEAPONS_ARTIFACTS, ALL
    }
}
