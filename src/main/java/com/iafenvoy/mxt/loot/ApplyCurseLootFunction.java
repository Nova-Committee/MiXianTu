package com.iafenvoy.mxt.loot;
import com.iafenvoy.mxt.registry.MxtResourceKeys;

import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.runtime.curse.CurseService;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.Codec;
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
 * Vanilla loot integration for a server-validated curse application.
 */
public final class ApplyCurseLootFunction extends LootItemConditionalFunction {
    public static final MapCodec<ApplyCurseLootFunction> CODEC = RecordCodecBuilder.mapCodec(i -> commonFields(i).and(i.group(
            EntityTarget.CODEC.optionalFieldOf("entity", EntityTarget.THIS).forGetter(function -> function.target),
            Identifier.CODEC.fieldOf("curse").forGetter(function -> function.curse),
            Codec.intRange(1, 256).optionalFieldOf("stacks", 1).forGetter(function -> function.stacks)
    )).apply(i, ApplyCurseLootFunction::new));
    private final EntityTarget target;
    private final Identifier curse;
    private final int stacks;

    private ApplyCurseLootFunction(List<LootItemCondition> conditions, EntityTarget target, Identifier curse, int stacks) {
        super(conditions);
        this.target = target;
        this.curse = curse;
        this.stacks = stacks;
    }

    @Override
    public @NonNull MapCodec<ApplyCurseLootFunction> codec() {
        return CODEC;
    }

    @Override
    public @NonNull ItemStack run(@NonNull ItemStack stack, @NonNull LootContext context) {
        Entity entity = this.target.get(context);
        if (entity != null)
            MxtDatapackRegistries.holder(MxtResourceKeys.CURSE, this.curse).ifPresent(curse ->
                    CurseService.apply(entity, curse, this.stacks, entity.level().getGameTime(), FormulaContext.of(entity), "loot"));
        return stack;
    }
}
