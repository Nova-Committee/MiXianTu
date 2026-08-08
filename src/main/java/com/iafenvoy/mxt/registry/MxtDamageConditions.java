package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.condition.DamageCondition;
import com.iafenvoy.mxt.data.condition.builtin.damage.AlwaysTrueDamageCondition;
import com.iafenvoy.mxt.data.condition.builtin.damage.DamageAmountRangeCondition;
import com.iafenvoy.mxt.data.condition.builtin.damage.DirectDamageCondition;
import com.iafenvoy.mxt.data.condition.builtin.damage.DamageTypeCondition;
import com.iafenvoy.mxt.data.condition.builtin.damage.DamageTypeTagCondition;
import com.iafenvoy.mxt.data.condition.builtin.damage.FireDamageCondition;
import com.iafenvoy.mxt.data.condition.builtin.damage.MagicDamageCondition;
import com.iafenvoy.mxt.data.condition.builtin.damage.ProjectileDamageCondition;
import com.iafenvoy.mxt.data.condition.builtin.damage.meta.*;
import com.iafenvoy.mxt.integration.kubejs.type.condition.JsDamageCondition;
import com.mojang.serialization.MapCodec;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class MxtDamageConditions {
    public static final DeferredRegister<MapCodec<? extends DamageCondition>> REGISTRY = DeferredRegister.create(MxtTypeRegistries.DAMAGE_CONDITION_TYPE, MiXianTu.MOD_ID);
    public static final DeferredHolder<MapCodec<? extends DamageCondition>, MapCodec<AlwaysTrueDamageCondition>> ALWAYS_TRUE = REGISTRY.register("always_true", () -> AlwaysTrueDamageCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends DamageCondition>, MapCodec<JsDamageCondition>> JS = REGISTRY.register("js", () -> JsDamageCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends DamageCondition>, MapCodec<DamageAmountRangeCondition>> AMOUNT_RANGE = REGISTRY.register("amount_range", () -> DamageAmountRangeCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends DamageCondition>, MapCodec<DirectDamageCondition>> DIRECTNESS = REGISTRY.register("directness", () -> DirectDamageCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends DamageCondition>, MapCodec<DamageTypeCondition>> DAMAGE_TYPE = REGISTRY.register("damage_type", () -> DamageTypeCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends DamageCondition>, MapCodec<DamageTypeTagCondition>> DAMAGE_TYPE_TAG = REGISTRY.register("damage_type_tag", () -> DamageTypeTagCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends DamageCondition>, MapCodec<FireDamageCondition>> FIRE = REGISTRY.register("fire", () -> FireDamageCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends DamageCondition>, MapCodec<MagicDamageCondition>> MAGIC = REGISTRY.register("magic", () -> MagicDamageCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends DamageCondition>, MapCodec<ProjectileDamageCondition>> PROJECTILE = REGISTRY.register("projectile", () -> ProjectileDamageCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends DamageCondition>, MapCodec<AndDamageCondition>> AND = REGISTRY.register("and", () -> AndDamageCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends DamageCondition>, MapCodec<ChanceDamageCondition>> CHANCE = REGISTRY.register("chance", () -> ChanceDamageCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends DamageCondition>, MapCodec<ConstantDamageCondition>> CONSTANT = REGISTRY.register("constant", () -> ConstantDamageCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends DamageCondition>, MapCodec<NotDamageCondition>> NOT = REGISTRY.register("not", () -> NotDamageCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends DamageCondition>, MapCodec<OrDamageCondition>> OR = REGISTRY.register("or", () -> OrDamageCondition.CODEC);

    private MxtDamageConditions() {
    }
}
