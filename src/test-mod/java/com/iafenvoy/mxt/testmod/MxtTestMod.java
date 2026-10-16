package com.iafenvoy.mxt.testmod;

import com.iafenvoy.mxt.event.CurseRemoveEvent.Post;
import com.iafenvoy.mxt.probe.ProbeTests;
import com.iafenvoy.mxt.util.HolderHelper;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import org.slf4j.Logger;

/**
 * Development-only mod contributing the mxt_test datapack and client resources; behaviour is checked by
 * playing the scenario, not by a suite that runs on boot.
 */
@Mod(MxtTestMod.MOD_ID)
public final class MxtTestMod {
    public static final String MOD_ID = "mxt_test";
    private static final Logger LOGGER = LogUtils.getLogger();

    public MxtTestMod(IEventBus modBus) {
        MxtTestItems.REGISTRY.register(modBus);
        MxtTestForgeItems.REGISTRY.register(modBus);
        MxtTestTechniqueItems.REGISTRY.register(modBus);
        MxtTestWeaponItems.REGISTRY.register(modBus);
        AlchemyTestHeatBlocks.register(modBus);
        HerbTestBlocks.register(modBus);
        MxtTestEntities.REGISTRY.register(modBus);
        PillProbeActions.REGISTRY.register(modBus);
        FormationProbeActions.REGISTRY.register(modBus);
        ProbeMountRenders.REGISTRY.register(modBus);
        modBus.addListener(MxtTestMod::registerAttributes);
        modBus.addListener(ProbeTests::registerFunctions);
        modBus.addListener(ProbeTests::registerTests);
        NeoForge.EVENT_BUS.addListener(MxtTestMod::logCurseRemovals);
        LOGGER.info("Loaded MiXianTu test mod");
    }

    private static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(MxtTestEntities.PROBE_BEAST.get(), ProbeBeast.createAttributes().build());
    }

    // Nothing in the mod logs a removal by itself, and the reason is the one part no data pack can observe.
    private static void logCurseRemovals(Post event) {
        LOGGER.info("curse removed: {} reason={} stacks={} sources={}", HolderHelper.id(event.curse()), event.reason(), event.state().stacks(), event.sources().stream().map(Identifier::toString).sorted().toList());
    }
}
