package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.action.ItemAction;
import com.iafenvoy.mxt.data.action.NoOpAction;
import com.iafenvoy.mxt.data.action.builtin.item.*;
import com.iafenvoy.mxt.data.action.builtin.item.meta.*;
import com.iafenvoy.mxt.compat.kubejs.type.action.JsItemAction;
import com.mojang.serialization.MapCodec;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@SuppressWarnings("unused")
public final class MxtItemActions {
    public static final DeferredRegister<MapCodec<? extends ItemAction>> REGISTRY = DeferredRegister.create(MxtRegistries.ITEM_ACTION_TYPE, MiXianTu.MOD_ID);

    public static final DeferredHolder<MapCodec<? extends ItemAction>, MapCodec<NoOpAction>> NO_OP = REGISTRY.register("no_op", () -> NoOpAction.CODEC);
    public static final DeferredHolder<MapCodec<? extends ItemAction>, MapCodec<JsItemAction>> JS = REGISTRY.register("js", () -> JsItemAction.CODEC);

    public static final DeferredHolder<MapCodec<? extends ItemAction>, MapCodec<SequenceItemAction>> SEQUENCE = REGISTRY.register("sequence", () -> SequenceItemAction.CODEC);
    public static final DeferredHolder<MapCodec<? extends ItemAction>, MapCodec<DamageItemAction>> DAMAGE_ITEM = REGISTRY.register("damage_item", () -> DamageItemAction.CODEC);
    public static final DeferredHolder<MapCodec<? extends ItemAction>, MapCodec<ConsumeItemAction>> CONSUME_ITEM = REGISTRY.register("consume_item", () -> ConsumeItemAction.CODEC);
    public static final DeferredHolder<MapCodec<? extends ItemAction>, MapCodec<ChargeArtifactItemAction>> CHARGE_ARTIFACT = REGISTRY.register("charge_artifact", () -> ChargeArtifactItemAction.CODEC);
    public static final DeferredHolder<MapCodec<? extends ItemAction>, MapCodec<ChanceAction>> CHANCE = REGISTRY.register("chance", () -> ChanceAction.CODEC);
    public static final DeferredHolder<MapCodec<? extends ItemAction>, MapCodec<IfElseAction>> IF_ELSE = REGISTRY.register("if_else", () -> IfElseAction.CODEC);
    public static final DeferredHolder<MapCodec<? extends ItemAction>, MapCodec<ChoiceAction>> CHOICE = REGISTRY.register("choice", () -> ChoiceAction.CODEC);
    public static final DeferredHolder<MapCodec<? extends ItemAction>, MapCodec<CooldownAction>> COOLDOWN = REGISTRY.register("cooldown", () -> CooldownAction.CODEC);
    public static final DeferredHolder<MapCodec<? extends ItemAction>, MapCodec<RemoveEnchantmentAction>> REMOVE_ENCHANTMENT = REGISTRY.register("remove_enchantment", () -> RemoveEnchantmentAction.CODEC);
    public static final DeferredHolder<MapCodec<? extends ItemAction>, MapCodec<AddEnchantmentAction>> ADD_ENCHANTMENT = REGISTRY.register("add_enchantment", () -> AddEnchantmentAction.CODEC);
    public static final DeferredHolder<MapCodec<? extends ItemAction>, MapCodec<MergeComponentsAction>> MERGE_COMPONENTS = REGISTRY.register("merge_components", () -> MergeComponentsAction.CODEC);
}
