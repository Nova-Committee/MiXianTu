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

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ForgingResultComponent>> FORGING_RESULT = REGISTRY.register("forging_result", () -> DataComponentType.<ForgingResultComponent>builder().persistent(ForgingResultComponent.CODEC).networkSynchronized(ByteBufCodecs.fromCodecWithRegistries(ForgingResultComponent.CODEC)).build());
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Holder<ItemQuality>>> ITEM_QUALITY = REGISTRY.register("item_quality", () -> DataComponentType.<Holder<ItemQuality>>builder().persistent(ItemQuality.CODEC).networkSynchronized(ByteBufCodecs.fromCodecWithRegistries(ItemQuality.CODEC)).build());
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ItemAuraComponent>> ITEM_AURA = REGISTRY.register("item_aura", () -> DataComponentType.<ItemAuraComponent>builder().persistent(ItemAuraComponent.CODEC).networkSynchronized(ByteBufCodecs.fromCodec(ItemAuraComponent.CODEC)).build());
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<SpiritStorageComponent>> SPIRIT_STORAGE = REGISTRY.register("spirit_storage", () -> DataComponentType.<SpiritStorageComponent>builder().persistent(SpiritStorageComponent.CODEC).networkSynchronized(ByteBufCodecs.fromCodec(SpiritStorageComponent.CODEC)).build());
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ArtifactStateComponent>> ARTIFACT_STATE = REGISTRY.register("artifact_state", () -> DataComponentType.<ArtifactStateComponent>builder().persistent(ArtifactStateComponent.CODEC).build());
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ArtifactStorageComponent>> ARTIFACT_STORAGE = REGISTRY.register("artifact_storage", () -> DataComponentType.<ArtifactStorageComponent>builder().persistent(ArtifactStorageComponent.CODEC).build());
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ItemAbilitiesComponent>> ITEM_ABILITIES = REGISTRY.register("item_abilities", () -> DataComponentType.<ItemAbilitiesComponent>builder().persistent(ItemAbilitiesComponent.CODEC).build());
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<CurseContainerComponent>> CURSE_CONTAINER = REGISTRY.register("curse_container", () -> DataComponentType.<CurseContainerComponent>builder().persistent(CurseContainerComponent.CODEC).build());
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ChequeComponent>> CHEQUE = REGISTRY.register("cheque", () -> DataComponentType.<ChequeComponent>builder().persistent(ChequeComponent.CODEC).networkSynchronized(ByteBufCodecs.fromCodecWithRegistries(ChequeComponent.CODEC)).build());
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ContractScrollComponent>> CONTRACT_SCROLL = REGISTRY.register("contract_scroll", () -> DataComponentType.<ContractScrollComponent>builder().persistent(ContractScrollComponent.CODEC).networkSynchronized(ByteBufCodecs.fromCodecWithRegistries(ContractScrollComponent.CODEC)).build());
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<SpiritBeastComponent>> SPIRIT_BEAST = REGISTRY.register("spirit_beast", () -> DataComponentType.<SpiritBeastComponent>builder().persistent(SpiritBeastComponent.CODEC).networkSynchronized(ByteBufCodecs.fromCodecWithRegistries(SpiritBeastComponent.CODEC)).build());
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<FormationPlateComponent>> FORMATION_PLATE = REGISTRY.register("formation_plate", () -> DataComponentType.<FormationPlateComponent>builder().persistent(FormationPlateComponent.CODEC).networkSynchronized(ByteBufCodecs.fromCodecWithRegistries(FormationPlateComponent.CODEC)).build());
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<RealmTokenComponent>> REALM_TOKEN = REGISTRY.register("realm_token", () -> DataComponentType.<RealmTokenComponent>builder().persistent(RealmTokenComponent.CODEC).networkSynchronized(ByteBufCodecs.fromCodecWithRegistries(RealmTokenComponent.CODEC)).build());
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ResourceContainerComponent>> RESOURCE_CONTAINER = REGISTRY.register("resource_container", () -> DataComponentType.<ResourceContainerComponent>builder().persistent(ResourceContainerComponent.CODEC).networkSynchronized(ByteBufCodecs.fromCodecWithRegistries(ResourceContainerComponent.CODEC)).build());
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<TokenComponent>> TOKEN = REGISTRY.register("token", () -> DataComponentType.<TokenComponent>builder().persistent(TokenComponent.CODEC).networkSynchronized(ByteBufCodecs.fromCodecWithRegistries(TokenComponent.CODEC)).build());
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<IdentificationComponent>> IDENTIFICATION = REGISTRY.register("identification", () -> DataComponentType.<IdentificationComponent>builder().persistent(IdentificationComponent.CODEC).networkSynchronized(ByteBufCodecs.fromCodecWithRegistries(IdentificationComponent.CODEC)).build());

    private MxtDataComponents() {
    }
}
