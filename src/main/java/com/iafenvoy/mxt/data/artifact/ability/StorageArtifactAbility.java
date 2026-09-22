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
 * the stack's storage component. {@link com.iafenvoy.mxt.data.artifact.Artifact} refuses a second entry of this
 * kind, so "how many slots has it" always has one answer.
 *
 * <p>It is also a {@link ToggableArtifactAbility}, which is what puts it on the wheel: opening the storage is
 * something a player has to press for, and the wheel is where the mod keeps everything of that kind. The press
 * opens a chest-shaped container over the artifact's own contents, exactly the way the reference implementation
 * does it: the screen is a vanilla container menu, the title is the artifact's name, and the contents are the
 * artifact's, not a copy of them.</p>
 *
 * <p>The capability has no state: nothing stays on when the container closes, so the wheel draws it as an
 * ordinary activation rather than as a switch.</p>
 */
public record StorageArtifactAbility(NumberProvider slots) implements ToggableArtifactAbility {
    /** The name this capability is addressed by inside its artifact; see {@link #key()}. */
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

    /**
     * Opens the artifact's storage for whoever pressed the cell. Refusals are the two ways it can be unavailable:
     * the definition declares no slots at all, or the artifact will not let this holder reach them.
     */
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
