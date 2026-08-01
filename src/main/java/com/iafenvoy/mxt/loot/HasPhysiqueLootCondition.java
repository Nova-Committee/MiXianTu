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
 * Vanilla loot predicate for a physique, intentionally separate from elemental spirit roots.
 */
public record HasPhysiqueLootCondition(EntityTarget target,
                                       Identifier physique) implements LootItemCondition {
    public static final MapCodec<HasPhysiqueLootCondition> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            EntityTarget.CODEC.optionalFieldOf("entity", EntityTarget.THIS).forGetter(HasPhysiqueLootCondition::target),
            Identifier.CODEC.fieldOf("physique").forGetter(HasPhysiqueLootCondition::physique)
    ).apply(instance, HasPhysiqueLootCondition::new));

    @Override
    public @NonNull MapCodec<HasPhysiqueLootCondition> codec() {
        return CODEC;
    }

    @Override
    public boolean test(LootContext context) {
        Entity entity = this.target.get(context);
        return entity != null && entity.getData(MxtAttachments.SPIRIT_DATA).physiques().contains(this.physique);
    }
}
