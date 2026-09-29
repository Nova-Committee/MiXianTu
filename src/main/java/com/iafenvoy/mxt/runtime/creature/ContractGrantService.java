package com.iafenvoy.mxt.runtime.creature;

import com.iafenvoy.mxt.data.creature.ContractType;
import com.iafenvoy.mxt.util.HolderHelper;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.LivingEntity;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Which contract types a body holds on the owner side. The record that decides anything stays on the creature,
 * but a grant has to be held by the owner, and the owner-side index is what answers that without walking loaded
 * levels. One entry per type: two beasts of the same contract are one grant, so losing one of them keeps it.
 *
 * <p>Nothing is granted here - the rebuild pass of {@code AbilityGrantService} reads this and grants, so a
 * contract's abilities have exactly one granter like every other source.</p>
 */
public final class ContractGrantService {
    private ContractGrantService() {
    }

    public static List<Holder<ContractType>> heldTypes(LivingEntity entity) {
        MinecraftServer server = entity.level().getServer();
        if (server == null) return List.of();
        return BoundBeastService.of(server, entity.getUUID()).stream()
                .map(BoundBeastsAttachment.Entry::type)
                .collect(Collectors.toMap(HolderHelper::id, Function.identity(), (first, second) -> first,
                        LinkedHashMap<Identifier, Holder<ContractType>>::new))
                .values().stream().toList();
    }
}
