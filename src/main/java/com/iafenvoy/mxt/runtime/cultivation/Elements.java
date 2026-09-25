package com.iafenvoy.mxt.runtime.cultivation;

import com.iafenvoy.mxt.attachment.SpiritIdentityAttachment;
import com.iafenvoy.mxt.data.cultivation.Element;
import com.iafenvoy.mxt.data.cultivation.SpiritRoot;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.util.codec.RegistryCodecs;
import com.mojang.datafixers.util.Either;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.core.Registry;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The one place that answers whether an element definition is live and which elements an entity carries, so
 * nothing else answers either for itself. A disabled element ({@code mxt:disabled}) must stop holding
 * relations, colouring text, satisfying a spirit root's binding and matching an affinity, and a
 * {@link Holder} cannot show that - its {@code value()} is the definition as written - so every read goes
 * through {@link #enabled(Holder)}. Roots are read through the registry and contribute their elements only
 * while the root is switched on and that element is enabled; an entity with no roots has no elements, which
 * callers read as "no element relation applies" rather than as an error.
 */
public final class Elements {
    private Elements() {
    }

    public static boolean enabled(Holder<Element> element) {
        return !MxtDatapackRegistries.isDisabled(MxtResourceKeys.ELEMENT, element);
    }

    public static boolean enabled(Optional<Holder<Element>> element) {
        return element.filter(Elements::enabled).isPresent();
    }

    // The holder-or-tag list shape every element field in the mod uses, so "disabled wins over matching"
    // lives here once instead of at each field.
    public static boolean matches(Collection<Either<Holder<Element>, TagKey<Element>>> elements, Holder<Element> candidate) {
        return enabled(candidate) && RegistryCodecs.matches(elements, candidate);
    }

    // The registry is passed in rather than reached for: this is asked on both sides, and a client (an item
    // tooltip evaluates conditions) has only the synchronised copies, so the server-only accessor would throw.
    public static Set<Holder<Element>> of(SpiritIdentityAttachment spirit, Provider access) {
        return spirit.activeSpiritRoots().stream()
                .flatMap(root -> MxtDatapackRegistries.get(access, MxtResourceKeys.SPIRIT_ROOT, root).stream())
                .flatMap(root -> root.elementHolders().stream())
                .filter(Elements::enabled)
                .collect(Collectors.toUnmodifiableSet());
    }

    public static Set<Holder<Element>> of(Entity entity) {
        // Read, never create: an entity that carries no roots must not come away holding an empty spirit
        // identity (and, with it, a save entry) because something merely asked what elements it has.
        SpiritIdentityAttachment spirit = entity.getExistingData(MxtAttachments.SPIRIT_IDENTITY).orElse(null);
        return spirit == null ? Set.of() : of(spirit, entity.level().registryAccess());
    }

    // A disabled element is part of no expansion, so a pack can take an element out of every query at once. A
    // caller with no registry gets an empty set, because a tag cannot be expanded without one.
    public static Set<Holder<Element>> expand(@Nullable Registry<Element> registry, Either<Holder<Element>, TagKey<Element>> entry) {
        if (entry.left().isPresent()) {
            Holder<Element> element = entry.left().orElseThrow();
            return enabled(element) ? Set.of(element) : Set.of();
        }
        if (registry == null) return Set.of();
        TagKey<Element> tag = entry.right().orElseThrow();
        return registry.listElements()
                .filter(holder -> holder.is(tag) && enabled(holder))
                .collect(Collectors.toUnmodifiableSet());
    }

    // Asked in both directions - a herb aligned with the tag "fire-like" answers a query for fire and vice
    // versa - because expanding both sides and intersecting does not care which side a pack wrote the tag on.
    public static boolean aligned(@Nullable Registry<Element> registry, Collection<Either<Holder<Element>, TagKey<Element>>> declared,
                                  Either<Holder<Element>, TagKey<Element>> query) {
        Set<Holder<Element>> wanted = expand(registry, query);
        if (wanted.isEmpty()) return false;
        return declared.stream().flatMap(entry -> expand(registry, entry).stream()).anyMatch(wanted::contains);
    }
}
