package com.iafenvoy.mxt.runtime;

import com.iafenvoy.mxt.data.AttributeEntry;
import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.data.progression.ProgressionHolder;
import com.iafenvoy.mxt.data.progression.ProgressionOwner;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.codec.RegistryCodecs;
import com.mojang.datafixers.util.Either;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * What a body's own sources contribute: the abilities its definitions grant, the passive attributes they declare and
 * the progressions they own. One module registers one object under whichever of the three it answers, and the service
 * that applies them knows only these contracts.
 */
public final class EntitySources {
    private EntitySources() {
    }

    // One granting system, asked once per rebuild. It states its whole contribution, so whatever it stops naming is
    // released. Each system reads the attachments of its own sort, since that is where its levels are kept.
    @FunctionalInterface
    public interface Grants {
        void collect(LivingEntity entity, GrantSink sink);
    }

    // One declaring system, asked once per reconcile or per tick.
    @FunctionalInterface
    public interface Attributes {
        void collect(LivingEntity entity, AttributeSink sink);
    }

    // One system whose definitions own a progression chain. A definition that declares no chain is not an owner, so
    // a system may name one only when its own rules say so.
    @FunctionalInterface
    public interface Owns {
        List<Owner<?>> held(Entity entity);
    }

    // What a granting system answers with: grants per source id. Several abilities may share one source.
    public interface GrantSink {
        void want(Identifier source, Holder<Ability> ability);

        // A definition's own list, which may name ids or tags. The access is the caller's because the caller is the
        // one that holds it: a grant is rebuilt from inside collect(), which already has the body being asked about.
        default void want(Identifier source, List<Either<Holder<Ability>, TagKey<Ability>>> values, Provider access) {
            RegistryCodecs.resolve(values, access, MxtResourceKeys.ABILITY)
                    .forEach(ability -> this.want(source, ability));
        }

        // A list already resolved by the owner, such as the cumulative unlocks of a reached level.
        default void wantResolved(Identifier source, List<Holder<Ability>> abilities) {
            abilities.forEach(ability -> this.want(source, ability));
        }
    }

    // What a declaring system answers with: the entries of one definition. The kind names the sort of thing that
    // declared them and the source the one definition; both are part of the generated modifier id.
    @FunctionalInterface
    public interface AttributeSink {
        void add(Sources.Declaration kind, Identifier source, List<AttributeEntry> values);
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

    // Every owner the body holds right now, from every registered system.
    public static List<Owner<?>> heldBy(Entity entity) {
        List<Owner<?>> owners = new ArrayList<>();
        for (Owns source : ModuleHooks.all(Owns.class)) owners.addAll(source.held(entity));
        return owners;
    }

    // The one owner a body holds under that id, if it holds it at all: reads and administrative writes both ask this
    // rather than walking the sources themselves. An id two sorts share is ambiguous here, so the caller that can
    // tell them apart asks its own system instead.
    public static Optional<Owner<?>> held(Entity entity, Identifier owner) {
        return heldBy(entity).stream().filter(entry -> entry.id().equals(owner)).findFirst();
    }
}
