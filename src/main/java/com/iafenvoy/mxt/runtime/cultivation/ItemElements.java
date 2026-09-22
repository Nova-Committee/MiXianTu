package com.iafenvoy.mxt.runtime.cultivation;

import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.aura.SpiritStorageComponent;
import com.iafenvoy.mxt.data.cultivation.Element;
import com.iafenvoy.mxt.registry.MxtDataComponents;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.artifact.ArtifactService;
import com.iafenvoy.mxt.runtime.item.ItemBindingService;
import com.mojang.datafixers.util.Either;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Which elements one item stack carries - the item-side counterpart of {@link Elements}, which answers the same
 * question for an entity. Two readings, and the first that answers wins: what the stack's own definitions
 * declare (the {@code element} field of the weapon binding, item binding or artifact claiming it, expanded so a
 * tag stands for every element it holds and unioned across the three), and failing that what the aura in it
 * names (the sole aura in its {@code mxt:spirit_storage}, or for a store that is empty or names several, the
 * {@code aura_type} of the aura its {@code mxt:item_aura} definition declares; an artifact's
 * {@code spirit_capacity} is deliberately not a source, since it says what a stack can hold, not what it is).
 * A disabled element contributes nothing, as everywhere else. Nothing is memoised: the reading walks the
 * item-binding registries, so a hot path asks once per strike rather than once per element compared.
 */
public final class ItemElements {
    private ItemElements() {
    }

    public static Set<Holder<Element>> of(RegistryAccess access, ItemStack stack) {
        if (stack.isEmpty()) return Set.of();
        Set<Holder<Element>> declared = declared(access, stack);
        return declared.isEmpty() ? fromAura(access, stack) : declared;
    }

    // A caller with no server (a client-side script) gets no element rather than an exception.
    public static Set<Holder<Element>> of(ItemStack stack) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        return server == null ? Set.of() : of(server.registryAccess(), stack);
    }

    private static Set<Holder<Element>> declared(RegistryAccess access, ItemStack stack) {
        Registry<Element> registry = access.lookupOrThrow(MxtResourceKeys.ELEMENT);
        Set<Holder<Element>> result = new LinkedHashSet<>();
        ItemBindingService.weapon(access, stack).ifPresent(binding -> collect(registry, binding.element(), result));
        ItemBindingService.binding(access, stack).ifPresent(binding -> collect(registry, binding.element(), result));
        ArtifactService.definition(access, stack)
                .ifPresent(artifact -> collect(registry, artifact.value().element(), result));
        return result.isEmpty() ? Set.of() : Set.copyOf(result);
    }

    private static void collect(Registry<Element> registry, List<Either<Holder<Element>, TagKey<Element>>> declared,
                                Set<Holder<Element>> into) {
        for (Either<Holder<Element>, TagKey<Element>> entry : declared) into.addAll(Elements.expand(registry, entry));
    }

    private static Set<Holder<Element>> fromAura(RegistryAccess access, ItemStack stack) {
        SpiritStorageComponent storage = stack.get(MxtDataComponents.SPIRIT_STORAGE);
        Optional<Holder<Aura>> aura = storage == null ? Optional.empty() : storage.soleAura();
        if (aura.isEmpty()) aura = ItemAuraService.type(access, stack);
        Optional<Holder<Element>> element = aura.flatMap(holder -> holder.value().auraType());
        return element.filter(Elements::enabled).map(Set::of).orElseGet(Set::of);
    }
}
