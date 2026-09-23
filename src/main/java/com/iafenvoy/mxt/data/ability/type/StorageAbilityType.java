package com.iafenvoy.mxt.data.ability.type;

import com.iafenvoy.mxt.data.ability.AbilityType;
import com.iafenvoy.mxt.data.ability.Togglable;
import com.iafenvoy.mxt.data.ability.ToggleContext;
import com.iafenvoy.mxt.registry.MxtMenus;
import com.iafenvoy.mxt.runtime.artifact.ArtifactService;
import com.iafenvoy.mxt.runtime.artifact.ArtifactStorageContainer;
import com.iafenvoy.mxt.runtime.artifact.ArtifactStorageService;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;

/**
 * Gives the ability's carrier an inventory of its own; the slot count is the whole declaration and the contents
 * live in the stack's storage component. A one-shot {@link Togglable}: pressing it opens the box and nothing stays
 * on, so the wheel draws an ordinary activation.
 */
public record StorageAbilityType(NumberProvider slots) implements AbilityType, Togglable {
    public static final MapCodec<StorageAbilityType> CODEC =
            NumberProvider.CODEC.fieldOf("slots").xmap(StorageAbilityType::new, StorageAbilityType::slots);

    @Override
    public MapCodec<StorageAbilityType> codec() {
        return CODEC;
    }

    @Override
    public Result activate(ToggleContext context) {
        if (!(context.holder() instanceof ServerPlayer player)) return Result.refused(Failure.UNAVAILABLE);
        ItemStack carrier = context.carrier();
        if (carrier == null || carrier.isEmpty()) return Result.refused(Failure.NO_CARRIER);
        Provider access = player.level().registryAccess();
        int capacity = ArtifactService.storageSlots(carrier, context.ability(), context.formula());
        // No slots is the slots formula's own answer, not a state the press cannot describe.
        if (capacity <= 0) return Result.refused(Failure.INVALID_FORMULA);
        if (!ArtifactStorageService.INSTANCE.mayAccess(access, carrier, player))
            return Result.refused(Failure.NOT_OWNED);
        Component title = Component.translatable("screen.mxt.artifact_storage", context.ability().value().name());
        ArtifactStorageContainer contents = new ArtifactStorageContainer(player, HolderHelper.id(context.ability()), capacity);
        int rows = capacity / ArtifactService.STORAGE_COLUMNS;
        player.openMenu(new SimpleMenuProvider((id, inventory, opener) ->
                        new ChestMenu(MxtMenus.ARTIFACT_STORAGE.get(), id, inventory, contents, rows), title),
                // The client rebuilds the same menu shape from the row count, which is all it needs: the slots
                // themselves arrive through the menu sync.
                buffer -> buffer.writeVarInt(rows));
        return Result.activated();
    }
}
