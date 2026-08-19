package com.iafenvoy.mxt.loot;

import com.iafenvoy.mxt.data.curse.Curse;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootContext.EntityTarget;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import org.jspecify.annotations.NonNull;

public record HasCurseLootCondition(EntityTarget target, Holder<Curse> curse) implements LootItemCondition {
    public static final MapCodec<HasCurseLootCondition> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            EntityTarget.CODEC.optionalFieldOf("entity", EntityTarget.THIS).forGetter(HasCurseLootCondition::target),
            Curse.CODEC.fieldOf("curse").forGetter(HasCurseLootCondition::curse)
    ).apply(i, HasCurseLootCondition::new));

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
