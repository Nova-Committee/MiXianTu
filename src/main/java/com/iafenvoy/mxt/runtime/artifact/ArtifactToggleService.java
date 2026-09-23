package com.iafenvoy.mxt.runtime.artifact;

import com.iafenvoy.mxt.data.artifact.Artifact;
import com.iafenvoy.mxt.data.artifact.ability.ArtifactToggleContext;
import com.iafenvoy.mxt.data.artifact.ability.ToggableArtifactAbility;
import com.iafenvoy.mxt.util.HolderHelper;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.*;

/**
 * The artifact capabilities one entity carries that need a key, read the same way on both sides: the client draws
 * them, the server re-reads them before honouring a trigger.
 *
 * <p>Nothing is stored: a capability is an entry of a definition and which artifacts an entity carries is read
 * from the stacks themselves, so putting an artifact away takes its cells off the wheel exactly as it takes its
 * skills.
 */
public final class ArtifactToggleService {
    private ArtifactToggleService() {
    }

    public record Toggle(LivingEntity holder, ItemStack stack, Holder<Artifact> artifact,
                         ToggableArtifactAbility ability) {
        // The artifact and key this cell names, which is how the wheel tells two capabilities of one apart.
        public ArtifactCapability capability() {
            return new ArtifactCapability(HolderHelper.id(this.artifact), this.ability.key());
        }

        public Identifier id() {
            return this.capability().id();
        }

        public ArtifactToggleContext context() {
            return new ArtifactToggleContext(this.holder, this.stack, this.artifact);
        }

        // Empty for a one-shot activation that leaves nothing on.
        public Optional<Boolean> state() {
            return this.ability.state(this.context());
        }

        // The one call the wheel makes on the server.
        public ToggableArtifactAbility.Result activate() {
            return this.ability.activate(this.context());
        }
    }

    // In id order and without duplicates: the wheel names the artifact rather than the slot, so the same
    // capability of the same artifact in two slots is one cell. Deliberately not cut to one page - how many
    // entries fit is the framework's business, and all the server asks is whether one id is in it.
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

    // One capability out of a list the caller has already read, which is how a trigger finds what it was sent.
    public static Optional<Toggle> find(List<Toggle> toggles, Identifier id) {
        return toggles.stream().filter(toggle -> toggle.id().equals(id)).findFirst();
    }
}
