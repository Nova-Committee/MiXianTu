package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.condition.AlwaysTrueCondition;
import com.iafenvoy.mxt.data.condition.DamageCondition;
import com.iafenvoy.mxt.data.condition.builtin.damage.DamageAmountRangeCondition;
import com.iafenvoy.mxt.data.condition.builtin.damage.DirectDamageCondition;
import com.iafenvoy.mxt.data.condition.builtin.damage.DamageTypeCondition;
import com.iafenvoy.mxt.data.condition.builtin.damage.DamageTypeTagCondition;
import com.iafenvoy.mxt.data.condition.builtin.damage.ProjectileDamageCondition;
import com.iafenvoy.mxt.data.condition.builtin.damage.meta.*;
import com.iafenvoy.mxt.compat.kubejs.type.condition.JsDamageCondition;
import com.mojang.serialization.MapCodec;
import net.minecraft.tags.DamageTypeTags;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import static com.iafenvoy.mxt.data.condition.SimpleConditions.createDamage;

@SuppressWarnings("unused")
public final class MxtDamageConditions {
    public static final DeferredRegister<MapCodec<? extends DamageCondition>> REGISTRY = DeferredRegister.create(MxtRegistries.DAMAGE_CONDITION_TYPE, MiXianTu.MOD_ID);

    public static final DeferredHolder<MapCodec<? extends DamageCondition>, MapCodec<AlwaysTrueCondition>> ALWAYS_TRUE = REGISTRY.register("always_true", () -> AlwaysTrueCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends DamageCondition>, MapCodec<JsDamageCondition>> JS = REGISTRY.register("js", () -> JsDamageCondition.CODEC);

    public static final DeferredHolder<MapCodec<? extends DamageCondition>, MapCodec<DamageAmountRangeCondition>> AMOUNT_RANGE = REGISTRY.register("amount_range", () -> DamageAmountRangeCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends DamageCondition>, MapCodec<DirectDamageCondition>> DIRECTNESS = REGISTRY.register("directness", () -> DirectDamageCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends DamageCondition>, MapCodec<DamageTypeCondition>> DAMAGE_TYPE = REGISTRY.register("damage_type", () -> DamageTypeCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends DamageCondition>, MapCodec<DamageTypeTagCondition>> DAMAGE_TYPE_TAG = REGISTRY.register("damage_type_tag", () -> DamageTypeTagCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends DamageCondition>, MapCodec<? extends DamageCondition>> FIRE = REGISTRY.register("fire", () -> createDamage(ctx -> ctx.source().is(DamageTypeTags.IS_FIRE)));
    public static final DeferredHolder<MapCodec<? extends DamageCondition>, MapCodec<? extends DamageCondition>> MAGIC = REGISTRY.register("magic", () -> createDamage(ctx -> ctx.source().is(DamageTypeTags.AVOIDS_GUARDIAN_THORNS) && ctx.source().is(DamageTypeTags.WITCH_RESISTANT_TO)));
    public static final DeferredHolder<MapCodec<? extends DamageCondition>, MapCodec<ProjectileDamageCondition>> PROJECTILE = REGISTRY.register("projectile", () -> ProjectileDamageCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends DamageCondition>, MapCodec<AndDamageCondition>> AND = REGISTRY.register("and", () -> AndDamageCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends DamageCondition>, MapCodec<ChanceDamageCondition>> CHANCE = REGISTRY.register("chance", () -> ChanceDamageCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends DamageCondition>, MapCodec<ConstantDamageCondition>> CONSTANT = REGISTRY.register("constant", () -> ConstantDamageCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends DamageCondition>, MapCodec<NotDamageCondition>> NOT = REGISTRY.register("not", () -> NotDamageCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends DamageCondition>, MapCodec<OrDamageCondition>> OR = REGISTRY.register("or", () -> OrDamageCondition.CODEC);
}
