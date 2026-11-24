package com.iafenvoy.mxt.probe.artifact;
import com.iafenvoy.saturn.registry.SaturnResourceKeys;

import com.iafenvoy.saturn.data.ability.Ability;
import com.iafenvoy.mxt.data.ability.type.StorageAbilityType;
import com.iafenvoy.saturn.data.storage.DataStorageHolder;
import com.iafenvoy.saturn.data.storage.builtin.ContainerDataStorage;
import com.iafenvoy.mxt.probe.Probe;
import com.iafenvoy.mxt.probe.ProbeContext;
import com.iafenvoy.mxt.probe.ProbeDefinitions;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtDataComponents;
import com.iafenvoy.mxt.attachment.SharedStorageAttachment;
import com.iafenvoy.mxt.runtime.artifact.ArtifactStorageContainer;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
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
                Probe.of("artifact/storage", ArtifactStorageProbes::storage),
                Probe.of("artifact/storage_scopes", ArtifactStorageProbes::storageScopes));
    }

    // The container an ability declares is one entry of the carrier's own ability storage, addressed by that
    // ability's id: slot 1 of 27, the untouched slots left as holes.
    private static void storage(ProbeContext context) {
        RegistryAccess access = context.registries();
        ServerPlayer player = context.player();
        ItemStack jadeStack = new ItemStack(Items.AMETHYST_SHARD);
        ArtifactService.refine(jadeStack, player);
        Holder<Ability> storageAbility = ArtifactService.abilities(access, jadeStack).stream()
                .filter(ref -> ref.value().type() instanceof StorageAbilityType type && !type.shared()).findFirst().orElse(null);
        Holder<Ability> foreignStorage = context.require(SaturnResourceKeys.ABILITY, ProbeDefinitions.PROBE_BOUND_STORAGE);
        boolean stored = storageAbility != null
                && ArtifactStorageService.INSTANCE.set(access, jadeStack, storageAbility, 1, new ItemStack(Items.STONE, 3), player)
                // A second write of the same kind replaces that ability's one entry instead of adding a second.
                && ArtifactStorageService.INSTANCE.set(access, jadeStack, storageAbility, 2, new ItemStack(Items.DIRT), player);
        DataStorageHolder<Holder<Ability>> jadeStorage = jadeStack.get(MxtDataComponents.STORAGE);
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

    private static void storageScopes(ProbeContext context) {
        ServerPlayer player = context.player();
        ServerPlayer other = context.joinBeside(player, 2.0D, 0.0D);
        RegistryAccess access = context.registries();
        ItemStack first = new ItemStack(Items.AMETHYST_SHARD);
        ItemStack second = new ItemStack(Items.AMETHYST_SHARD);
        ItemStack third = new ItemStack(Items.AMETHYST_SHARD);
        ArtifactService.refine(first, player);
        ArtifactService.refine(second, player);
        ArtifactService.refine(third, other);
        player.getInventory().setItem(1, first);
        player.getInventory().setItem(2, second);
        Holder<Ability> independent = storageAbility(access, first, false);
        Holder<Ability> shared = storageAbility(access, first, true);
        ArtifactStorageService service = ArtifactStorageService.INSTANCE;

        service.set(access, second, independent, 0, new ItemStack(Items.DIRT, 2), player);
        ArtifactStorageContainer menu = new ArtifactStorageContainer(player, first, independent, 27);
        context.check("independent menu starts with the passed carrier's contents", menu.isEmpty());
        menu.setItem(0, new ItemStack(Items.STONE, 3));
        context.expect("first carrier holds three stones", service.get(access, first, independent, 0, player).getCount(), 3);
        context.check("second carrier retains its independent inventory", service.get(access, second, independent, 0, player).is(Items.DIRT));
        player.getInventory().setItem(1, ItemStack.EMPTY);
        context.check("removing the passed carrier invalidates its menu", !menu.stillValid(player));

        service.set(access, second, shared, 0, new ItemStack(Items.EMERALD, 5), player);
        context.expect("other eligible carrier of the same player shares inventory", service.get(access, first, shared, 0, player).getCount(), 5);
        context.check("a second player's inventory is separate", service.get(access, third, shared, 0, other).isEmpty());
        RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, access);
        Tag saved = SharedStorageAttachment.CODEC.codec().encodeStart(ops, player.getData(MxtAttachments.SHARED_STORAGE)).getOrThrow();
        SharedStorageAttachment loaded = SharedStorageAttachment.CODEC.codec().parse(ops, saved).getOrThrow();
        player.setData(MxtAttachments.SHARED_STORAGE, loaded);
        context.check("shared inventory persists independently of the carrier", service.get(access, second, shared, 0, player).is(Items.EMERALD));
    }

    private static Holder<Ability> storageAbility(RegistryAccess access, ItemStack carrier, boolean shared) {
        return ArtifactService.abilities(access, carrier).stream()
                .filter(ref -> ref.value().type() instanceof StorageAbilityType type && type.shared() == shared)
                .findFirst().orElseThrow();
    }
}
