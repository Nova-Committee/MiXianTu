package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.artifact.ArtifactStateComponent;
import com.iafenvoy.mxt.data.artifact.ForgingResultComponent;
import com.iafenvoy.mxt.data.artifact.ItemAbilitiesComponent;
import com.iafenvoy.mxt.data.aura.ItemAuraComponent;
import com.iafenvoy.mxt.data.aura.SpiritStorageComponent;
import com.iafenvoy.mxt.data.cultivation.Element;
import com.iafenvoy.mxt.data.cultivation.Technique;
import com.iafenvoy.mxt.data.curse.CurseContainerComponent;
import com.iafenvoy.mxt.data.economy.ChequeComponent;
import com.iafenvoy.mxt.data.forging.ForgingBlueprint;
import com.iafenvoy.mxt.data.forging.ForgingMethod;
import com.iafenvoy.mxt.data.item.*;
import com.iafenvoy.mxt.data.quality.ItemQuality;
import com.iafenvoy.mxt.data.quality.QualityChain;
import com.iafenvoy.mxt.data.storage.DataStorageHolder;
import com.iafenvoy.mxt.util.codec.AutoIgnoreListCodec;
import com.iafenvoy.mxt.util.codec.RegistryCodecs;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.tags.TagKey;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredRegister.DataComponents;

import java.util.List;

public final class MxtDataComponents {
    public static final DataComponents REGISTRY = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, MiXianTu.MOD_ID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ForgingResultComponent>> FORGING_RESULT = register("forging_result", ForgingResultComponent.CODEC);
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<List<Holder<ForgingMethod>>>> FORGING_METHODS = register("forging_methods", AutoIgnoreListCodec.create(ForgingMethod.CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<List<Holder<ForgingBlueprint>>>> FORGING_BLUEPRINTS = register("forging_blueprints", AutoIgnoreListCodec.create(ForgingBlueprint.CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Holder<ItemQuality>>> ITEM_QUALITY = register("item_quality", ItemQuality.CODEC);
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Holder<QualityChain>>> QUALITY_CHAIN = register("quality_chain", QualityChain.CODEC);
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<List<Either<Holder<Element>, TagKey<Element>>>>> ELEMENT = register("element", RegistryCodecs.holderOrTagList(MxtResourceKeys.ELEMENT));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<PillComponent>> PILL = register("pill", PillComponent.CODEC);
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<TechniqueReadingComponent>> TECHNIQUE_READING = register("technique_reading", TechniqueReadingComponent.CODEC);
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ItemAuraComponent>> ITEM_AURA = register("item_aura", ItemAuraComponent.CODEC);
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<SpiritStorageComponent>> SPIRIT_STORAGE = register("spirit_storage", SpiritStorageComponent.CODEC);
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ArtifactStateComponent>> ARTIFACT_STATE = register("artifact_state", ArtifactStateComponent.CODEC);
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<DataStorageHolder>> STORAGE = register("storage", DataStorageHolder.CODEC);
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ItemAbilitiesComponent>> ITEM_ABILITIES = register("item_abilities", ItemAbilitiesComponent.CODEC);
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<CurseContainerComponent>> CURSE_CONTAINER = register("curse_container", CurseContainerComponent.CODEC);
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ChequeComponent>> CHEQUE = register("cheque", ChequeComponent.CODEC);
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ContractScrollComponent>> CONTRACT_SCROLL = register("contract_scroll", ContractScrollComponent.CODEC);
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ContractBellComponent>> CONTRACT_BELL = register("contract_bell", ContractBellComponent.CODEC);
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<SpiritBeastComponent>> SPIRIT_BEAST = register("spirit_beast", SpiritBeastComponent.CODEC);
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<FormationPlateComponent>> FORMATION_PLATE = register("formation_plate", FormationPlateComponent.CODEC);
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<SecretRealmTokenComponent>> SECRET_REALM_TOKEN = register("secret_realm_token", SecretRealmTokenComponent.CODEC);
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ResourceContainerComponent>> RESOURCE_CONTAINER = register("resource_container", ResourceContainerComponent.CODEC);
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<TokenComponent>> TOKEN = register("token", TokenComponent.CODEC);
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<IdentificationComponent>> IDENTIFICATION = register("identification", IdentificationComponent.CODEC);
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<TalismanComponent>> TALISMAN = register("talisman", TalismanComponent.CODEC);
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<RiftComponent>> RIFT = register("rift", RiftComponent.CODEC);
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Holder<Technique>>> TECHNIQUE = register("technique", Technique.CODEC);

    private static <T> DeferredHolder<DataComponentType<?>, DataComponentType<T>> register(String id, Codec<T> codec) {
        return REGISTRY.registerComponentType(id, b -> b.persistent(codec).networkSynchronized(ByteBufCodecs.fromCodecWithRegistries(codec)));
    }
}
