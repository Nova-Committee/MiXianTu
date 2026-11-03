package com.iafenvoy.mxt.runtime;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.saturn.profiling.ProfilerSource;

/**
 * The content-side profile buckets, claimed once from the mod constructor. Saturn reads the sources that exist when
 * a server starts, so every name used by a {@code scope(...)} call has to be claimed here first; a bucket Saturn
 * already claims is not claimed twice.
 */
public final class MxtProfilerSources {
    public static final ProfilerSource AURA = ProfilerSource.register(MiXianTu.MOD_ID, "aura");
    public static final ProfilerSource AURA_QUERY = ProfilerSource.register(MiXianTu.MOD_ID, "aura/query", AURA);
    public static final ProfilerSource AURA_BLOCK_CACHE = ProfilerSource.register(MiXianTu.MOD_ID, "aura/block_cache", AURA);
    public static final ProfilerSource CULTIVATION = ProfilerSource.register(MiXianTu.MOD_ID, "cultivation");
    public static final ProfilerSource CULTIVATION_DISTRIBUTION = ProfilerSource.register(MiXianTu.MOD_ID, "cultivation/distribution", CULTIVATION);
    public static final ProfilerSource PROGRESSION = ProfilerSource.register(MiXianTu.MOD_ID, "progression");
    public static final ProfilerSource COMBAT = ProfilerSource.register(MiXianTu.MOD_ID, "combat");
    public static final ProfilerSource FORMATION = ProfilerSource.register(MiXianTu.MOD_ID, "formation");
    public static final ProfilerSource FORMATION_STRUCTURE = ProfilerSource.register(MiXianTu.MOD_ID, "formation/structure", FORMATION);
    public static final ProfilerSource CURSE = ProfilerSource.register(MiXianTu.MOD_ID, "curse");
    public static final ProfilerSource TRIBULATION = ProfilerSource.register(MiXianTu.MOD_ID, "tribulation");
    public static final ProfilerSource ARTIFACT = ProfilerSource.register(MiXianTu.MOD_ID, "artifact");
    public static final ProfilerSource SPIRIT = ProfilerSource.register(MiXianTu.MOD_ID, "spirit");
    public static final ProfilerSource CREATURE = ProfilerSource.register(MiXianTu.MOD_ID, "creature");
    public static final ProfilerSource ALCHEMY = ProfilerSource.register(MiXianTu.MOD_ID, "alchemy");
    public static final ProfilerSource HERB = ProfilerSource.register(MiXianTu.MOD_ID, "herb");
    public static final ProfilerSource FORGING = ProfilerSource.register(MiXianTu.MOD_ID, "forging");
    public static final ProfilerSource TALISMAN = ProfilerSource.register(MiXianTu.MOD_ID, "talisman");
    public static final ProfilerSource CRAFTING = ProfilerSource.register(MiXianTu.MOD_ID, "crafting");
    public static final ProfilerSource ECONOMY = ProfilerSource.register(MiXianTu.MOD_ID, "economy");
    public static final ProfilerSource ECONOMY_CHANGE = ProfilerSource.register(MiXianTu.MOD_ID, "economy/change", ECONOMY);
    public static final ProfilerSource SECRET_REALM = ProfilerSource.register(MiXianTu.MOD_ID, "secret_realm");
    public static final ProfilerSource RIFT = ProfilerSource.register(MiXianTu.MOD_ID, "rift");

    private MxtProfilerSources() {
    }

    // Touching the class is what claims the constants above; the constructor calls this before the register event, so
    // no scope can name a bucket the synced registry does not have.
    public static void init() {
    }
}
