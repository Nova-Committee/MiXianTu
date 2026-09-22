package com.iafenvoy.mxt.testmod;

import com.iafenvoy.mxt.attachment.AbilityAttachment;
import com.iafenvoy.mxt.event.CurseRemoveEvent.Post;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.ability.AbilityEventBridge;
import com.iafenvoy.mxt.util.HolderHelper;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;
import org.slf4j.Logger;

import java.util.List;

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
        NeoForge.EVENT_BUS.addListener(MxtTestMod::grantTestAbilities);
        NeoForge.EVENT_BUS.addListener(MxtTestCommands::registerCommands);
        NeoForge.EVENT_BUS.addListener(MxtTestMod::logCurseRemovals);
        LOGGER.info("Loaded MiXianTu test mod");
    }

    // Nothing in the mod logs a removal by itself, and the reason is the one part no data pack can observe.
    private static void logCurseRemovals(Post event) {
        LOGGER.info("curse removed: {} reason={} stacks={} sources={}", HolderHelper.id(event.curse()),
                event.reason(), event.state().stacks(), event.sources().stream().map(Identifier::toString).sorted().toList());
    }

    // Grants on join so the wheel editor has something to put in a sector and the wheel something to trigger.
    private static void grantTestAbilities(PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        AbilityAttachment holder = player.getData(MxtAttachments.ABILITY_HOLDER);
        Identifier source = Identifier.fromNamespaceAndPath(MOD_ID, "hotbar_test");
        for (String id : List.of("firebolt", "water_shield", "infuse_true_essence", "awaken_divine_sense")) {
            MxtDatapackRegistries.holder(MxtResourceKeys.ABILITY, Identifier.fromNamespaceAndPath(MOD_ID, id))
                    .ifPresent(ability -> holder.grant(ability, source));
        }
        AbilityEventBridge.rebuildTriggerSubscriptions(player);
    }
}
