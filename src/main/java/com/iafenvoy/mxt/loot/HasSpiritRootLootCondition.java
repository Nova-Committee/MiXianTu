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
 * Vanilla loot predicate for an entity's independent spirit-root collection.
 */
public record HasSpiritRootLootCondition(EntityTarget target,
                                         Identifier spiritRoot) implements LootItemCondition {
    public static final MapCodec<HasSpiritRootLootCondition> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            EntityTarget.CODEC.optionalFieldOf("entity", EntityTarget.THIS).forGetter(HasSpiritRootLootCondition::target),
            Identifier.CODEC.fieldOf("spirit_root").forGetter(HasSpiritRootLootCondition::spiritRoot)
    ).apply(instance, HasSpiritRootLootCondition::new));

    @Override
    public @NonNull MapCodec<HasSpiritRootLootCondition> codec() {
        return CODEC;
    }

    @Override
    public boolean test(LootContext context) {
        Entity entity = this.target.get(context);
        return entity != null && entity.getData(MxtAttachments.SPIRIT_DATA).spiritRoots().contains(this.spiritRoot);
    }
}
