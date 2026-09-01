package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.action.BlockAction;
import com.iafenvoy.mxt.data.action.NoOpAction;
import com.iafenvoy.mxt.data.action.builtin.block.*;
import com.iafenvoy.mxt.data.action.builtin.block.meta.*;
import com.iafenvoy.mxt.compat.kubejs.type.action.JsBlockAction;
import com.mojang.serialization.MapCodec;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@SuppressWarnings("unused")
public final class MxtBlockActions {
    public static final DeferredRegister<MapCodec<? extends BlockAction>> REGISTRY = DeferredRegister.create(MxtRegistries.BLOCK_ACTION_TYPE, MiXianTu.MOD_ID);

    public static final DeferredHolder<MapCodec<? extends BlockAction>, MapCodec<NoOpAction>> NO_OP = REGISTRY.register("no_op", () -> NoOpAction.CODEC);
    public static final DeferredHolder<MapCodec<? extends BlockAction>, MapCodec<JsBlockAction>> JS = REGISTRY.register("js", () -> JsBlockAction.CODEC);

    public static final DeferredHolder<MapCodec<? extends BlockAction>, MapCodec<SequenceBlockAction>> SEQUENCE = REGISTRY.register("sequence", () -> SequenceBlockAction.CODEC);
    public static final DeferredHolder<MapCodec<? extends BlockAction>, MapCodec<SetBlockAction>> SET_BLOCK = REGISTRY.register("set_block", () -> SetBlockAction.CODEC);
    public static final DeferredHolder<MapCodec<? extends BlockAction>, MapCodec<BreakBlockAction>> BREAK_BLOCK = REGISTRY.register("break_block", () -> BreakBlockAction.CODEC);
    public static final DeferredHolder<MapCodec<? extends BlockAction>, MapCodec<ChangeAuraBlockAction>> CHANGE_AURA = REGISTRY.register("change_aura", () -> ChangeAuraBlockAction.CODEC);
    public static final DeferredHolder<MapCodec<? extends BlockAction>, MapCodec<ScheduleTickAction>> SCHEDULE_TICK = REGISTRY.register("schedule_tick", () -> ScheduleTickAction.CODEC);
    public static final DeferredHolder<MapCodec<? extends BlockAction>, MapCodec<BonemealAction>> BONEMEAL = REGISTRY.register("bonemeal", () -> BonemealAction.CODEC);
    public static final DeferredHolder<MapCodec<? extends BlockAction>, MapCodec<LightUpAction>> LIGHT_UP = REGISTRY.register("light_up", () -> LightUpAction.CODEC);
    public static final DeferredHolder<MapCodec<? extends BlockAction>, MapCodec<ChanceAction>> CHANCE = REGISTRY.register("chance", () -> ChanceAction.CODEC);
    public static final DeferredHolder<MapCodec<? extends BlockAction>, MapCodec<IfElseAction>> IF_ELSE = REGISTRY.register("if_else", () -> IfElseAction.CODEC);
    public static final DeferredHolder<MapCodec<? extends BlockAction>, MapCodec<ChoiceAction>> CHOICE = REGISTRY.register("choice", () -> ChoiceAction.CODEC);
    public static final DeferredHolder<MapCodec<? extends BlockAction>, MapCodec<OffsetAction>> OFFSET = REGISTRY.register("offset", () -> OffsetAction.CODEC);
    public static final DeferredHolder<MapCodec<? extends BlockAction>, MapCodec<ExplodeAction>> EXPLODE = REGISTRY.register("explode", () -> ExplodeAction.CODEC);
    public static final DeferredHolder<MapCodec<? extends BlockAction>, MapCodec<SpawnEntityAction>> SPAWN_ENTITY = REGISTRY.register("spawn_entity", () -> SpawnEntityAction.CODEC);
}
