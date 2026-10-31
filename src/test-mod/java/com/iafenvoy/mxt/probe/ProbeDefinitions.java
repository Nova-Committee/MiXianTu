package com.iafenvoy.mxt.probe;

import com.iafenvoy.mxt.testmod.MxtTestMod;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * The definition ids and fixtures the probe bodies name, lifted from the {@code /mxt_test} harness so the ported
 * bodies read exactly as they did. Every id is in the test pack's own namespace.
 */
public final class ProbeDefinitions {
    public static final Identifier QI = id("qi");
    public static final Identifier QI_REFINING = id("qi_refining");
    public static final Identifier FOUNDATION = id("foundation");
    public static final Identifier CORE_FORMING = id("core_forming");
    public static final Identifier POOR_QUALITY = id("poor");
    public static final Identifier NORMAL_QUALITY = id("normal");
    public static final Identifier EXCELLENT_QUALITY = id("excellent");
    public static final Identifier SPIRIT_POWER = id("spirit_power");
    public static final Identifier SPIRIT_POWER_REFINING = id("spirit_power_refining");
    public static final Identifier WATER_POWER = id("water_power");
    public static final Identifier SOUL_POWER = id("soul_power");
    public static final Identifier ROOT = id("qingxiao_fire_root");
    public static final Identifier WATER_ROOT = id("water_root");
    public static final Identifier PHYSIQUE = id("qingxiao_body");
    public static final Identifier TECHNIQUE = id("qingxiao_breathing_manual");
    public static final Identifier CULTIVATE = id("qingxiao_meditation");
    public static final Identifier FREE_MEDITATION = id("free_meditation");
    public static final Identifier TECHNIQUE_MEDITATION = id("technique_meditation");
    public static final Identifier DUAL_MEDITATION = id("dual_meditation");
    public static final Identifier NAMED_MEDITATION = id("named_meditation");
    public static final Identifier STRICT_MEDITATION = id("strict_meditation");
    public static final Identifier WORLDLY_MEDITATION = id("worldly_meditation");
    public static final Identifier SWORD_MANUAL = id("sword_manual");
    public static final Identifier SWORD_MASTERY = id("sword_mastery");
    public static final Identifier FORMATION = id("spirit_gathering");
    public static final Identifier TRIAL_REALM = id("trial_realm");
    public static final Identifier CONTRACT = id("master_servant");
    public static final Identifier OPEN_CONTRACT = id("open_contract");
    // The owner side of each contract fixture: a body must hold what its contract type declares, and the two
    // abilities are distinct so losing one type cannot be mistaken for losing the other.
    public static final Identifier OWNER_BOND = id("owner_bond");
    public static final Identifier OPEN_BOND = id("open_bond");
    // The creature profile's own chain: three levels, a growth value the pack feeds, and a third level whose
    // condition is never true, so a promotion can be blocked after its mastery is already paid.
    public static final Identifier PROBE_BEAST_PROFILE = id("probe_beast");
    public static final Identifier BEAST_1 = id("beast_1");
    public static final Identifier BEAST_2 = id("beast_2");
    public static final Identifier BEAST_3 = id("beast_3");
    public static final Identifier BEAST_GROWTH = id("beast_growth");
    public static final Identifier PROGRESSION_GROWTH = id("progression_growth");
    public static final Identifier SWORD_ART_1 = id("sword_art_1");
    // A content mod's order, registered by the probe the way a content mod registers one: known to both sides, and
    // deliberately not on the probe beast's own list.
    public static final Identifier PROBE_ONLY = id("probe_only");
    public static final Identifier PROBE_FIRE_ROOT = id("fire_root");
    public static final Identifier PROBE_WATER_ROOT = id("water_root");
    public static final Identifier PROBE_INERT_ROOT = id("inert_root");
    public static final Identifier PROBE_ANTI_WATER_ROOT = id("anti_water_root");
    public static final Identifier PROBE_METAL_ROOT = id("metal_root");
    public static final Identifier PROBE_WOOD_ROOT = id("wood_root");
    public static final Identifier PROBE_EARTH_ROOT = id("earth_root");
    public static final Identifier PROBE_DUAL_ROOT = id("dual_root");
    public static final Identifier PROBE_DUAL_EVEN_ROOT = id("dual_even_root");
    public static final Identifier PROBE_METAL_ELEMENT = id("metal");
    public static final Identifier PROBE_WOOD_ELEMENT = id("wood");
    public static final Identifier PROBE_EARTH_ELEMENT = id("earth");
    public static final Identifier PROBE_FIVE_PHASES_TAG = id("five_phases");
    public static final Identifier PROBE_FIRE_ELEMENT = id("fire");
    public static final Identifier PROBE_WATER_ELEMENT = id("water");
    public static final Identifier PROBE_INERT_ELEMENT = id("inert");
    public static final Identifier PROBE_GATED_ELEMENT = id("condition_gated");
    public static final Identifier PROBE_ELEMENT_ABILITY = id("elemental_probe");
    public static final Identifier PROBE_ELEMENTAL = id("elemental_probe");
    public static final Identifier PROBE_ELEMENT_TAG = id("basic");
    public static final Identifier PROBE_PHYSIQUE = id("probe_body");
    public static final Identifier PROBE_AFFINITY_ABILITY = id("firebolt");
    public static final double PROBE_FIRE_AFFINITY = 1.1D;
    public static final Identifier PROBE_TECHNIQUE = id("sword_manual");
    public static final Identifier PROBE_PROGRESSION_LEVEL = id("sword_art_2");
    public static final Identifier PROBE_ABILITY = id("artifact_guard");
    public static final Identifier SWORD_FOCUS = id("sword_focus");
    // The storage entry the fixture artifacts name; it is an ordinary mxt:ability entry.
    public static final Identifier PROBE_BOUND_STORAGE = id("bound_storage");
    // An id of the right shape that nothing registers, so the kind check has a negative that is not an artifact.
    public static final Identifier PROBE_ABSENT_ABILITY = id("absent_storage");
    // Wrong on purpose: grants an active ability as mxt:passive.
    // The fixture kept its name from when the check existed; it is now only the other half of the claim conflict.
    public static final Identifier MISDECLARED_ARTIFACT = id("misdeclared_grant_probe");
    // Wrong on purpose: claims the item MISDECLARED_ARTIFACT already claims.
    public static final Identifier CLAIM_CONFLICT_ARTIFACT = id("claim_conflict_probe");
    // Active, so a derived wheel page may list it while it is granted.
    public static final Identifier PROBE_WHEEL_ABILITY = id("firebolt");
    // Declared mxt:modifier, so a derived page must leave it out even while granted.
    public static final Identifier PROBE_WHEEL_PASSIVE = id("artifact_guard");
    // The fixture artifact whose hand page names a flight and a storage ability, and whose own id is no ability.
    public static final Identifier PROBE_WHEEL_TOGGLE = id("bound_sword");
    // More active abilities than one page holds; the overflow leg expects thirteen entries and up.
    public static final List<Identifier> PROBE_WHEEL_MANY = List.of(
            id("firebolt"), id("awaken_divine_sense"), id("expend_test"), id("infuse_true_essence"),
            id("curse_apply_probe"), id("curse_cleanse_probe"), id("curse_query_probe"),
            id("curse_remaining_probe"), id("curse_replace_probe"), id("elemental_probe"),
            id("pos_pulse"), id("pull_pulse"), id("qingxiao_firebolt"), id("sigil_pulse")
    );
    public static final Identifier TEST_ABILITY_SOURCE = id("grant/test_kit");
    public static final List<Identifier> TEST_ACTIVE_ABILITIES = List.of(
            id("firebolt"), id("awaken_divine_sense"), id("expend_test"), id("infuse_true_essence"),
            id("curse_apply_probe"), id("curse_cleanse_probe"), id("curse_query_probe"),
            id("curse_remaining_probe"), id("curse_replace_probe")
    );

    private ProbeDefinitions() {
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MxtTestMod.MOD_ID, path);
    }
}
