package com.iafenvoy.mxt.data.artifact.ability;

import com.iafenvoy.mxt.registry.MxtMenus;
import com.iafenvoy.mxt.runtime.artifact.ArtifactService;
import com.iafenvoy.mxt.runtime.artifact.ArtifactStorageContainer;
import com.iafenvoy.mxt.runtime.artifact.ArtifactStorageService;
import com.iafenvoy.mxt.util.DefinitionText;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ChestMenu;

/**
 * Gives the artifact an inventory of its own; the slot count is the whole declaration and the contents live in
 * the stack's storage component. Also a {@link ToggableArtifactAbility}, which is what puts it on the wheel;
 * it has no state (nothing stays on when the container closes), so the wheel draws an ordinary activation.
 */
public record StorageArtifactAbility(NumberProvider slots) implements ToggableArtifactAbility {
    public static final String KEY = "storage";
    public static final MapCodec<StorageArtifactAbility> CODEC =
            NumberProvider.CODEC.fieldOf("slots").xmap(StorageArtifactAbility::new, StorageArtifactAbility::slots);

    @Override
    public MapCodec<StorageArtifactAbility> codec() {
        return CODEC;
    }

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Component displayName() {
        return Component.translatable("wheel.mxt.artifact_skill.storage");
    }

    @Override
    public Result activate(ArtifactToggleContext context) {
        if (!(context.holder() instanceof ServerPlayer player)) return Result.refused(Failure.UNAVAILABLE);
        Provider access = player.level().registryAccess();
        int capacity = ArtifactStorageService.INSTANCE.slots(access, context.stack(), context.formula());
        if (capacity <= 0) return Result.refused(Failure.UNAVAILABLE);
        if (!ArtifactStorageService.INSTANCE.mayAccess(access, context.stack(), player))
            return Result.refused(Failure.NOT_OWNED);
        Component title = Component.translatable("screen.mxt.artifact_storage", DefinitionText.name(context.artifact()));
        ArtifactStorageContainer contents = new ArtifactStorageContainer(player, HolderHelper.id(context.artifact()), capacity);
        int rows = capacity / ArtifactService.STORAGE_COLUMNS;
        player.openMenu(new SimpleMenuProvider((id, inventory, opener) ->
                        new ChestMenu(MxtMenus.ARTIFACT_STORAGE.get(), id, inventory, contents, rows), title),
                // The client rebuilds the same menu shape from the row count, which is all it needs: the slots
                // themselves arrive through the menu sync, and the artifact stays on this side.
                buffer -> buffer.writeVarInt(rows));
        return Result.activated();
    }
}
