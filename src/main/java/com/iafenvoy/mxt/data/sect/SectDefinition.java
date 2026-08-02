package com.iafenvoy.mxt.data.sect;

import com.iafenvoy.mxt.data.resource.ResourceCost;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Optional;

/**
 * Datapack-defined sect policy; membership and contribution are runtime state.
 */
public record SectDefinition(List<Rank> ranks, List<Task> tasks,
                             List<Exchange> exchanges, List<Identifier> territoryPermissions) {
    public SectDefinition(List<Rank> ranks) {
        this(ranks, List.of(), List.of(), List.of());
    }

    public static final Codec<SectDefinition> CODEC = RecordCodecBuilder.<SectDefinition>create(instance -> instance.group(
            Rank.CODEC.listOf().fieldOf("ranks").forGetter(SectDefinition::ranks),
            Task.CODEC.listOf().optionalFieldOf("tasks", List.of()).forGetter(SectDefinition::tasks),
            Exchange.CODEC.listOf().optionalFieldOf("exchanges", List.of()).forGetter(SectDefinition::exchanges),
            Identifier.CODEC.listOf().optionalFieldOf("territory_permissions", List.of()).forGetter(SectDefinition::territoryPermissions)
    ).apply(instance, SectDefinition::new)).validate(SectDefinition::validate);

    private static DataResult<SectDefinition> validate(SectDefinition value) {
        if (value.ranks().isEmpty())
            return DataResult.error(() -> "Sect requires at least one rank");
        if (value.ranks().stream().map(Rank::id).distinct().count() != value.ranks().size())
            return DataResult.error(() -> "Sect rank IDs must be unique");
        if (value.tasks().stream().map(Task::id).distinct().count() != value.tasks().size())
            return DataResult.error(() -> "Sect task IDs must be unique");
        if (value.exchanges().stream().map(Exchange::id).distinct().count() != value.exchanges().size())
            return DataResult.error(() -> "Sect exchange IDs must be unique");
        return DataResult.success(value);
    }

    public record Rank(String id, int priority, int minContribution, List<Identifier> permissions,
                       List<ResourceCost> promotionCosts) {
        public static final Codec<Rank> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("id").forGetter(Rank::id), Codec.INT.optionalFieldOf("priority", 0).forGetter(Rank::priority),
                Codec.INT.optionalFieldOf("min_contribution", 0).forGetter(Rank::minContribution), Identifier.CODEC.listOf().optionalFieldOf("permissions", List.of()).forGetter(Rank::permissions),
                ResourceCost.LIST_CODEC.optionalFieldOf("promotion_costs", List.of()).forGetter(Rank::promotionCosts)
        ).apply(instance, Rank::new));
    }

    /**
     * A server-side completion hook may award a one-shot or repeatable contribution task.
     */
    public record Task(Identifier id, int contributionReward, boolean once, Optional<Identifier> requiredPermission) {
        public static final Codec<Task> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Identifier.CODEC.fieldOf("id").forGetter(Task::id), Codec.intRange(1, Integer.MAX_VALUE).fieldOf("contribution_reward").forGetter(Task::contributionReward),
                Codec.BOOL.optionalFieldOf("once", true).forGetter(Task::once), Identifier.CODEC.optionalFieldOf("required_permission").forGetter(Task::requiredPermission)
        ).apply(instance, Task::new));
    }

    /**
     * Contribution and resource payment are exchanged atomically for vanilla item outputs.
     */
    public record Exchange(Identifier id, int contributionCost, List<ResourceCost> costs, List<Identifier> outputs,
                           Optional<Identifier> requiredPermission) {
        public static final Codec<Exchange> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Identifier.CODEC.fieldOf("id").forGetter(Exchange::id), Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf("contribution_cost", 0).forGetter(Exchange::contributionCost),
                ResourceCost.LIST_CODEC.optionalFieldOf("costs", List.of()).forGetter(Exchange::costs), Identifier.CODEC.listOf().fieldOf("outputs").forGetter(Exchange::outputs),
                Identifier.CODEC.optionalFieldOf("required_permission").forGetter(Exchange::requiredPermission)
        ).apply(instance, Exchange::new));
    }
}
