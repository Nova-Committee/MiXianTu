package com.iafenvoy.mxt.runtime.artifact;

import com.iafenvoy.mxt.attachment.FlightAttachment;
import com.iafenvoy.mxt.data.ability.Abilities;
import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.runtime.artifact.FlightService.Failure;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent.Post;

import java.util.Optional;

// Per-tick server driver for an active flight: it re-reads the ability the flight was started with wherever the
// player now keeps the thing that declared it, and ends the flight when it is no longer offered.
@EventBusSubscriber
public final class FlightEventBridge {
    private FlightEventBridge() {
    }

    @SubscribeEvent
    public static void onEntityTick(Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.level().isClientSide()) return;
        FlightAttachment data = player.getData(MxtAttachments.FLIGHT);
        if (!data.active()) return;
        Identifier archetype = data.archetype().orElse(null);
        if (archetype == null) {
            FlightService.dismount(player, Failure.NOT_FLYABLE);
            return;
        }
        Provider access = player.level().registryAccess();
        Holder<Ability> ability = Abilities.resolve(access, archetype).orElse(null);
        Optional<ItemStack> carrier = ArtifactService.carried(access, player, archetype);
        if (ability == null || carrier.isEmpty() || !FlightService.ownsEquippedArchetype(player, carrier.get(), ability)) {
            FlightService.dismount(player, Failure.NOT_OWNED);
            return;
        }
        FlightService.tick(player, ability, FormulaContext.of(player));
    }
}
