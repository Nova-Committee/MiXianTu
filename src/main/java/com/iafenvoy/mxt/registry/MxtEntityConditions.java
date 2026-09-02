package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.condition.AlwaysTrueCondition;
import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.condition.builtin.entity.*;
import com.iafenvoy.mxt.data.condition.builtin.entity.meta.*;
import com.iafenvoy.mxt.compat.kubejs.type.condition.JsEntityCondition;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import static com.iafenvoy.mxt.data.condition.SimpleConditions.createEntity;

@SuppressWarnings("unused")
public final class MxtEntityConditions {
    public static final DeferredRegister<MapCodec<? extends EntityCondition>> REGISTRY = DeferredRegister.create(MxtRegistries.ENTITY_CONDITION_TYPE, MiXianTu.MOD_ID);

    public static final DeferredHolder<MapCodec<? extends EntityCondition>, MapCodec<AlwaysTrueCondition>> ALWAYS_TRUE = REGISTRY.register("always_true", () -> AlwaysTrueCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityCondition>, MapCodec<JsEntityCondition>> JS = REGISTRY.register("js", () -> JsEntityCondition.CODEC);

    public static final DeferredHolder<MapCodec<? extends EntityCondition>, MapCodec<NeverEntityCondition>> NEVER = REGISTRY.register("never", () -> NeverEntityCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityCondition>, MapCodec<AndEntityCondition>> AND = REGISTRY.register("and", () -> AndEntityCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityCondition>, MapCodec<NotEntityCondition>> NOT = REGISTRY.register("not", () -> NotEntityCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityCondition>, MapCodec<OrEntityCondition>> OR = REGISTRY.register("or", () -> OrEntityCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityCondition>, MapCodec<? extends EntityCondition>> SNEAKING = REGISTRY.register("sneaking", () -> createEntity(ctx -> ctx.entity().isShiftKeyDown()));
    public static final DeferredHolder<MapCodec<? extends EntityCondition>, MapCodec<HasAbilityEntityCondition>> HAS_ABILITY = REGISTRY.register("has_ability", () -> HasAbilityEntityCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityCondition>, MapCodec<HasCurseEntityCondition>> HAS_CURSE = REGISTRY.register("has_curse", () -> HasCurseEntityCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityCondition>, MapCodec<RealmEntityCondition>> REALM = REGISTRY.register("realm", () -> RealmEntityCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityCondition>, MapCodec<HasRealmEntityCondition>> HAS_REALM = REGISTRY.register("has_realm", () -> HasRealmEntityCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityCondition>, MapCodec<AuraRangeEntityCondition>> AURA_RANGE = REGISTRY.register("aura_range", () -> AuraRangeEntityCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityCondition>, MapCodec<ResourceCompareEntityCondition>> RESOURCE_COMPARE = REGISTRY.register("resource_compare", () -> ResourceCompareEntityCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityCondition>, MapCodec<EntityTypeTagCondition>> ENTITY_TAG = REGISTRY.register("entity_tag", () -> EntityTypeTagCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityCondition>, MapCodec<FormationMemberEntityCondition>> FORMATION_MEMBER = REGISTRY.register("formation_member", () -> FormationMemberEntityCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityCondition>, MapCodec<AirCondition>> AIR = REGISTRY.register("air", () -> AirCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityCondition>, MapCodec<DimensionCondition>> DIMENSION = REGISTRY.register("dimension", () -> DimensionCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityCondition>, MapCodec<EntityTypeCondition>> ENTITY_TYPE = REGISTRY.register("entity_type", () -> EntityTypeCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityCondition>, MapCodec<FallDistanceCondition>> FALL_DISTANCE = REGISTRY.register("fall_distance", () -> FallDistanceCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityCondition>, MapCodec<? extends EntityCondition>> GLOWING = REGISTRY.register("glowing", () -> createEntity(ctx -> ctx.entity().isCurrentlyGlowing()));
    public static final DeferredHolder<MapCodec<? extends EntityCondition>, MapCodec<HealthCondition>> HEALTH = REGISTRY.register("health", () -> HealthCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityCondition>, MapCodec<? extends EntityCondition>> EXPOSED_TO_SKY = REGISTRY.register("exposed_to_sky", () -> createEntity(ctx -> ctx.entity().level().canSeeSky(ctx.entity().blockPosition())));
    public static final DeferredHolder<MapCodec<? extends EntityCondition>, MapCodec<FoodLevelCondition>> FOOD_LEVEL = REGISTRY.register("food_level", () -> FoodLevelCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityCondition>, MapCodec<MobEffectCondition>> MOB_EFFECT = REGISTRY.register("mob_effect", () -> MobEffectCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityCondition>, MapCodec<OnBlockCondition>> ON_BLOCK = REGISTRY.register("on_block", () -> OnBlockCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityCondition>, MapCodec<TimeOfDayCondition>> TIME_OF_DAY = REGISTRY.register("time_of_day", () -> TimeOfDayCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityCondition>, MapCodec<? extends EntityCondition>> USING_ITEM = REGISTRY.register("using_item", () -> createEntity(ctx -> ctx.entity() instanceof LivingEntity living && living.isUsingItem()));
    public static final DeferredHolder<MapCodec<? extends EntityCondition>, MapCodec<BrightnessCondition>> BRIGHTNESS = REGISTRY.register("brightness", () -> BrightnessCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityCondition>, MapCodec<ExposedToSunCondition>> EXPOSED_TO_SUN = REGISTRY.register("exposed_to_sun", () -> ExposedToSunCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityCondition>, MapCodec<ExperienceLevelCondition>> EXPERIENCE_LEVEL = REGISTRY.register("experience_level", () -> ExperienceLevelCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityCondition>, MapCodec<ExperiencePointsCondition>> EXPERIENCE_POINTS = REGISTRY.register("experience_points", () -> ExperiencePointsCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityCondition>, MapCodec<RelativeHealthCondition>> RELATIVE_HEALTH = REGISTRY.register("relative_health", () -> RelativeHealthCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityCondition>, MapCodec<SaturationLevelCondition>> SATURATION_LEVEL = REGISTRY.register("saturation_level", () -> SaturationLevelCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityCondition>, MapCodec<TeamCondition>> TEAM = REGISTRY.register("team", () -> TeamCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityCondition>, MapCodec<ChanceCondition>> CHANCE = REGISTRY.register("chance", () -> ChanceCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityCondition>, MapCodec<ConstantCondition>> CONSTANT = REGISTRY.register("constant", () -> ConstantCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityCondition>, MapCodec<AttributeCondition>> ATTRIBUTE = REGISTRY.register("attribute", () -> AttributeCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityCondition>, MapCodec<BlockCollisionCondition>> BLOCK_COLLISION = REGISTRY.register("block_collision", () -> BlockCollisionCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityCondition>, MapCodec<CanHaveEffectCondition>> CAN_HAVE_EFFECT = REGISTRY.register("can_have_effect", () -> CanHaveEffectCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityCondition>, MapCodec<GamemodeCondition>> GAMEMODE = REGISTRY.register("gamemode", () -> GamemodeCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityCondition>, MapCodec<PassengerCondition>> PASSENGER = REGISTRY.register("passenger", () -> PassengerCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityCondition>, MapCodec<AttackCooldownCondition>> ATTACK_COOLDOWN = REGISTRY.register("attack_cooldown", () -> AttackCooldownCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityCondition>, MapCodec<EquippedItemCondition>> EQUIPPED_ITEM = REGISTRY.register("equipped_item", () -> EquippedItemCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityCondition>, MapCodec<InBlockCondition>> IN_BLOCK = REGISTRY.register("in_block", () -> InBlockCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityCondition>, MapCodec<InBlockAnywhereCondition>> IN_BLOCK_ANYWHERE = REGISTRY.register("in_block_anywhere", () -> InBlockAnywhereCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityCondition>, MapCodec<ScoreboardCondition>> SCOREBOARD = REGISTRY.register("scoreboard", () -> ScoreboardCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityCondition>, MapCodec<RidingCondition>> RIDING = REGISTRY.register("riding", () -> RidingCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityCondition>, MapCodec<RidingRecursiveCondition>> RIDING_RECURSIVE = REGISTRY.register("riding_recursive", () -> RidingRecursiveCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityCondition>, MapCodec<PassengerRecursiveCondition>> PASSENGER_RECURSIVE = REGISTRY.register("passenger_recursive", () -> PassengerRecursiveCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityCondition>, MapCodec<RidingRootCondition>> RIDING_ROOT = REGISTRY.register("riding_root", () -> RidingRootCondition.CODEC);
}
