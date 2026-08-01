package com.iafenvoy.mxt.advancement;

import com.iafenvoy.mxt.advancement.IdentifierCriterionTrigger.Instance;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.advancements.criterion.ContextAwarePredicate;
import net.minecraft.advancements.criterion.EntityPredicate;
import net.minecraft.advancements.criterion.SimpleCriterionTrigger;
import net.minecraft.advancements.criterion.SimpleCriterionTrigger.SimpleInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NonNull;

import java.util.Optional;

/**
 * A reusable vanilla advancement trigger filtered by an optional MXT definition ID.
 */
public final class IdentifierCriterionTrigger extends SimpleCriterionTrigger<Instance> {
    @Override
    public @NonNull Codec<Instance> codec() {
        return Instance.CODEC;
    }

    public void trigger(ServerPlayer player, Identifier definition) {
        this.trigger(player, instance -> instance.definition().isEmpty() || instance.definition().get().equals(definition));
    }

    public record Instance(Optional<ContextAwarePredicate> player,
                           Optional<Identifier> definition) implements SimpleInstance {
        public static final Codec<Instance> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(Instance::player),
                Identifier.CODEC.optionalFieldOf("definition").forGetter(Instance::definition)
        ).apply(instance, Instance::new));
    }
}
