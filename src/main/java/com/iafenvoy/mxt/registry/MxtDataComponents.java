package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.artifact.ArtifactStateData;
import com.iafenvoy.mxt.data.artifact.ArtifactStorageData;
import com.iafenvoy.mxt.data.artifact.ForgingResultData;
import com.iafenvoy.mxt.data.artifact.ItemAbilitiesData;
import com.iafenvoy.mxt.data.curse.CurseContainerData;
import com.iafenvoy.mxt.data.ChequeData;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Persistent ItemStack state. Item components contain results, never active player attachment state.
 */
public final class MxtDataComponents {
    public static final DeferredRegister<DataComponentType<?>> REGISTRY = DeferredRegister.create(BuiltInRegistries.DATA_COMPONENT_TYPE, MiXianTu.MOD_ID);
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ForgingResultData>> FORGING_RESULT = REGISTRY.register("forging_result", () -> DataComponentType.<ForgingResultData>builder().persistent(ForgingResultData.CODEC).build());
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ArtifactStateData>> ARTIFACT_STATE = REGISTRY.register("artifact_state", () -> DataComponentType.<ArtifactStateData>builder().persistent(ArtifactStateData.CODEC).build());
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ArtifactStorageData>> ARTIFACT_STORAGE = REGISTRY.register("artifact_storage", () -> DataComponentType.<ArtifactStorageData>builder().persistent(ArtifactStorageData.CODEC).build());
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ItemAbilitiesData>> ITEM_ABILITIES = REGISTRY.register("item_abilities", () -> DataComponentType.<ItemAbilitiesData>builder().persistent(ItemAbilitiesData.CODEC).build());
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<CurseContainerData>> CURSE_CONTAINER = REGISTRY.register("curse_container", () -> DataComponentType.<CurseContainerData>builder().persistent(CurseContainerData.CODEC).build());
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ChequeData>> CHEQUE = REGISTRY.register("cheque", () -> DataComponentType.<ChequeData>builder().persistent(ChequeData.CODEC).build());
    private MxtDataComponents() {
    }
}
