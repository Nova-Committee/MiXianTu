package com.iafenvoy.mxt.testmod;

import com.iafenvoy.mxt.attachment.AbilityAttachment;
import com.iafenvoy.mxt.event.CurseRemoveEvent;
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
 * Development-only mod that contributes the mxt_test datapack and client resources.
 * <p>
 * It carries no audit: the game is started to be played, and the fixtures, items and {@code /mxt_test}
 * commands here are what make the scenario playable. Behaviour is checked by reading the code and by
 * running the game, not by a suite that runs itself on every boot.
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

    /**
     * Development-only trace of every curse removal. Nothing in the mod logs this by itself, and the reason is
     * the one part of a removal that no data pack can observe, so it is read here while playing.
     */
    private static void logCurseRemovals(CurseRemoveEvent.Post event) {
        LOGGER.info("curse removed: {} reason={} stacks={} sources={}", HolderHelper.id(event.curse()),
                event.reason(), event.state().stacks(), event.sources().stream().map(Identifier::toString).sorted().toList());
    }

    /**
     * Gives every joining player the abilities the test hotbar is meant to show, so a development session
     * starts with something to press.
     */
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
