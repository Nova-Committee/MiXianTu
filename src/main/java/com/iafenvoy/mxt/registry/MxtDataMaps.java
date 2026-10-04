package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.CurrencyValue;
import com.iafenvoy.mxt.data.alchemy.HeatSource;
import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.aura.AuraValue;
import com.iafenvoy.mxt.data.aura.BlockAuraMerger;
import com.iafenvoy.mxt.data.aura.ItemAura;
import com.iafenvoy.mxt.data.forging.BlueprintBinding;
import com.iafenvoy.mxt.data.forging.ToolBinding;
import com.iafenvoy.mxt.data.item.ItemBinding;
import com.iafenvoy.mxt.data.item.WeaponBinding;
import com.iafenvoy.mxt.data.quality.ItemQuality;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.datamaps.AdvancedDataMapType;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import net.neoforged.neoforge.registries.datamaps.DataMapValueMerger;
import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent;
import org.jspecify.annotations.NonNull;

import java.util.Map;
import java.util.function.ToIntFunction;

/**
 * The data maps this mod publishes. The item-keyed seven answer "what is this item" - the fuel it burns, the price it
 * carries, the behaviour it brings to a fight or to the forge, the tier it starts at; the block-keyed two answer
 * "what does this block give off" (its aura, its heat). They replace the matching tables that answered the same
 * question by walking every definition on every lookup.
 */
@EventBusSubscriber
public final class MxtDataMaps {
    public static final DataMapType<Item, ItemAura> ITEM_AURA = table("item_aura", ItemAura.CODEC, ItemAura::priority);
    public static final DataMapType<Item, CurrencyValue> CURRENCY = table("currency", CurrencyValue.CODEC, CurrencyValue::priority);
    public static final DataMapType<Item, ItemBinding> ITEM_BINDING = table("item_binding", ItemBinding.CODEC, ItemBinding::priority);
    public static final DataMapType<Item, WeaponBinding> WEAPON_BINDING = table("weapon_binding", WeaponBinding.CODEC, WeaponBinding::priority);
    public static final DataMapType<Item, ToolBinding> TOOL_BINDING = table("tool_binding", ToolBinding.CODEC, ToolBinding::priority);
    public static final DataMapType<Item, BlueprintBinding> BLUEPRINT_BINDING = table("blueprint_binding", BlueprintBinding.CODEC, BlueprintBinding::priority);
    // The item's own tier, read only when nothing else answers: no merger, because the value carries no priority -
    // two packs claiming one item settle the way every other map does, the later one winning.
    public static final DataMapType<Item, Holder<ItemQuality>> DEFAULT_QUALITY = DataMapType
            .builder(Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "default_quality"), Registries.ITEM, ItemQuality.CODEC)
            .synced(ItemQuality.CODEC, false)
            .build();
    public static final DataMapType<Block, Map<Holder<Aura>, AuraValue>> BLOCK_AURA = AdvancedDataMapType
            .builder(Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "block_aura"), Registries.BLOCK, AuraValue.MAP_CODEC)
            .merger(new BlockAuraMerger())
            .synced(AuraValue.MAP_CODEC, false)
            .build();
    public static final DataMapType<Block, HeatSource> HEAT_SOURCE = AdvancedDataMapType
            .builder(Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "heat_source"), Registries.BLOCK, HeatSource.CODEC)
            .merger(new PriorityMerger<>(HeatSource::priority))
            .synced(HeatSource.CODEC, false)
            .build();

    private MxtDataMaps() {
    }

    @SubscribeEvent
    public static void register(RegisterDataMapTypesEvent event) {
        event.register(ITEM_AURA);
        event.register(CURRENCY);
        event.register(ITEM_BINDING);
        event.register(WEAPON_BINDING);
        event.register(TOOL_BINDING);
        event.register(BLUEPRINT_BINDING);
        event.register(DEFAULT_QUALITY);
        event.register(BLOCK_AURA);
        event.register(HEAT_SOURCE);
    }

    // Synced, because the client draws tooltips and the picker's catalogue from the same tables.
    private static <T> DataMapType<Item, T> table(String path, Codec<T> codec, ToIntFunction<T> priority) {
        return AdvancedDataMapType.builder(Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, path), Registries.ITEM, codec)
                .merger(new PriorityMerger<>(priority))
                .synced(codec, false)
                .build();
    }

    /**
     * The conflict rule the priority-ordered tables share: the higher {@code priority} wins, and equal priorities
     * leave the later value in place. That is the old matching order - highest priority first, ties by load order -
     * with "the later pack" standing where "the later registry entry" used to.
     */
    private record PriorityMerger<R, T>(ToIntFunction<T> priority) implements DataMapValueMerger<R, T> {
        @Override
        public T merge(@NonNull Registry<R> registry, @NonNull Either<TagKey<R>, ResourceKey<R>> first, T firstValue,
                       @NonNull Either<TagKey<R>, ResourceKey<R>> second, T secondValue) {
            return this.priority.applyAsInt(secondValue) >= this.priority.applyAsInt(firstValue) ? secondValue : firstValue;
        }
    }
}
