package com.iafenvoy.mxt.probe.verify;

import com.iafenvoy.mxt.probe.Probe;
import com.iafenvoy.mxt.probe.ProbeContext;
import com.iafenvoy.mxt.registry.MxtLootConditions;
import com.iafenvoy.mxt.registry.MxtLootFunctions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

import java.util.List;

public final class LootTypeVerifyProbes {
    private LootTypeVerifyProbes() {
    }

    public static List<Probe> probes() {
        return List.of(Probe.of("verify/loot_types", LootTypeVerifyProbes::lootTypes));
    }

    private static void lootTypes(ProbeContext context) {
        context.expect("realm loot condition is registered",
                BuiltInRegistries.LOOT_CONDITION_TYPE.getValue(Identifier.parse("mxt:realm")),
                MxtLootConditions.REALM.get());
        context.expect("artifact owner loot function is registered",
                BuiltInRegistries.LOOT_FUNCTION_TYPE.getValue(Identifier.parse("mxt:set_artifact_owner")),
                MxtLootFunctions.SET_ARTIFACT_OWNER.get());
        for (String namespace : List.of("mxt", "saturn")) {
            Identifier id = Identifier.fromNamespaceAndPath(namespace, "js");
            context.check("no script loot type at " + id,
                    !BuiltInRegistries.LOOT_CONDITION_TYPE.containsKey(id)
                            && !BuiltInRegistries.LOOT_FUNCTION_TYPE.containsKey(id));
        }
    }
}
