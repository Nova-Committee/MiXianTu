package com.iafenvoy.mxt.runtime.cultivation;

import com.iafenvoy.mxt.attachment.SpiritIdentityAttachment;
import com.iafenvoy.mxt.data.cultivation.Element;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.mojang.datafixers.util.Either;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The one place that answers which elements an entity carries, so nothing else answers for itself. A definition
 * that did not load cannot be held in the first place, so an element a field names always counts; roots are read
 * through the registry and contribute their elements only while the root is switched on, and an entity with no
 * roots has no elements, which callers read as "no element relation applies" rather than as an error.
 */
public final class Elements {
    private Elements() {
    }

    // A root that is switched on contributes the elements it names; an entity with no roots has no elements, which
    // callers read as "no element relation applies" rather than as an error.
    public static Set<Holder<Element>> of(SpiritIdentityAttachment spirit) {
        return spirit.activeSpiritRoots().stream()
                .flatMap(root -> root.value().elementHolders().stream())
                .collect(Collectors.toUnmodifiableSet());
    }

    public static Set<Holder<Element>> of(Entity entity) {
        // Read, never create: an entity that carries no roots must not come away holding an empty spirit
        // identity (and, with it, a save entry) because something merely asked what elements it has.
        SpiritIdentityAttachment spirit = entity.getExistingData(MxtAttachments.SPIRIT_IDENTITY).orElse(null);
        return spirit == null ? Set.of() : of(spirit);
    }

    // A caller with no registry gets an empty set, because a tag cannot be expanded without one.
    public static Set<Holder<Element>> expand(@Nullable Registry<Element> registry, Either<Holder<Element>, TagKey<Element>> entry) {
        if (entry.left().isPresent()) return Set.of(entry.left().orElseThrow());
        if (registry == null) return Set.of();
        TagKey<Element> tag = entry.right().orElseThrow();
        return registry.listElements().filter(holder -> holder.is(tag)).collect(Collectors.toUnmodifiableSet());
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
