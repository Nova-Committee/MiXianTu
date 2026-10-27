package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.condition.builtin.entity.*;
import com.iafenvoy.saturn.data.condition.EntityCondition;
import com.iafenvoy.saturn.registry.SaturnRegistries;
import com.mojang.serialization.MapCodec;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@SuppressWarnings("unused")
public final class MxtEntityConditions {
    public static final DeferredRegister<MapCodec<? extends EntityCondition>> REGISTRY = DeferredRegister.create(SaturnRegistries.ENTITY_CONDITION_TYPE, MiXianTu.MOD_ID);


    public static final DeferredHolder<MapCodec<? extends EntityCondition>, MapCodec<HasCurseEntityCondition>> HAS_CURSE = REGISTRY.register("has_curse", () -> HasCurseEntityCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityCondition>, MapCodec<HasSpiritRootEntityCondition>> HAS_SPIRIT_ROOT = REGISTRY.register("has_spirit_root", () -> HasSpiritRootEntityCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityCondition>, MapCodec<HasElementEntityCondition>> HAS_ELEMENT = REGISTRY.register("has_element", () -> HasElementEntityCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityCondition>, MapCodec<ElementAttachmentEntityCondition>> ELEMENT_ATTACHMENT = REGISTRY.register("element_attachment", () -> ElementAttachmentEntityCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityCondition>, MapCodec<HasPhysiqueEntityCondition>> HAS_PHYSIQUE = REGISTRY.register("has_physique", () -> HasPhysiqueEntityCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityCondition>, MapCodec<RealmEntityCondition>> REALM = REGISTRY.register("realm", () -> RealmEntityCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityCondition>, MapCodec<HasRealmEntityCondition>> HAS_REALM = REGISTRY.register("has_realm", () -> HasRealmEntityCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityCondition>, MapCodec<HasTechniqueEntityCondition>> TECHNIQUE = REGISTRY.register("technique", () -> HasTechniqueEntityCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityCondition>, MapCodec<CultivatingEntityCondition>> CULTIVATING = REGISTRY.register("cultivating", () -> CultivatingEntityCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityCondition>, MapCodec<ContractEntityCondition>> CONTRACT = REGISTRY.register("contract", () -> ContractEntityCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityCondition>, MapCodec<ProgressionEntityCondition>> PROGRESSION = REGISTRY.register("progression", () -> ProgressionEntityCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityCondition>, MapCodec<AuraRangeEntityCondition>> AURA_RANGE = REGISTRY.register("aura_range", () -> AuraRangeEntityCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityCondition>, MapCodec<AuraElementEntityCondition>> AURA_ELEMENT = REGISTRY.register("aura_element", () -> AuraElementEntityCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityCondition>, MapCodec<FormationMemberEntityCondition>> FORMATION_MEMBER = REGISTRY.register("formation_member", () -> FormationMemberEntityCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityCondition>, MapCodec<FormationOwnerEntityCondition>> FORMATION_OWNER = REGISTRY.register("formation_owner", () -> FormationOwnerEntityCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityCondition>, MapCodec<FormationAllyEntityCondition>> FORMATION_ALLY = REGISTRY.register("formation_ally", () -> FormationAllyEntityCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityCondition>, MapCodec<PillToxicityCondition>> PILL_TOXICITY = REGISTRY.register("pill_toxicity", () -> PillToxicityCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityCondition>, MapCodec<HasEquippedItemCondition>> HAS_EQUIPPED_ITEM = REGISTRY.register("has_equipped_item", () -> HasEquippedItemCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityCondition>, MapCodec<InSecretRealmEntityCondition>> IN_SECRET_REALM = REGISTRY.register("in_secret_realm", () -> InSecretRealmEntityCondition.CODEC);
}
