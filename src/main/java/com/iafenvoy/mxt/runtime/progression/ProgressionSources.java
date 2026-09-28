package com.iafenvoy.mxt.runtime.progression;

import com.iafenvoy.mxt.attachment.SpiritIdentityAttachment;
import com.iafenvoy.mxt.data.progression.ProgressionOwner;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.util.HolderHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.List;
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
            ProgressionSources::techniques);

    private ProgressionSources() {
    }

    public static List<Owner> heldBy(Entity entity) {
        List<Owner> owners = new ArrayList<>();
        for (Function<Entity, List<Owner>> source : SOURCES) owners.addAll(source.apply(entity));
        return owners;
    }

    private static List<Owner> techniques(Entity entity) {
        SpiritIdentityAttachment spirit = entity.getExistingData(MxtAttachments.SPIRIT_IDENTITY).orElse(null);
        if (spirit == null) return List.of();
        return spirit.learnedTechniques().stream()
                .map(technique -> new Owner(HolderHelper.id(technique), technique.value()))
                .toList();
    }
}
