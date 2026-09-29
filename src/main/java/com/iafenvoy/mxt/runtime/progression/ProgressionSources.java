package com.iafenvoy.mxt.runtime.progression;

import com.iafenvoy.mxt.attachment.CreatureSpiritAttachment;
import com.iafenvoy.mxt.attachment.SpiritIdentityAttachment;
import com.iafenvoy.mxt.data.progression.ProgressionOwner;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.util.HolderHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Where an owned progression comes from: one entry per system whose definitions own a chain. Adding a system is
 * implementing {@link ProgressionOwner} on its definition and adding one source here - nothing in the core, the
 * attachment or the condition has to know about it.
 */
public final class ProgressionSources {
    /**
     * One owner a body holds, together with the id its level is stored under.
     */
    public record Owner(Identifier id, ProgressionOwner definition) {
    }

    private static final List<Function<Entity, List<Owner>>> SOURCES = List.of(
            ProgressionSources::techniques,
            ProgressionSources::creatures);

    private ProgressionSources() {
    }

    public static List<Owner> heldBy(Entity entity) {
        List<Owner> owners = new ArrayList<>();
        for (Function<Entity, List<Owner>> source : SOURCES) owners.addAll(source.apply(entity));
        return owners;
    }

    // The one owner a body holds under that id, if it holds it at all: reads and administrative writes both ask
    // this rather than walking the sources themselves.
    public static Optional<Owner> held(Entity entity, Identifier owner) {
        return heldBy(entity).stream().filter(entry -> entry.id().equals(owner)).findFirst();
    }

    private static List<Owner> techniques(Entity entity) {
        SpiritIdentityAttachment spirit = entity.getExistingData(MxtAttachments.SPIRIT_IDENTITY).orElse(null);
        if (spirit == null) return List.of();
        return spirit.learnedTechniques().stream()
                .map(technique -> new Owner(HolderHelper.id(technique), technique.value()))
                .toList();
    }

    // A profile only counts as an owner once it names a chain: a creature profile that stays a pure stat block
    // keeps every creature exactly as it was before progressions existed.
    private static List<Owner> creatures(Entity entity) {
        CreatureSpiritAttachment spirit = entity.getExistingData(MxtAttachments.CREATURE_SPIRIT).orElse(null);
        if (spirit == null) return List.of();
        return spirit.profile()
                .filter(profile -> profile.value().entryLevel().isPresent())
                .map(profile -> List.of(new Owner(HolderHelper.id(profile), profile.value())))
                .orElse(List.of());
    }
}
