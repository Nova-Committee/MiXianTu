package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.action.BlockAction;
import com.iafenvoy.mxt.data.action.builtin.*;
import com.mojang.serialization.MapCodec;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class MxtBlockActions {
    public static final DeferredRegister<MapCodec<? extends BlockAction>> REGISTRY = DeferredRegister.create(MxtTypeRegistries.BLOCK_ACTION_TYPE, MiXianTu.MOD_ID);
    public static final DeferredHolder<MapCodec<? extends BlockAction>, MapCodec<NoOpBlockAction>> NO_OP = REGISTRY.register("no_op", () -> NoOpBlockAction.CODEC);
    public static final DeferredHolder<MapCodec<? extends BlockAction>, MapCodec<SequenceBlockAction>> SEQUENCE = REGISTRY.register("sequence", () -> SequenceBlockAction.CODEC);
    public static final DeferredHolder<MapCodec<? extends BlockAction>, MapCodec<SetBlockAction>> SET_BLOCK = REGISTRY.register("set_block", () -> SetBlockAction.CODEC);
    public static final DeferredHolder<MapCodec<? extends BlockAction>, MapCodec<BreakBlockAction>> BREAK_BLOCK = REGISTRY.register("break_block", () -> BreakBlockAction.CODEC);
    public static final DeferredHolder<MapCodec<? extends BlockAction>, MapCodec<ChangeAuraBlockAction>> CHANGE_AURA = REGISTRY.register("change_aura", () -> ChangeAuraBlockAction.CODEC);

    private MxtBlockActions() {
    }
}
