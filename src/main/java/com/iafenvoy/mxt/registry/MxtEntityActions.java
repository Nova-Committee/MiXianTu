package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.action.builtin.*;
import com.mojang.serialization.MapCodec;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class MxtEntityActions {
    public static final DeferredRegister<MapCodec<? extends EntityAction>> REGISTRY = DeferredRegister.create(MxtTypeRegistries.ENTITY_ACTION_TYPE, MiXianTu.MOD_ID);
    public static final DeferredHolder<MapCodec<? extends EntityAction>, MapCodec<NoOpEntityAction>> NO_OP = REGISTRY.register("no_op", () -> NoOpEntityAction.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityAction>, MapCodec<SequenceEntityAction>> SEQUENCE = REGISTRY.register("sequence", () -> SequenceEntityAction.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityAction>, MapCodec<HealEntityAction>> HEAL = REGISTRY.register("heal", () -> HealEntityAction.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityAction>, MapCodec<DamageEntityAction>> DAMAGE = REGISTRY.register("damage", () -> DamageEntityAction.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityAction>, MapCodec<AddResourceEntityAction>> ADD_RESOURCE = REGISTRY.register("add_resource", () -> AddResourceEntityAction.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityAction>, MapCodec<GrantAbilityEntityAction>> GRANT_ABILITY = REGISTRY.register("grant_ability", () -> GrantAbilityEntityAction.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityAction>, MapCodec<RemoveAbilityEntityAction>> REMOVE_ABILITY = REGISTRY.register("remove_ability", () -> RemoveAbilityEntityAction.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityAction>, MapCodec<ApplyCurseEntityAction>> APPLY_CURSE = REGISTRY.register("apply_curse", () -> ApplyCurseEntityAction.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityAction>, MapCodec<ApplyCursesEntityAction>> APPLY_CURSES = REGISTRY.register("apply_curses", () -> ApplyCursesEntityAction.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityAction>, MapCodec<RemoveCurseEntityAction>> REMOVE_CURSE = REGISTRY.register("remove_curse", () -> RemoveCurseEntityAction.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityAction>, MapCodec<RemoveCursesByTagEntityAction>> REMOVE_CURSES_BY_TAG = REGISTRY.register("remove_curses_by_tag", () -> RemoveCursesByTagEntityAction.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityAction>, MapCodec<ApplyEffectEntityAction>> APPLY_EFFECT = REGISTRY.register("apply_effect", () -> ApplyEffectEntityAction.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityAction>, MapCodec<TeleportEntityAction>> TELEPORT = REGISTRY.register("teleport", () -> TeleportEntityAction.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityAction>, MapCodec<KnockbackEntityAction>> KNOCKBACK = REGISTRY.register("knockback", () -> KnockbackEntityAction.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityAction>, MapCodec<ModifyComponentEntityAction>> MODIFY_COMPONENT = REGISTRY.register("modify_component", () -> ModifyComponentEntityAction.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityAction>, MapCodec<SpawnEntityAction>> SPAWN_ENTITY = REGISTRY.register("spawn_entity", () -> SpawnEntityAction.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityAction>, MapCodec<SpawnProjectileEntityAction>> SPAWN_PROJECTILE = REGISTRY.register("spawn_projectile", () -> SpawnProjectileEntityAction.CODEC);

    private MxtEntityActions() {
    }
}
