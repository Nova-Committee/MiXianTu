package com.iafenvoy.mxt.loot;

import com.iafenvoy.mxt.data.cultivation.RealmStage;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootContext.EntityTarget;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import org.jspecify.annotations.NonNull;

public record RealmLootCondition(EntityTarget target, Holder<RealmStage> realm) implements LootItemCondition {
    public static final MapCodec<RealmLootCondition> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            EntityTarget.CODEC.optionalFieldOf("entity", EntityTarget.THIS).forGetter(RealmLootCondition::target),
            RealmStage.CODEC.fieldOf("realm").forGetter(RealmLootCondition::realm)
    ).apply(instance, RealmLootCondition::new));

    @Override
    public @NonNull MapCodec<RealmLootCondition> codec() {
        return CODEC;
    }

    @Override
    public boolean test(LootContext context) {
        Entity entity = this.target.get(context);
        return entity != null && entity.getData(MxtAttachments.SPIRIT_DATA).realmStage().filter(this.realm::equals).isPresent();
    }
}
