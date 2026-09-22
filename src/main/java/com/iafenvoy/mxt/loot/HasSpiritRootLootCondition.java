package com.iafenvoy.mxt.loot;

import com.iafenvoy.mxt.data.cultivation.SpiritRoot;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.util.codec.RegistryCodecs;
import com.mojang.datafixers.util.Either;
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

// A disabled root is not held as far as this condition is concerned.
public record HasSpiritRootLootCondition(EntityTarget target,
                                         List<Either<Holder<SpiritRoot>, TagKey<SpiritRoot>>> spiritRoots) implements LootItemCondition {
    public static final MapCodec<HasSpiritRootLootCondition> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            EntityTarget.CODEC.optionalFieldOf("entity", EntityTarget.THIS).forGetter(HasSpiritRootLootCondition::target),
            RegistryCodecs.holderOrTagList(MxtResourceKeys.SPIRIT_ROOT).fieldOf("spirit_root").forGetter(HasSpiritRootLootCondition::spiritRoots)
    ).apply(i, HasSpiritRootLootCondition::new));

    @Override
    public @NonNull MapCodec<HasSpiritRootLootCondition> codec() {
        return CODEC;
    }

    @Override
    public boolean test(LootContext context) {
        Entity entity = this.target.get(context);
        return entity != null && entity.getData(MxtAttachments.SPIRIT_IDENTITY).spiritRoots().stream()
                .filter(held -> !MxtDatapackRegistries.isDisabled(MxtResourceKeys.SPIRIT_ROOT, held))
                .anyMatch(held -> RegistryCodecs.matches(this.spiritRoots, held));
    }
}
