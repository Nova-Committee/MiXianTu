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

public record HasCurseLootCondition(EntityTarget target, Identifier curse) implements LootItemCondition {
    public static final MapCodec<HasCurseLootCondition> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            EntityTarget.CODEC.optionalFieldOf("entity", EntityTarget.THIS).forGetter(HasCurseLootCondition::target),
            Identifier.CODEC.fieldOf("curse").forGetter(HasCurseLootCondition::curse)
    ).apply(instance, HasCurseLootCondition::new));

    @Override
    public @NonNull MapCodec<HasCurseLootCondition> codec() {
        return CODEC;
    }

    @Override
    public boolean test(LootContext context) {
        Entity entity = this.target.get(context);
        return entity != null && entity.getData(MxtAttachments.CURSE_HOLDER).instances().containsKey(this.curse);
    }
}
