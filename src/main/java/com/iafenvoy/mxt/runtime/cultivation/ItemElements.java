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
 * Which elements one item stack carries. The item-side counterpart of {@link Elements}, which answers the same
 * question for an entity.
 *
 * <p>Two readings, and the first one that answers wins:</p>
 *
 * <ol>
 *   <li><b>What the stack's own definitions declare.</b> The {@code element} field of the {@code weapon_binding},
 *   {@code item_binding} or {@code artifact} that claims the stack, expanded through the element registry so a
 *   tag stands for every element it holds. Several definitions may contribute, and the union is the answer;
 *   within one registry the usual {@code ItemMatcher} reading applies, so the first match is the one that
 *   speaks.</li>
 *   <li><b>What the aura in it names.</b> Only when no definition declares anything: the single aura in the
 *   stack's {@code mxt:spirit_storage} component, or - for a store that is empty or names several - the aura
 *   its {@code mxt:item_aura} definition says it carries, and then that aura's {@code aura_type}. A store that
 *   holds nothing therefore reads as the aura it declares, which is what "a drained stone is empty of what it
 *   declares" means for this reading too. An artifact's {@code spirit_capacity} is deliberately not a source:
 *   it says what a stack can hold, not what it is.</li>
 * </ol>
 *
 * <p>An element a pack disabled contributes nothing, exactly as everywhere else - {@link Elements#expand} is
 * what expands the declarations, and the aura path filters through {@link Elements#enabled}.</p>
 *
 * <p>Nothing here is memoised: the reading walks the item-binding registries, so a caller in a hot path should
 * ask once per strike rather than once per element it is about to compare.</p>
 */
public final class ItemElements {
    private ItemElements() {
    }

    /**
     * The elements this stack carries, with the registry view the caller has.
     */
    public static Set<Holder<Element>> of(RegistryAccess access, ItemStack stack) {
        if (stack.isEmpty()) return Set.of();
        Set<Holder<Element>> declared = declared(access, stack);
        return declared.isEmpty() ? fromAura(access, stack) : declared;
    }

    /**
     * {@link #of(RegistryAccess, ItemStack)} against the running server's registries, for callers that hold only a
     * stack. A caller with no server (a client-side script) gets no element rather than an exception, the same
     * reading {@link ItemAuraService#type(ItemStack)} takes.
     */
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
        return element.filter(Elements::enabled).<Set<Holder<Element>>>map(Set::of).orElseGet(Set::of);
    }
}
