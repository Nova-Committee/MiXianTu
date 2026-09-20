package com.iafenvoy.mxt.config;

import com.iafenvoy.jupiter.config.container.AutoInitConfigContainer;
import com.iafenvoy.jupiter.config.entry.BooleanEntry;
import com.iafenvoy.jupiter.config.entry.EnumEntry;
import com.iafenvoy.jupiter.config.entry.IntegerEntry;
import com.iafenvoy.mxt.MiXianTu;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * The server-side switches: one tab per system, in the order the screen shows them, so this file is the layout.
 * An entry's name is what an admin scans and its tooltip is where the reasoning goes; call sites read the entry
 * itself through {@link #INSTANCE}, so there is no wrapper to keep in step with it. Numeric entries carry their
 * own range, which is also their decode check, so nothing downstream clamps them again.
 *
 * <p>Serialised keys are short, so the saved file reads {@code {"formation": {"respect_friends": true}}}. The
 * first layout spelled every key out in full ({@code config.mxt.server.formation.respect_friends}) and kept the
 * friend ranks in a tab of their own; the key rules in the constructor translate both on load.</p>
 */
public final class MxtServerConfig extends AutoInitConfigContainer {
    public static final MxtServerConfig INSTANCE = new MxtServerConfig();

    public final Curios curios = new Curios();
    public final Cultivation cultivation = new Cultivation();
    public final Talisman talisman = new Talisman();
    public final Aura aura = new Aura();
    public final Formations formations = new Formations();
    public final Commands commands = new Commands();
    public final Compat compat = new Compat();

    private MxtServerConfig() {
        super(Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "server"), "config.mxt.server", "./config/mxt-server.json");
        // What the old file wrote: a tab that has been merged away, and keys that spelled out their own path.
        this.dataFixer.registerKeyRule("friends", "compat");
        this.dataFixer.registerKeyRule("^config\\.mxt\\.server\\.[a-z_]+\\.([a-z_]+)$", key -> key.substring(key.lastIndexOf('.') + 1));
    }

    /**
     * Which items the two Curios slots accept on their own, and whether this mod draws them where Curios would
     * not. Not compatibility: the slots are part of the equipment model the rest of the mod is built on.
     */
    public static final class Curios extends AutoInitConfigCategoryBase {
        public final EnumEntry<BackMode> backMode = EnumEntry.builder("config.mxt.server.curios.back_mode", BackMode.MANUAL)
                .key("back_mode")
                .tooltip("config.mxt.server.curios.back_mode.tooltip")
                .nameProvider(value -> Component.translatable("config.mxt.server.mode." + value.name().toLowerCase()))
                .build();
        public final EnumEntry<BeltMode> beltMode = EnumEntry.builder("config.mxt.server.curios.belt_mode", BeltMode.MANUAL)
                .key("belt_mode")
                .tooltip("config.mxt.server.curios.belt_mode.tooltip")
                .nameProvider(value -> Component.translatable("config.mxt.server.mode." + value.name().toLowerCase()))
                .build();
        public final BooleanEntry forceRenderSlots = BooleanEntry.builder("config.mxt.server.curios.force_render_slots", false)
                .key("force_render_slots")
                .tooltip("config.mxt.server.curios.force_render_slots.tooltip")
                .build();

        private Curios() {
            super("curios", "config.mxt.server.curios");
        }
    }

    public static final class Cultivation extends AutoInitConfigCategoryBase {
        public final BooleanEntry allowMovement = BooleanEntry.builder("config.mxt.server.cultivation.allow_movement", false)
                .key("allow_movement")
                .tooltip("config.mxt.server.cultivation.allow_movement.tooltip")
                .build();
        public final BooleanEntry forbidWithoutEligibleAura = BooleanEntry.builder("config.mxt.server.cultivation.forbid_without_eligible_aura", false)
                .key("forbid_without_eligible_aura")
                .tooltip("config.mxt.server.cultivation.forbid_without_eligible_aura.tooltip")
                .build();
        public final IntegerEntry techniqueLearnCooldown = IntegerEntry.builder("config.mxt.server.cultivation.technique_learn_cooldown", 60)
                .key("technique_learn_cooldown")
                .tooltip("config.mxt.server.cultivation.technique_learn_cooldown.tooltip")
                .range(0, 72_000).build();

        private Cultivation() {
            super("cultivation", "config.mxt.server.cultivation");
        }
    }

    /**
     * How a carrier behaves when it is used by hand rather than poured into. The pour has a price of its own, so
     * the only thing left to state here is how often the click may be spent.
     */
    public static final class Talisman extends AutoInitConfigCategoryBase {
        public final IntegerEntry useCooldown = IntegerEntry.builder("config.mxt.server.talisman.use_cooldown", 20)
                .key("use_cooldown")
                .tooltip("config.mxt.server.talisman.use_cooldown.tooltip")
                .range(0, 72_000).build();

        private Talisman() {
            super("talisman", "config.mxt.server.talisman");
        }
    }

    /**
     * How often the aura world is recomputed and how much of that work reports itself. Every switch here trades
     * responsiveness for server time, so they are grouped away from the gameplay tabs.
     */
    public static final class Aura extends AutoInitConfigCategoryBase {
        public final IntegerEntry blockAuraTickInterval = IntegerEntry.builder("config.mxt.server.aura.block_aura_tick_interval", 10)
                .key("block_aura_tick_interval")
                .tooltip("config.mxt.server.aura.block_aura_tick_interval.tooltip")
                .range(1, 1200).build();
        public final IntegerEntry entityRefreshInterval = IntegerEntry.builder("config.mxt.server.aura.entity_refresh_interval", 10)
                .key("entity_refresh_interval")
                .tooltip("config.mxt.server.aura.entity_refresh_interval.tooltip")
                .range(1, 1200).build();
        public final IntegerEntry auraSyncInterval = IntegerEntry.builder("config.mxt.server.aura.sync_interval", 5)
                .key("sync_interval")
                .tooltip("config.mxt.server.aura.sync_interval.tooltip")
                .range(1, 1200).build();
        public final BooleanEntry queryStats = BooleanEntry.builder("config.mxt.server.aura.query_stats", false)
                .key("query_stats")
                .tooltip("config.mxt.server.aura.query_stats.tooltip")
                .build();

        private Aura() {
            super("aura", "config.mxt.server.aura");
        }
    }

    /**
     * The formation switches that are about formations alone. Anything that only matters with a claim plugin
     * installed lives in {@link Compat} instead.
     */
    public static final class Formations extends AutoInitConfigCategoryBase {
        public final BooleanEntry plateAutoDetect = BooleanEntry.builder("config.mxt.server.formation.plate_auto_detect", true)
                .key("plate_auto_detect")
                .tooltip("config.mxt.server.formation.plate_auto_detect.tooltip")
                .build();
        /**
         * What an empty allow list means; every plate written before the list existed relies on the default.
         */
        public final BooleanEntry emptyAllowsAll = BooleanEntry.builder("config.mxt.server.formation.empty_plate_allows_all", true)
                .key("empty_plate_allows_all")
                .tooltip("config.mxt.server.formation.empty_plate_allows_all.tooltip")
                .build();
        public final BooleanEntry drawsEnvironment = BooleanEntry.builder("config.mxt.server.formation.draws_environment", false)
                .key("draws_environment")
                .tooltip("config.mxt.server.formation.draws_environment.tooltip")
                .build();
        /**
         * Whether a formation that declares {@code spare_friends} spares whoever its owner counts as a friend.
         * It changes nothing on its own: a formation only identifies friends by declaring the switch.
         */
        public final BooleanEntry respectFriends = BooleanEntry.builder("config.mxt.server.formation.respect_friends", true)
                .key("respect_friends")
                .tooltip("config.mxt.server.formation.respect_friends.tooltip")
                .build();
        /**
         * Whether the owner's friends may take a formation down as well; off leaves it to the owner and operators.
         */
        public final BooleanEntry teammatesCanDismantle = BooleanEntry.builder("config.mxt.server.formation.teammates_can_dismantle", false)
                .key("teammates_can_dismantle")
                .tooltip("config.mxt.server.formation.teammates_can_dismantle.tooltip")
                .build();

        private Formations() {
            super("formation", "config.mxt.server.formation");
        }
    }

    /**
     * The command switches: whether a subtree may also be reached at the command root instead of under
     * {@code /mxt}. One entry per subtree, all the same rule.
     */
    public static final class Commands extends AutoInitConfigCategoryBase {
        public final BooleanEntry ability = BooleanEntry.builder("config.mxt.server.commands.ability", true).key("ability").tooltip("config.mxt.server.commands.ability.tooltip").build();
        public final BooleanEntry aura = BooleanEntry.builder("config.mxt.server.commands.aura", true).key("aura").tooltip("config.mxt.server.commands.aura.tooltip").build();
        public final BooleanEntry curse = BooleanEntry.builder("config.mxt.server.commands.curse", true).key("curse").tooltip("config.mxt.server.commands.curse.tooltip").build();
        public final BooleanEntry display = BooleanEntry.builder("config.mxt.server.commands.display", true).key("display").tooltip("config.mxt.server.commands.display.tooltip").build();
        public final BooleanEntry formation = BooleanEntry.builder("config.mxt.server.commands.formation", true).key("formation").tooltip("config.mxt.server.commands.formation.tooltip").build();
        public final BooleanEntry friend = BooleanEntry.builder("config.mxt.server.commands.friend", true).key("friend").tooltip("config.mxt.server.commands.friend.tooltip").build();
        public final BooleanEntry identity = BooleanEntry.builder("config.mxt.server.commands.identity", true).key("identity").tooltip("config.mxt.server.commands.identity.tooltip").build();
        public final BooleanEntry lightning = BooleanEntry.builder("config.mxt.server.commands.lightning", true).key("lightning").tooltip("config.mxt.server.commands.lightning.tooltip").build();
        public final BooleanEntry picker = BooleanEntry.builder("config.mxt.server.commands.picker", true).key("picker").tooltip("config.mxt.server.commands.picker.tooltip").build();
        public final BooleanEntry talisman = BooleanEntry.builder("config.mxt.server.commands.talisman", true).key("talisman").tooltip("config.mxt.server.commands.talisman.tooltip").build();
        public final BooleanEntry technique = BooleanEntry.builder("config.mxt.server.commands.technique", true).key("technique").tooltip("config.mxt.server.commands.technique.tooltip").build();
        public final BooleanEntry trade = BooleanEntry.builder("config.mxt.server.commands.trade", true).key("trade").tooltip("config.mxt.server.commands.trade.tooltip").build();
        public final BooleanEntry tribulation = BooleanEntry.builder("config.mxt.server.commands.tribulation", true).key("tribulation").tooltip("config.mxt.server.commands.tribulation.tooltip").build();

        private Commands() {
            super("commands", "config.mxt.server.commands");
        }
    }

    /**
     * Everything that only means something with another mod installed: FTB Teams answers the friend
     * judgement, FTB Chunks is what the claim switches hand protection to. Curios is deliberately not here —
     * the equipment slots are part of this mod's own model, not an integration.
     */
    public static final class Compat extends AutoInitConfigCategoryBase {
        /**
         * FTB Teams' own "this outsider is with us" rank; off leaves only real team members.
         */
        public final BooleanEntry ftbTeamsAlly = BooleanEntry.builder("config.mxt.server.compat.ftb_teams_ally", true)
                .key("ftb_teams_ally")
                .tooltip("config.mxt.server.compat.ftb_teams_ally.tooltip")
                .build();
        /**
         * Off by default: a free-to-join team reports that rank for anybody at all.
         */
        public final BooleanEntry ftbTeamsInvited = BooleanEntry.builder("config.mxt.server.compat.ftb_teams_invited", false)
                .key("ftb_teams_invited")
                .tooltip("config.mxt.server.compat.ftb_teams_invited.tooltip")
                .build();
        public final EnumEntry<ClaimLinkage> claimLinkage = EnumEntry.builder("config.mxt.server.compat.claim_linkage", ClaimLinkage.NONE)
                .key("claim_linkage")
                .tooltip("config.mxt.server.compat.claim_linkage.tooltip")
                .nameProvider(value -> Component.translatable("config.mxt.server.compat.claim_linkage." + value.name().toLowerCase()))
                .build();
        /**
         * Off hands protection over even where there is nothing to hand it to, protecting nothing at all.
         */
        public final BooleanEntry delegateRequiresClaims = BooleanEntry.builder("config.mxt.server.compat.delegate_requires_claims", true)
                .key("delegate_requires_claims")
                .tooltip("config.mxt.server.compat.delegate_requires_claims.tooltip")
                .build();
        /**
         * Three ways through, all of them the landowner's own answer; inert without a claim plugin.
         */
        public final BooleanEntry wardsNeedClaimPermission = BooleanEntry.builder("config.mxt.server.compat.wards_need_claim_permission", true)
                .key("wards_need_claim_permission")
                .tooltip("config.mxt.server.compat.wards_need_claim_permission.tooltip")
                .build();

        private Compat() {
            super("compat", "config.mxt.server.compat");
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
