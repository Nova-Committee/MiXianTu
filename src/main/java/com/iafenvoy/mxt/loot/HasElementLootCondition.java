package com.iafenvoy.mxt.loot;

import com.iafenvoy.mxt.data.cultivation.Element;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.cultivation.Elements;
import com.iafenvoy.mxt.util.codec.RegistryCodecs;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootContext.EntityTarget;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import org.jspecify.annotations.NonNull;

import java.util.List;

// Coarser than HasSpiritRootLootCondition: asks whether any element the entity's roots name is listed.
public record HasElementLootCondition(EntityTarget target,
                                      List<Either<Holder<Element>, TagKey<Element>>> elements) implements LootItemCondition {
    public static final MapCodec<HasElementLootCondition> CODEC = RecordCodecBuilder.<HasElementLootCondition>mapCodec(i -> i.group(
            EntityTarget.CODEC.optionalFieldOf("entity", EntityTarget.THIS).forGetter(HasElementLootCondition::target),
            RegistryCodecs.holderOrTagList(MxtResourceKeys.ELEMENT).fieldOf("elements").forGetter(HasElementLootCondition::elements)
    ).apply(i, HasElementLootCondition::new)).validate(HasElementLootCondition::validate);

    // An empty list can never match, so it is silently always-false; refuse it at load.
    private static DataResult<HasElementLootCondition> validate(HasElementLootCondition condition) {
        return condition.elements().isEmpty()
                ? DataResult.error(() -> "mxt:has_element needs at least one element to ask about")
                : DataResult.success(condition);
    }

    @Override
    public @NonNull MapCodec<HasElementLootCondition> codec() {
        return CODEC;
    }

    @Override
    public boolean test(LootContext context) {
        Entity entity = this.target.get(context);
        return entity != null && Elements.of(entity).stream()
                .anyMatch(element -> RegistryCodecs.matches(this.elements, element));
    }
}
