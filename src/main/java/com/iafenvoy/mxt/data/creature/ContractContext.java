package com.iafenvoy.mxt.data.creature;

import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * What one contract question or operation is asked about: the creature, the owner's id, the owner themselves
 * when vanilla's owner logic can resolve them, and the contract type. The id and the player answer different
 * questions - a death has an owner to name and possibly nobody online, and a follow needs the player.
 *
 * <p>Lives here rather than in {@code api} because that package holds interfaces only.</p>
 */
public record ContractContext(Mob self, @Nullable UUID owner, @Nullable ServerPlayer player,
                              Holder<ContractType> type) {
    public static ContractContext of(Mob self, @Nullable UUID owner, @Nullable ServerPlayer player, Holder<ContractType> type) {
        return new ContractContext(self, owner, player, type);
    }
}
