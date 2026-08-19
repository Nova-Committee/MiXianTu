package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.artifact.ArtifactStateData;
import com.iafenvoy.mxt.data.artifact.ArtifactStorageData;
import com.iafenvoy.mxt.data.artifact.ForgingResultData;
import com.iafenvoy.mxt.data.artifact.ItemAbilitiesData;
import com.iafenvoy.mxt.data.curse.CurseContainerData;
import com.iafenvoy.mxt.data.ChequeData;
import com.iafenvoy.mxt.data.item.ContractScrollData;
import com.iafenvoy.mxt.data.item.FormationPlateData;
import com.iafenvoy.mxt.data.item.IdentificationData;
import com.iafenvoy.mxt.data.item.RealmTokenData;
import com.iafenvoy.mxt.data.item.ResourceContainerData;
import com.iafenvoy.mxt.data.item.SpiritBeastData;
import com.iafenvoy.mxt.data.item.TokenData;
import com.iafenvoy.mxt.data.quality.ItemQuality;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Persistent ItemStack state. Item components contain results, never active player attachment state.
 */
public final class MxtDataComponents {
    public static final DeferredRegister<DataComponentType<?>> REGISTRY = DeferredRegister.create(BuiltInRegistries.DATA_COMPONENT_TYPE, MiXianTu.MOD_ID);
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ForgingResultData>> FORGING_RESULT = REGISTRY.register("forging_result", () -> DataComponentType.<ForgingResultData>builder().persistent(ForgingResultData.CODEC).networkSynchronized(ByteBufCodecs.fromCodecWithRegistries(ForgingResultData.CODEC)).build());
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Holder<ItemQuality>>> ITEM_QUALITY = REGISTRY.register("item_quality", () -> DataComponentType.<Holder<ItemQuality>>builder().persistent(ItemQuality.CODEC).networkSynchronized(ByteBufCodecs.fromCodecWithRegistries(ItemQuality.CODEC)).build());
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ArtifactStateData>> ARTIFACT_STATE = REGISTRY.register("artifact_state", () -> DataComponentType.<ArtifactStateData>builder().persistent(ArtifactStateData.CODEC).build());
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ArtifactStorageData>> ARTIFACT_STORAGE = REGISTRY.register("artifact_storage", () -> DataComponentType.<ArtifactStorageData>builder().persistent(ArtifactStorageData.CODEC).build());
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ItemAbilitiesData>> ITEM_ABILITIES = REGISTRY.register("item_abilities", () -> DataComponentType.<ItemAbilitiesData>builder().persistent(ItemAbilitiesData.CODEC).build());
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<CurseContainerData>> CURSE_CONTAINER = REGISTRY.register("curse_container", () -> DataComponentType.<CurseContainerData>builder().persistent(CurseContainerData.CODEC).build());
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ChequeData>> CHEQUE = REGISTRY.register("cheque", () -> DataComponentType.<ChequeData>builder().persistent(ChequeData.CODEC).build());
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ContractScrollData>> CONTRACT_SCROLL = REGISTRY.register("contract_scroll", () -> DataComponentType.<ContractScrollData>builder().persistent(ContractScrollData.CODEC).networkSynchronized(ByteBufCodecs.fromCodecWithRegistries(ContractScrollData.CODEC)).build());
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<SpiritBeastData>> SPIRIT_BEAST = REGISTRY.register("spirit_beast", () -> DataComponentType.<SpiritBeastData>builder().persistent(SpiritBeastData.CODEC).networkSynchronized(ByteBufCodecs.fromCodecWithRegistries(SpiritBeastData.CODEC)).build());
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<FormationPlateData>> FORMATION_PLATE = REGISTRY.register("formation_plate", () -> DataComponentType.<FormationPlateData>builder().persistent(FormationPlateData.CODEC).networkSynchronized(ByteBufCodecs.fromCodecWithRegistries(FormationPlateData.CODEC)).build());
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<RealmTokenData>> REALM_TOKEN = REGISTRY.register("realm_token", () -> DataComponentType.<RealmTokenData>builder().persistent(RealmTokenData.CODEC).networkSynchronized(ByteBufCodecs.fromCodecWithRegistries(RealmTokenData.CODEC)).build());
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ResourceContainerData>> RESOURCE_CONTAINER = REGISTRY.register("resource_container", () -> DataComponentType.<ResourceContainerData>builder().persistent(ResourceContainerData.CODEC).networkSynchronized(ByteBufCodecs.fromCodecWithRegistries(ResourceContainerData.CODEC)).build());
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<TokenData>> TOKEN = REGISTRY.register("token", () -> DataComponentType.<TokenData>builder().persistent(TokenData.CODEC).networkSynchronized(ByteBufCodecs.fromCodecWithRegistries(TokenData.CODEC)).build());
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<IdentificationData>> IDENTIFICATION = REGISTRY.register("identification", () -> DataComponentType.<IdentificationData>builder().persistent(IdentificationData.CODEC).networkSynchronized(ByteBufCodecs.fromCodecWithRegistries(IdentificationData.CODEC)).build());

    private MxtDataComponents() {
    }
}
