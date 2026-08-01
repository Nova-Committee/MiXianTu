package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.item.ItemBindingDefinition;
import com.iafenvoy.mxt.data.item.ItemDefinitionReference;
import com.iafenvoy.mxt.data.item.ItemDefinitionRegistry;
import com.iafenvoy.mxt.runtime.item.ItemBindingService;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

/** Code-owned and datapack-defined item collections. */
public final class MxtCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> REGISTRY = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MiXianTu.MOD_ID);
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = REGISTRY.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.mxt.main"))
            .icon(() -> new ItemStack(MxtItems.CHEQUE.get()))
            .displayItems((parameters, output) -> MxtItems.registeredItems().forEach(item -> output.accept(item.get())))
            .build());
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> ITEM = dataDrivenTab(
            "item", "itemGroup.mxt.item", Items.APPLE, ItemDefinitionRegistry.OTHER, false);
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> PILL = dataDrivenTab(
            "pill", "itemGroup.mxt.pill", Items.HONEY_BOTTLE, ItemDefinitionRegistry.PILL, true);
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> WEAPON = dataDrivenTab(
            "weapon", "itemGroup.mxt.weapon", Items.DIAMOND_SWORD, ItemDefinitionRegistry.WEAPON, true);

    private static DeferredHolder<CreativeModeTab, CreativeModeTab> dataDrivenTab(String path, String title, Item icon,
                                                                                     ItemDefinitionRegistry category, boolean bindStack) {
        return REGISTRY.register(path, () -> CreativeModeTab.builder()
                .title(Component.translatable(title))
                .icon(() -> new ItemStack(icon))
                .displayItems((parameters, output) -> MxtDatapackRegistries
                        .holders(parameters.holders(), MxtDatapackRegistries.itemRegistry(category))
                        .forEach(definition -> addDefinition(parameters, output, category, definition, bindStack)))
                .build());
    }

    private static void addDefinition(CreativeModeTab.ItemDisplayParameters parameters, CreativeModeTab.Output output,
                                      ItemDefinitionRegistry category, Holder.Reference<com.iafenvoy.mxt.data.item.ItemDefinition> definition,
                                      boolean bindStack) {
        ItemDefinitionReference reference = new ItemDefinitionReference(category, definition.key().identifier());
        if (bindStack) {
            ItemBindingService.create(parameters.holders(), reference).ifPresent(output::accept);
            return;
        }
        List<Item> bindings = MxtDatapackRegistries.holders(parameters.holders(), MxtDatapackRegistries.ITEM_BINDING)
                .map(Holder.Reference::value)
                .filter(binding -> binding.definition().equals(reference))
                .map(ItemBindingDefinition::item)
                .distinct()
                .toList();
        if (bindings.size() == 1) output.accept(bindings.getFirst());
    }

    private MxtCreativeTabs() {
    }
}
