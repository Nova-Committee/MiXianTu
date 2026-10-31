package com.iafenvoy.mxt.runtime;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.saturn.profiling.ServerProfiling;

/**
 * The content-side profile buckets, registered once from the mod constructor. Saturn samples only the sources that
 * exist when a server starts, so every name used by a {@code scope(...)} call has to be claimed here first; a name
 * Saturn already registers (a resource, an ability, an entity) is read from it instead of being registered twice.
 */
public final class MxtProfilerSources {
    public static final ServerProfiling.Source AURA = ServerProfiling.Source.register(MiXianTu.MOD_ID, "aura");
    public static final ServerProfiling.Source AURA_QUERY = ServerProfiling.Source.register(MiXianTu.MOD_ID, "aura/query", AURA);
    public static final ServerProfiling.Source AURA_BLOCK_CACHE = ServerProfiling.Source.register(MiXianTu.MOD_ID, "aura/block_cache", AURA);
    public static final ServerProfiling.Source CULTIVATION = ServerProfiling.Source.register(MiXianTu.MOD_ID, "cultivation");
    public static final ServerProfiling.Source CULTIVATION_DISTRIBUTION = ServerProfiling.Source.register(MiXianTu.MOD_ID, "cultivation/distribution", CULTIVATION);
    public static final ServerProfiling.Source PROGRESSION = ServerProfiling.Source.register(MiXianTu.MOD_ID, "progression");
    public static final ServerProfiling.Source COMBAT = ServerProfiling.Source.register(MiXianTu.MOD_ID, "combat");
    public static final ServerProfiling.Source FORMATION = ServerProfiling.Source.register(MiXianTu.MOD_ID, "formation");
    public static final ServerProfiling.Source FORMATION_STRUCTURE = ServerProfiling.Source.register(MiXianTu.MOD_ID, "formation/structure", FORMATION);
    public static final ServerProfiling.Source CURSE = ServerProfiling.Source.register(MiXianTu.MOD_ID, "curse");
    public static final ServerProfiling.Source TRIBULATION = ServerProfiling.Source.register(MiXianTu.MOD_ID, "tribulation");
    public static final ServerProfiling.Source ARTIFACT = ServerProfiling.Source.register(MiXianTu.MOD_ID, "artifact");
    public static final ServerProfiling.Source SPIRIT = ServerProfiling.Source.register(MiXianTu.MOD_ID, "spirit");
    public static final ServerProfiling.Source CREATURE = ServerProfiling.Source.register(MiXianTu.MOD_ID, "creature");
    public static final ServerProfiling.Source ALCHEMY = ServerProfiling.Source.register(MiXianTu.MOD_ID, "alchemy");
    public static final ServerProfiling.Source HERB = ServerProfiling.Source.register(MiXianTu.MOD_ID, "herb");
    public static final ServerProfiling.Source FORGING = ServerProfiling.Source.register(MiXianTu.MOD_ID, "forging");
    public static final ServerProfiling.Source TALISMAN = ServerProfiling.Source.register(MiXianTu.MOD_ID, "talisman");
    public static final ServerProfiling.Source CRAFTING = ServerProfiling.Source.register(MiXianTu.MOD_ID, "crafting");
    public static final ServerProfiling.Source ECONOMY = ServerProfiling.Source.register(MiXianTu.MOD_ID, "economy");
    public static final ServerProfiling.Source ECONOMY_CHANGE = ServerProfiling.Source.register(MiXianTu.MOD_ID, "economy/change", ECONOMY);
    public static final ServerProfiling.Source SECRET_REALM = ServerProfiling.Source.register(MiXianTu.MOD_ID, "secret_realm");
    public static final ServerProfiling.Source RIFT = ServerProfiling.Source.register(MiXianTu.MOD_ID, "rift");

    private MxtProfilerSources() {
    }

    // Touching the class is what registers the constants above; the constructor calls this so no scope can name an
    // unregistered bucket.
    public static void init() {
    }
}
