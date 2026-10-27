package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.action.builtin.entity.*;
import com.iafenvoy.mxt.runtime.cultivation.LifeSpanService;
import com.iafenvoy.saturn.data.action.EntityAction;
import com.iafenvoy.saturn.registry.SaturnRegistries;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import static com.iafenvoy.saturn.data.action.SimpleActions.createEntity;

@SuppressWarnings("unused")
public final class MxtEntityActions {
    public static final DeferredRegister<MapCodec<? extends EntityAction>> REGISTRY = DeferredRegister.create(SaturnRegistries.ENTITY_ACTION_TYPE, MiXianTu.MOD_ID);


    public static final DeferredHolder<MapCodec<? extends EntityAction>, MapCodec<DamageAction>> DAMAGE = REGISTRY.register("damage", () -> DamageAction.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityAction>, MapCodec<GrantSpiritRootAction>> GRANT_SPIRIT_ROOT = REGISTRY.register("grant_spirit_root", () -> GrantSpiritRootAction.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityAction>, MapCodec<AttachElementAction>> ATTACH_ELEMENT = REGISTRY.register("attach_element", () -> AttachElementAction.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityAction>, MapCodec<GrantPhysiqueAction>> GRANT_PHYSIQUE = REGISTRY.register("grant_physique", () -> GrantPhysiqueAction.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityAction>, MapCodec<RemoveSpiritRootAction>> REMOVE_SPIRIT_ROOT = REGISTRY.register("remove_spirit_root", () -> RemoveSpiritRootAction.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityAction>, MapCodec<RemovePhysiqueAction>> REMOVE_PHYSIQUE = REGISTRY.register("remove_physique", () -> RemovePhysiqueAction.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityAction>, MapCodec<ApplyCurseAction>> APPLY_CURSE = REGISTRY.register("apply_curse", () -> ApplyCurseAction.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityAction>, MapCodec<ApplyCursesAction>> APPLY_CURSES = REGISTRY.register("apply_curses", () -> ApplyCursesAction.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityAction>, MapCodec<RemoveCurseAction>> REMOVE_CURSE = REGISTRY.register("remove_curse", () -> RemoveCurseAction.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityAction>, MapCodec<RemoveCursesByTagAction>> REMOVE_CURSES_BY_TAG = REGISTRY.register("remove_curses_by_tag", () -> RemoveCursesByTagAction.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityAction>, MapCodec<SpawnSwordAuraAction>> SPAWN_SWORD_AURA = REGISTRY.register("spawn_sword_aura", () -> SpawnSwordAuraAction.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityAction>, MapCodec<ExplodeAction>> EXPLODE = REGISTRY.register("explode", () -> ExplodeAction.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityAction>, MapCodec<SpawnLightningAction>> SPAWN_LIGHTNING = REGISTRY.register("spawn_lightning", () -> SpawnLightningAction.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityAction>, MapCodec<ModifyLifespanAction>> MODIFY_LIFESPAN = REGISTRY.register("modify_lifespan", () -> ModifyLifespanAction.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityAction>, MapCodec<ModifyPillToxicityAction>> MODIFY_PILL_TOXICITY = REGISTRY.register("modify_pill_toxicity", () -> ModifyPillToxicityAction.CODEC);
    // A pack's own reincarnation pill, trial or curse ends here instead of waiting for a life to run out; the reset
    // list, the config switches and any listener's veto are the ones the command and the script entry use.
    public static final DeferredHolder<MapCodec<? extends EntityAction>, MapCodec<? extends EntityAction>> REINCARNATE = REGISTRY.register("reincarnate", () -> createEntity(ctx -> {
        if (ctx.entity() instanceof LivingEntity living) LifeSpanService.reincarnate(living);
    }));
    // The data pack's own way in and out of cultivation: the only other entry point is the player's key.
    public static final DeferredHolder<MapCodec<? extends EntityAction>, MapCodec<StartCultivatingAction>> CULTIVATE = REGISTRY.register("cultivate", () -> StartCultivatingAction.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityAction>, MapCodec<StopCultivatingAction>> STOP_CULTIVATING = REGISTRY.register("stop_cultivating", () -> StopCultivatingAction.CODEC);
}
