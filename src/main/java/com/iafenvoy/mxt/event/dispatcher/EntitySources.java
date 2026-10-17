package com.iafenvoy.mxt.event.dispatcher;

import com.iafenvoy.mxt.data.AttributeEntry;
import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.data.progression.ProgressionHolder;
import com.iafenvoy.mxt.data.progression.ProgressionOwner;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.Sources;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.codec.RegistryCodecs;
import com.mojang.datafixers.util.Either;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.NeoForge;

import java.util.List;
import java.util.Optional;

/**
 * The answers a body's own sources give, and the queries over them. The questions are the {@code Collect*} events, so
 * a module contributes by listening and the service that applies the answers knows none of them.
 */
public final class EntitySources {
    private EntitySources() {
    }

    // One owner a body holds, together with the holder that keeps its levels. The holder is what makes the sort part
    // of the address: a technique and a creature profile sharing an id are two owners with a slot each.
    public record Owner<T extends ProgressionOwner>(Holder<T> holder, ProgressionHolder<T> levels) {
        public Identifier id() {
            return HolderHelper.id(this.holder);
        }

        public T definition() {
            return this.holder.value();
        }
    }

    // Every owner the body holds right now, from every listening system.
    public static List<Owner<?>> heldBy(Entity entity) {
        CollectProgressionOwnersEvent event = new CollectProgressionOwnersEvent(entity);
        NeoForge.EVENT_BUS.post(event);
        return event.owners();
    }

    // The one owner a body holds under that id, if it holds it at all: reads and administrative writes both ask this
    // rather than walking the systems themselves. An id two sorts share is ambiguous here, so the caller that can
    // tell them apart asks its own system instead.
    public static Optional<Owner<?>> held(Entity entity, Identifier owner) {
        return heldBy(entity).stream().filter(entry -> entry.id().equals(owner)).findFirst();
    }
}
