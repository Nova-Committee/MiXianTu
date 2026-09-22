package com.iafenvoy.mxt.loot;

import com.iafenvoy.mxt.data.curse.CurseFilter;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootContext.EntityTarget;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import org.jspecify.annotations.NonNull;

public record HasCurseLootCondition(EntityTarget target, CurseFilter filter) implements LootItemCondition {
    public static final MapCodec<HasCurseLootCondition> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            EntityTarget.CODEC.optionalFieldOf("entity", EntityTarget.THIS).forGetter(HasCurseLootCondition::target),
            CurseFilter.MAP_CODEC.forGetter(HasCurseLootCondition::filter)
    ).apply(i, HasCurseLootCondition::new));

    @Override
    public @NonNull MapCodec<HasCurseLootCondition> codec() {
        return CODEC;
    }

    @Override
    public boolean test(LootContext context) {
        Entity entity = this.target.get(context);
        return entity != null && this.filter.test(entity, FormulaContext.of(entity));
    }
}
