package com.iafenvoy.mxt.loot;
import com.iafenvoy.mxt.registry.MxtResourceKeys;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootContext.EntityTarget;
import net.minecraft.world.level.storage.loot.functions.LootItemConditionalFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import org.jspecify.annotations.NonNull;

import java.util.List;

/**
 * Grants a persistent ability source to the selected loot-context entity.
 */
public final class GrantAbilityLootFunction extends LootItemConditionalFunction {
    public static final MapCodec<GrantAbilityLootFunction> CODEC = RecordCodecBuilder.mapCodec(i -> commonFields(i).and(i.group(
            EntityTarget.CODEC.optionalFieldOf("entity", EntityTarget.THIS).forGetter(function -> function.target),
            Identifier.CODEC.fieldOf("ability").forGetter(function -> function.ability),
            Identifier.CODEC.optionalFieldOf("source", Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "loot")).forGetter(function -> function.source)
    )).apply(i, GrantAbilityLootFunction::new));
    private final EntityTarget target;
    private final Identifier ability;
    private final Identifier source;

    private GrantAbilityLootFunction(List<LootItemCondition> conditions, EntityTarget target, Identifier ability, Identifier source) {
        super(conditions);
        this.target = target;
        this.ability = ability;
        this.source = source;
    }

    @Override
    public @NonNull MapCodec<GrantAbilityLootFunction> codec() {
        return CODEC;
    }

    @Override
    public @NonNull ItemStack run(@NonNull ItemStack stack, @NonNull LootContext context) {
        Entity entity = this.target.get(context);
        if (entity != null) MxtDatapackRegistries.holder(MxtResourceKeys.ABILITY, this.ability)
                .ifPresent(ability -> entity.getData(MxtAttachments.ABILITY_HOLDER).grant(ability, this.source));
        return stack;
    }
}
