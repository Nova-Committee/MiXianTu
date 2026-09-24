package com.iafenvoy.mxt.runtime.artifact;

import com.iafenvoy.mxt.attachment.FlightAttachment;
import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.artifact.FlightService.Failure;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent.Post;

import java.util.Optional;

// Per-tick server driver for an active flight: it re-reads the ability the flight was started with wherever the
// holder now keeps the thing that declared it, and ends the flight when it is no longer offered.
@EventBusSubscriber
public final class FlightEventBridge {
    private FlightEventBridge() {
    }

    @SubscribeEvent
    public static void onEntityTick(Post event) {
        // getExistingData: every living entity ticks here, and getData would put an empty flight record on each of
        // them the moment they spawn.
        if (!(event.getEntity() instanceof LivingEntity holder) || holder.level().isClientSide()) return;
        FlightAttachment data = holder.getExistingData(MxtAttachments.FLIGHT).orElse(null);
        if (data == null || !data.active()) return;
        // The disabled check the id lookup used to apply: mxt:disabled has to end a flight that is already up.
        Holder<Ability> ability = data.archetype().filter(archetype -> !MxtDatapackRegistries.isDisabled(MxtResourceKeys.ABILITY, archetype)).orElse(null);
        if (ability == null) {
            FlightService.dismount(holder, Failure.NOT_FLYABLE);
            return;
        }
        Optional<ItemStack> carrier = ArtifactService.carried(holder.level().registryAccess(), holder, HolderHelper.id(ability));
        if (carrier.isEmpty() || !FlightService.ownsEquippedArchetype(holder, carrier.get(), ability)) {
            FlightService.dismount(holder, Failure.NOT_OWNED);
            return;
        }
        FlightService.tick(holder, ability, FormulaContext.of(holder));
    }
}
