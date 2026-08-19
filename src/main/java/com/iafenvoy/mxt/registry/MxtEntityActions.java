package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.action.builtin.entity.*;
import com.iafenvoy.mxt.data.action.builtin.entity.meta.*;
import com.iafenvoy.mxt.integration.kubejs.type.action.JsEntityAction;
import com.mojang.serialization.MapCodec;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class MxtEntityActions {
    public static final DeferredRegister<MapCodec<? extends EntityAction>> REGISTRY = DeferredRegister.create(MxtRegistries.ENTITY_ACTION_TYPE, MiXianTu.MOD_ID);
    public static final DeferredHolder<MapCodec<? extends EntityAction>, MapCodec<NoOpAction>> NO_OP = REGISTRY.register("no_op", () -> NoOpAction.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityAction>, MapCodec<JsEntityAction>> JS = REGISTRY.register("js", () -> JsEntityAction.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityAction>, MapCodec<SequenceAction>> SEQUENCE = REGISTRY.register("sequence", () -> SequenceAction.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityAction>, MapCodec<HealAction>> HEAL = REGISTRY.register("heal", () -> HealAction.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityAction>, MapCodec<DamageAction>> DAMAGE = REGISTRY.register("damage", () -> DamageAction.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityAction>, MapCodec<AddResourceAction>> ADD_RESOURCE = REGISTRY.register("add_resource", () -> AddResourceAction.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityAction>, MapCodec<GrantAbilityAction>> GRANT_ABILITY = REGISTRY.register("grant_ability", () -> GrantAbilityAction.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityAction>, MapCodec<GrantSpiritRootAction>> GRANT_SPIRIT_ROOT = REGISTRY.register("grant_spirit_root", () -> GrantSpiritRootAction.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityAction>, MapCodec<RemoveAbilityAction>> REMOVE_ABILITY = REGISTRY.register("remove_ability", () -> RemoveAbilityAction.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityAction>, MapCodec<ApplyCurseAction>> APPLY_CURSE = REGISTRY.register("apply_curse", () -> ApplyCurseAction.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityAction>, MapCodec<ApplyCursesAction>> APPLY_CURSES = REGISTRY.register("apply_curses", () -> ApplyCursesAction.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityAction>, MapCodec<RemoveCurseAction>> REMOVE_CURSE = REGISTRY.register("remove_curse", () -> RemoveCurseAction.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityAction>, MapCodec<RemoveCursesByTagAction>> REMOVE_CURSES_BY_TAG = REGISTRY.register("remove_curses_by_tag", () -> RemoveCursesByTagAction.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityAction>, MapCodec<ApplyEffectAction>> APPLY_EFFECT = REGISTRY.register("apply_effect", () -> ApplyEffectAction.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityAction>, MapCodec<TeleportAction>> TELEPORT = REGISTRY.register("teleport", () -> TeleportAction.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityAction>, MapCodec<KnockbackAction>> KNOCKBACK = REGISTRY.register("knockback", () -> KnockbackAction.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityAction>, MapCodec<ModifyComponentAction>> MODIFY_COMPONENT = REGISTRY.register("modify_component", () -> ModifyComponentAction.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityAction>, MapCodec<SpawnEntityAction>> SPAWN_ENTITY = REGISTRY.register("spawn_entity", () -> SpawnEntityAction.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityAction>, MapCodec<SpawnProjectileAction>> SPAWN_PROJECTILE = REGISTRY.register("spawn_projectile", () -> SpawnProjectileAction.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityAction>, MapCodec<AddExperienceAction>> ADD_EXPERIENCE = REGISTRY.register("add_experience", () -> AddExperienceAction.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityAction>, MapCodec<AddVelocityAction>> ADD_VELOCITY = REGISTRY.register("add_velocity", () -> AddVelocityAction.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityAction>, MapCodec<ExhaustAction>> EXHAUST = REGISTRY.register("exhaust", () -> ExhaustAction.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityAction>, MapCodec<FeedAction>> FEED = REGISTRY.register("feed", () -> FeedAction.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityAction>, MapCodec<GainAirAction>> GAIN_AIR = REGISTRY.register("gain_air", () -> GainAirAction.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityAction>, MapCodec<GiveItemAction>> GIVE_ITEM = REGISTRY.register("give_item", () -> GiveItemAction.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityAction>, MapCodec<PlaySoundAction>> PLAY_SOUND = REGISTRY.register("play_sound", () -> PlaySoundAction.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityAction>, MapCodec<RemoveEffectAction>> REMOVE_EFFECT = REGISTRY.register("remove_effect", () -> RemoveEffectAction.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityAction>, MapCodec<SetFallDistanceAction>> SET_FALL_DISTANCE = REGISTRY.register("set_fall_distance", () -> SetFallDistanceAction.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityAction>, MapCodec<SetNoGravityAction>> SET_NO_GRAVITY = REGISTRY.register("set_no_gravity", () -> SetNoGravityAction.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityAction>, MapCodec<SetOnFireAction>> SET_ON_FIRE = REGISTRY.register("set_on_fire", () -> SetOnFireAction.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityAction>, MapCodec<SwingHandAction>> SWING_HAND = REGISTRY.register("swing_hand", () -> SwingHandAction.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityAction>, MapCodec<ChanceAction>> CHANCE = REGISTRY.register("chance", () -> ChanceAction.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityAction>, MapCodec<IfElseAction>> IF_ELSE = REGISTRY.register("if_else", () -> IfElseAction.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityAction>, MapCodec<ChoiceAction>> CHOICE = REGISTRY.register("choice", () -> ChoiceAction.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityAction>, MapCodec<EmitGameEventAction>> EMIT_GAME_EVENT = REGISTRY.register("emit_game_event", () -> EmitGameEventAction.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityAction>, MapCodec<PassengerAction>> PASSENGER_ACTION = REGISTRY.register("passenger_action", () -> PassengerAction.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityAction>, MapCodec<BlockActionAction>> BLOCK_ACTION = REGISTRY.register("block_action", () -> BlockActionAction.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityAction>, MapCodec<SelfBiEntityAction>> SELF_BIENTITY_ACTION = REGISTRY.register("self_bientity_action", () -> SelfBiEntityAction.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityAction>, MapCodec<EquippedItemAction>> EQUIPPED_ITEM_ACTION = REGISTRY.register("equipped_item_action", () -> EquippedItemAction.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityAction>, MapCodec<RidingAction>> RIDING_ACTION = REGISTRY.register("riding_action", () -> RidingAction.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityAction>, MapCodec<ExplodeAction>> EXPLODE = REGISTRY.register("explode", () -> ExplodeAction.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityAction>, MapCodec<SpawnParticlesAction>> SPAWN_PARTICLES = REGISTRY.register("spawn_particles", () -> SpawnParticlesAction.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityAction>, MapCodec<SpawnEffectCloudAction>> SPAWN_EFFECT_CLOUD = REGISTRY.register("spawn_effect_cloud", () -> SpawnEffectCloudAction.CODEC);

    private MxtEntityActions() {
    }
}
