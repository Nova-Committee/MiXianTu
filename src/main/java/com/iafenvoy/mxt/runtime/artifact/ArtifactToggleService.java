package com.iafenvoy.mxt.runtime.artifact;

import com.iafenvoy.mxt.data.artifact.Artifact;
import com.iafenvoy.mxt.data.artifact.ability.ArtifactToggleContext;
import com.iafenvoy.mxt.data.artifact.ability.ToggableArtifactAbility;
import com.iafenvoy.mxt.util.HolderHelper;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The artifact capabilities one entity carries that need a key - every switch and every one-shot an artifact
 * declares - read the same way on both sides: the client draws them, the server re-reads them before honouring a
 * trigger, so "this cell is a capability the player has" means one thing.
 *
 * <p>Nothing here is stored. A capability is an entry of an artifact definition, and which artifacts an entity
 * carries is read from the stacks themselves - the same reading {@link ArtifactService} does for abilities - so
 * putting the artifact away takes its cells off the wheel in the same breath, exactly as it takes its skills.</p>
 */
public final class ArtifactToggleService {
    private ArtifactToggleService() {
    }

    /**
     * One capability a player carries: the ability, the stack that declares it and the definition behind both.
     */
    public record Toggle(LivingEntity holder, ItemStack stack, Holder<Artifact> artifact,
                         ToggableArtifactAbility ability) {
        /** The artifact and key this cell names, which is how the wheel tells two capabilities of one apart. */
        public ArtifactCapability capability() {
            return new ArtifactCapability(HolderHelper.id(this.artifact), this.ability.key());
        }

        /** The id the wheel addresses the cell by. */
        public Identifier id() {
            return this.capability().id();
        }

        public ArtifactToggleContext context() {
            return new ArtifactToggleContext(this.holder, this.stack, this.artifact);
        }

        /** The state it is in, when it has one; empty for a one-shot activation that leaves nothing on. */
        public Optional<Boolean> state() {
            return this.ability.state(this.context());
        }

        /** Presses it; the one call the wheel makes on the server. */
        public ToggableArtifactAbility.Result activate() {
            return this.ability.activate(this.context());
        }
    }

    /**
     * Every capability the given stacks declare, in id order and without duplicates: the same capability of the
     * same artifact in two slots is one cell, because the wheel names the artifact rather than the slot it happens
     * to be in.
     *
     * <p>The list is deliberately not cut to one page - how many entries fit is the framework's business, and all
     * the server asks of it is whether one id is in it.</p>
     */
    public static List<Toggle> of(LivingEntity holder, List<ItemStack> stacks) {
        Map<Identifier, Toggle> found = new LinkedHashMap<>();
        for (ItemStack stack : stacks) {
            if (stack.isEmpty()) continue;
            ArtifactService.definition(holder.level().registryAccess(), stack).ifPresent(artifact -> {
                for (ToggableArtifactAbility ability : artifact.value().toggables()) {
                    // The first slot that declares it wins, so the entry does not move while the artifact is in
                    // two places at once.
                    found.putIfAbsent(new ArtifactCapability(HolderHelper.id(artifact), ability.key()).id(),
                            new Toggle(holder, stack, artifact, ability));
                }
            });
        }
        return found.values().stream()
                .sorted(Comparator.comparing(toggle -> toggle.id().toString()))
                .toList();
    }

    /** One capability out of a list the caller has already read, which is how a trigger finds what it was sent. */
    public static Optional<Toggle> find(List<Toggle> toggles, Identifier id) {
        return toggles.stream().filter(toggle -> toggle.id().equals(id)).findFirst();
    }
}
