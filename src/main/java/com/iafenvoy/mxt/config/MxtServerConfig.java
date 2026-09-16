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
     * What an empty allow list on a formation plate means. Defaults to true, which is what every plate
     * written before the allow list existed relies on; off means every plate must name what it may run.
     */
    public static boolean emptyPlateAllowsAll() {
        return INSTANCE.formations.emptyAllowsAll.getValue();
    }

    /**
     * Whether the ambient aura of the ground a formation stands on also pays its upkeep. Off by default,
     * which makes a formation over rich ground cheap or free even with no emitters and no owner resources.
     */
    public static boolean formationDrawsEnvironment() {
        return INSTANCE.formations.drawsEnvironment.getValue();
    }

    /**
     * Whether a hostile formation spares whoever its owner counts as a friend. On by default, and it
     * changes nothing on its own: a formation only becomes hostile by declaring {@code hostile}.
     */
    public static boolean formationRespectsFriends() {
        return INSTANCE.formations.respectFriends.getValue();
    }

    /**
     * Whether handing a formation's protection to claims requires claim protection to actually be active.
     * On by default, so the delegation is honoured only while a claim plugin has its protection switched on
     * and the formation's own flags stay in force otherwise. Like {@link #formationRespectsFriends()}, it only
     * affects definitions that declare the switch.
     */
    public static boolean formationDelegateRequiresClaims() {
        return INSTANCE.formations.delegateRequiresClaims.getValue();
    }

    /**
     * How a protection formation and the server's claim plugin relate to each other. Defaults to
     * {@link ClaimLinkage#NONE}, which leaves them independent: both apply, and either may refuse a position.
     */
    public static ClaimLinkage formationClaimLinkage() {
        return INSTANCE.formations.claimLinkage.getValue();
    }

    /**
     * Whether raising a protection formation on somebody else's land needs their leave. On by default and
     * independent of {@link #formationClaimLinkage()}; three ways through, all of them the landowner's own
     * answer: the claim plugin's edit permission, a team with no player owner to ask, or being a friend.
     */
    public static boolean formationWardsNeedClaimPermission() {
        return INSTANCE.formations.wardsNeedClaimPermission.getValue();
    }

    /**
     * Whether an unbound formation plate identifies the formation standing in front of it. On by default,
     * which makes a plate usable without a command; a bound plate and the allow list are unaffected. The cost
     * is a structure check per candidate definition per neighbouring position, so it can be turned off.
     */
    public static boolean formationPlateAutoDetect() {
        return INSTANCE.formations.plateAutoDetect.getValue();
    }

    /**
     * Whether FTB Teams' {@code ALLY} rank counts as a friend. On by default, because that rank is FTB
     * Teams' own "this outsider is with us" marker; off leaves only real team members.
     */
    public static boolean ftbTeamsAllyCounts() {
        return INSTANCE.friends.ftbTeamsAlly.getValue();
    }

    /**
     * Whether FTB Teams' {@code INVITED} rank counts as a friend. Off by default, because a free-to-join
     * team returns that rank for anybody at all, which would make every stranger on the server a friend.
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
        public final BooleanEntry plateAutoDetect = BooleanEntry.builder("config.mxt.server.formation.plate_auto_detect", true).build();

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
