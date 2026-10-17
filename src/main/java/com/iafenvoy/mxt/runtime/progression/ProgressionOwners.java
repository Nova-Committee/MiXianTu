package com.iafenvoy.mxt.runtime.progression;

import com.iafenvoy.mxt.data.progression.ProgressionHolder;
import com.iafenvoy.mxt.data.progression.ProgressionOwner;
import com.iafenvoy.mxt.event.dispatcher.CollectProgressionOwnersEvent;
import com.iafenvoy.mxt.util.HolderHelper;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.common.NeoForge;

import java.util.List;
import java.util.Optional;

/**
 * The owners a body holds: one definition together with the holder that keeps its levels. The question itself is the
 * {@link CollectProgressionOwnersEvent}, so a system contributes by listening and this class knows none of them.
 */
public final class ProgressionOwners {
    private ProgressionOwners() {
    }

    // The holder is what makes the sort part of the address: a technique and a creature profile sharing an id are two
    // owners with a slot each.
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
        return NeoForge.EVENT_BUS.post(new CollectProgressionOwnersEvent(entity)).getOwners();
    }

    // The one owner a body holds under that id, if it holds it at all: reads and administrative writes both ask this
    // rather than walking the systems themselves. An id two sorts share is ambiguous here, so the caller that can
    // tell them apart asks its own system instead.
    public static Optional<Owner<?>> held(Entity entity, Identifier owner) {
        return heldBy(entity).stream().filter(entry -> entry.id().equals(owner)).findFirst();
    }
}
