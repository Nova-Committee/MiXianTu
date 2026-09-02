package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.economy.ChequeComponent;
import com.iafenvoy.mxt.data.artifact.ArtifactStateComponent;
import com.iafenvoy.mxt.data.artifact.ArtifactStorageComponent;
import com.iafenvoy.mxt.data.artifact.ForgingResultComponent;
import com.iafenvoy.mxt.data.artifact.ItemAbilitiesComponent;
import com.iafenvoy.mxt.data.aura.ItemAuraComponent;
import com.iafenvoy.mxt.data.aura.SpiritStorageComponent;
import com.iafenvoy.mxt.data.curse.CurseContainerComponent;
import com.iafenvoy.mxt.data.item.ContractScrollComponent;
import com.iafenvoy.mxt.data.item.FormationPlateComponent;
import com.iafenvoy.mxt.data.item.IdentificationComponent;
import com.iafenvoy.mxt.data.item.RealmTokenComponent;
import com.iafenvoy.mxt.data.item.ResourceContainerComponent;
import com.iafenvoy.mxt.data.item.SpiritBeastComponent;
import com.iafenvoy.mxt.data.item.TokenComponent;
import com.iafenvoy.mxt.data.quality.ItemQuality;
import com.mojang.serialization.Codec;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredRegister.DataComponents;

public final class MxtDataComponents {
    public static final DataComponents REGISTRY = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, MiXianTu.MOD_ID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ForgingResultComponent>> FORGING_RESULT = register("forging_result", ForgingResultComponent.CODEC);
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Holder<ItemQuality>>> ITEM_QUALITY = register("item_quality", ItemQuality.CODEC);
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ItemAuraComponent>> ITEM_AURA = register("item_aura", ItemAuraComponent.CODEC);
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<SpiritStorageComponent>> SPIRIT_STORAGE = register("spirit_storage", SpiritStorageComponent.CODEC);
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ArtifactStateComponent>> ARTIFACT_STATE = register("artifact_state", ArtifactStateComponent.CODEC);
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ArtifactStorageComponent>> ARTIFACT_STORAGE = register("artifact_storage", ArtifactStorageComponent.CODEC);
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ItemAbilitiesComponent>> ITEM_ABILITIES = register("item_abilities", ItemAbilitiesComponent.CODEC);
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<CurseContainerComponent>> CURSE_CONTAINER = register("curse_container", CurseContainerComponent.CODEC);
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ChequeComponent>> CHEQUE = register("cheque", ChequeComponent.CODEC);
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ContractScrollComponent>> CONTRACT_SCROLL = register("contract_scroll", ContractScrollComponent.CODEC);
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<SpiritBeastComponent>> SPIRIT_BEAST = register("spirit_beast", SpiritBeastComponent.CODEC);
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<FormationPlateComponent>> FORMATION_PLATE = register("formation_plate", FormationPlateComponent.CODEC);
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<RealmTokenComponent>> REALM_TOKEN = register("realm_token", RealmTokenComponent.CODEC);
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ResourceContainerComponent>> RESOURCE_CONTAINER = register("resource_container", ResourceContainerComponent.CODEC);
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<TokenComponent>> TOKEN = register("token", TokenComponent.CODEC);
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<IdentificationComponent>> IDENTIFICATION = register("identification", IdentificationComponent.CODEC);

    private static <T> DeferredHolder<DataComponentType<?>, DataComponentType<T>> register(String id, Codec<T> codec) {
        return REGISTRY.registerComponentType(id, b -> b.persistent(codec).networkSynchronized(ByteBufCodecs.fromCodecWithRegistries(codec)));
    }
}
