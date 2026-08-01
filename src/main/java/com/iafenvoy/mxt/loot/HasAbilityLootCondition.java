package com.iafenvoy.mxt.loot;

import com.iafenvoy.mxt.registry.MxtAttachments;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootContext.EntityTarget;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import org.jspecify.annotations.NonNull;

/**
 * Tests an entity's granted ability attachment in a vanilla loot predicate.
 */
public record HasAbilityLootCondition(EntityTarget target,
                                      Identifier ability) implements LootItemCondition {
    public static final MapCodec<HasAbilityLootCondition> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            EntityTarget.CODEC.optionalFieldOf("entity", EntityTarget.THIS).forGetter(HasAbilityLootCondition::target),
            Identifier.CODEC.fieldOf("ability").forGetter(HasAbilityLootCondition::ability)
    ).apply(instance, HasAbilityLootCondition::new));

    @Override
    public @NonNull MapCodec<HasAbilityLootCondition> codec() {
        return CODEC;
    }

    @Override
    public boolean test(LootContext context) {
        Entity entity = this.target.get(context);
        return entity != null && entity.getData(MxtAttachments.ABILITY_HOLDER).has(this.ability);
    }
}
