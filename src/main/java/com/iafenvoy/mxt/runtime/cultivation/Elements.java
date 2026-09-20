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
 * The one place that answers two questions the rest of the mod must not answer for itself: whether an element
 * definition is live, and which elements an entity actually carries.
 *
 * <p>Every datapack registry in this mod can be disabled by tagging an entry with {@code mxt:disabled}, and an
 * element is no exception - a disabled element must stop holding relations, colouring text, satisfying a
 * spirit root's binding or matching an affinity. Nothing about that can be seen from a {@link Holder}: its
 * {@code value()} is the definition as written, disabled or not. Reading an element therefore goes through
 * {@link #enabled(Holder)} (a tag test, so it is the same answer on both sides) rather than through
 * {@code Holder#value()}.</p>
 *
 * <p>The second question is the spirit roots. They are read through the registry so that a root a pack
 * disabled contributes nothing - the same rule every other reader of the roots follows - and a root that
 * survives contributes its element only while that element is enabled too. A root the holder switched off with
 * the enable/disable module is not read at all: it is still held, but nothing about it applies. An entity with
 * no roots has no elements here, and callers read that as "no element relation applies" rather than as an
 * error: an ordinary mob is supposed to have neither.</p>
 */
public final class Elements {
    private Elements() {
    }

    /**
     * Whether this element definition takes part in runtime queries. A tag test on the holder, so callers on
     * either side get the same answer without reaching for a registry.
     */
    public static boolean enabled(Holder<Element> element) {
        return !MxtDatapackRegistries.isDisabled(MxtResourceKeys.ELEMENT, element);
    }

    public static boolean enabled(Optional<Holder<Element>> element) {
        return element.filter(Elements::enabled).isPresent();
    }

    /**
     * Whether an element matches a holder-or-tag list and is live. The list shape is the one every element
     * field in the mod uses ({@code element_affinity}, {@code preferred_aura_elements}, ...), so the
     * "disabled wins over matching" rule lives here once instead of at each field.
     */
    public static boolean matches(Collection<Either<Holder<Element>, TagKey<Element>>> elements, Holder<Element> candidate) {
        return enabled(candidate) && RegistryCodecs.matches(elements, candidate);
    }

    /**
     * The distinct live elements a spirit identity names, in no particular order, with the registry view the
     * caller has.
     *
     * <p>The registry is passed in rather than reached for, because this reading happens on both sides: a
     * condition can be asked from a client (an item tooltip evaluates them), where the only roots and roots
     * definitions available are the synchronised copies and the server-only accessor would throw. The level's
     * own {@code registryAccess()} answers on either side, so {@link #of(Entity)} uses it.</p>
     */
    public static Set<Holder<Element>> of(SpiritIdentityAttachment spirit, Provider access) {
        return spirit.activeSpiritRoots().stream()
                .flatMap(root -> MxtDatapackRegistries.get(access, MxtResourceKeys.SPIRIT_ROOT, root).stream())
                .map(SpiritRoot::element)
                .filter(Elements::enabled)
                .collect(Collectors.toUnmodifiableSet());
    }

    public static Set<Holder<Element>> of(Entity entity) {
        // Read, never create: this is asked of both sides of every strike, and an entity that carries no roots
        // must not come away holding an empty spirit identity (and, with it, a save entry) because something
        // merely asked what elements it has.
        SpiritIdentityAttachment spirit = entity.getExistingData(MxtAttachments.SPIRIT_IDENTITY).orElse(null);
        return spirit == null ? Set.of() : of(spirit, entity.level().registryAccess());
    }

    /**
     * The elements a holder-or-tag entry stands for, expanded against the registry: an entry is itself, a tag
     * is every element in it. A disabled element is not part of any expansion, so a pack can take an element
     * out of every query at once. A caller with no registry to ask gets an empty set, because a tag cannot be
     * expanded without one.
     */
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

    /**
     * Whether a set of declared elements meets a query written as an entry or a tag, asked in both directions:
     * a herb aligned with the tag "fire-like" answers a query for fire, and a herb aligned with fire answers a
     * query for that tag. Both sides are expanded to elements and intersected, which is the only reading that
     * does not depend on which side a pack happened to write the tag on.
     */
    public static boolean aligned(@Nullable Registry<Element> registry, Collection<Either<Holder<Element>, TagKey<Element>>> declared,
                                  Either<Holder<Element>, TagKey<Element>> query) {
        Set<Holder<Element>> wanted = expand(registry, query);
        if (wanted.isEmpty()) return false;
        return declared.stream().flatMap(entry -> expand(registry, entry).stream()).anyMatch(wanted::contains);
    }
}
