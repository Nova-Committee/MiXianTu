package com.iafenvoy.mxt.loot;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.curse.Curse;
import com.iafenvoy.mxt.runtime.curse.CurseService;
import com.iafenvoy.saturn.util.formula.FormulaContext;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootContext.EntityTarget;
import net.minecraft.world.level.storage.loot.functions.LootItemConditionalFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import org.jspecify.annotations.NonNull;

import java.util.List;

public final class ApplyCurseLootFunction extends LootItemConditionalFunction {
    public static final MapCodec<ApplyCurseLootFunction> CODEC = RecordCodecBuilder.mapCodec(i -> commonFields(i).and(i.group(
            EntityTarget.CODEC.optionalFieldOf("entity", EntityTarget.THIS).forGetter(function -> function.target),
            Curse.CODEC.fieldOf("curse").forGetter(function -> function.curse),
            Codec.intRange(1, 256).optionalFieldOf("stacks", 1).forGetter(function -> function.stacks)
    )).apply(i, ApplyCurseLootFunction::new));
    private static final Identifier SOURCE = Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "loot");
    private final EntityTarget target;
    private final Holder<Curse> curse;
    private final int stacks;

    private ApplyCurseLootFunction(List<LootItemCondition> conditions, EntityTarget target, Holder<Curse> curse, int stacks) {
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
            CurseService.apply(entity, this.curse, this.stacks, entity.level().getGameTime(), FormulaContext.of(entity), SOURCE);
        return stack;
    }
}
