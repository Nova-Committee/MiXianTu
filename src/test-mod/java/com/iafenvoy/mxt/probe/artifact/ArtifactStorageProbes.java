package com.iafenvoy.mxt.probe.artifact;

import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.data.ability.type.StorageAbilityType;
import com.iafenvoy.mxt.data.storage.DataStorageHolder;
import com.iafenvoy.mxt.data.storage.builtin.ContainerDataStorage;
import com.iafenvoy.mxt.probe.Probe;
import com.iafenvoy.mxt.probe.ProbeContext;
import com.iafenvoy.mxt.probe.ProbeDefinitions;
import com.iafenvoy.mxt.registry.MxtDataComponents;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.artifact.ArtifactService;
import com.iafenvoy.mxt.runtime.artifact.ArtifactStorageService;
import com.iafenvoy.mxt.runtime.item.ItemStorageService;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * The state a carrier keeps for its abilities: the container one storage ability declares.
 */
public final class ArtifactStorageProbes {
    private ArtifactStorageProbes() {
    }

    public static List<Probe> probes() {
        return List.of(
                Probe.of("artifact/storage", ArtifactStorageProbes::storage));
    }

    // The container an ability declares is one entry of the carrier's own ability storage, addressed by that
    // ability's id: slot 1 of 27, the untouched slots left as holes.
    private static void storage(ProbeContext context) {
        RegistryAccess access = context.registries();
        ServerPlayer player = context.player();
        ItemStack jadeStack = new ItemStack(Items.AMETHYST_SHARD);
        ArtifactService.refine(jadeStack, player);
        Holder<Ability> storageAbility = ArtifactService.abilities(access, jadeStack).stream()
                .filter(ref -> ref.value().type() instanceof StorageAbilityType).findFirst().orElse(null);
        Holder<Ability> foreignStorage = context.require(MxtResourceKeys.ABILITY, ProbeDefinitions.PROBE_BOUND_STORAGE);
        boolean stored = storageAbility != null
                && ArtifactStorageService.INSTANCE.set(access, jadeStack, storageAbility, 1, new ItemStack(Items.STONE, 3), player)
                // A second write of the same kind replaces that ability's one entry instead of adding a second.
                && ArtifactStorageService.INSTANCE.set(access, jadeStack, storageAbility, 2, new ItemStack(Items.DIRT), player);
        DataStorageHolder jadeStorage = jadeStack.get(MxtDataComponents.STORAGE);
        boolean container = stored
                && jadeStorage != null && jadeStorage.count(ContainerDataStorage.class) == 1
                && ArtifactStorageService.INSTANCE.get(access, jadeStack, storageAbility, 1, player).is(Items.STONE)
                && ArtifactStorageService.INSTANCE.get(access, jadeStack, storageAbility, 1, player).getCount() == 3
                // The capacity is the definition's, so the slot one past it is not there.
                && ArtifactStorageService.INSTANCE.get(access, jadeStack, storageAbility, 27, player).isEmpty()
                && ItemStorageService.get(jadeStack, storageAbility, ContainerDataStorage.class)
                .filter(value -> value.contents().size() == 27 && value.get(0).isEmpty()
                        && value.get(1).is(Items.STONE) && value.get(2).is(Items.DIRT)).isPresent()
                // Another ability's id is a different slot: two storage abilities on one carrier never share a box.
                && ItemStorageService.get(jadeStack, foreignStorage, ContainerDataStorage.class).isEmpty();
        context.check("artifact roster container 27 slots, slot 1 = stone x3, slot 2 = dirt, one entry per (ability, kind)", container);
    }
}
