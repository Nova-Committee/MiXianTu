package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.util.ItemMatcher;
import net.minecraft.core.Registry;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Code-owned items and physical items selected by datapack bindings. */
public final class MxtCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> REGISTRY = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MiXianTu.MOD_ID);
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = REGISTRY.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.mxt.main"))
            .icon(() -> new ItemStack(MxtItems.CHEQUE.get()))
            .displayItems((parameters, output) -> MxtItems.registeredItems().forEach(item -> output.accept(item.get()))).build());
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> ITEM = REGISTRY.register("item", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.mxt.item")).icon(() -> new ItemStack(Items.APPLE))
            .displayItems((parameters, output) -> matchingItems(parameters.holders(), MxtDatapackRegistries.ITEM_BINDING).forEach(output::accept)).build());
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> PILL = REGISTRY.register("pill", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.mxt.pill")).icon(() -> new ItemStack(Items.HONEY_BOTTLE))
            .displayItems((parameters, output) -> matchingItems(parameters.holders(), MxtDatapackRegistries.PILL_BINDING).forEach(output::accept)).build());
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> WEAPON = REGISTRY.register("weapon", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.mxt.weapon")).icon(() -> new ItemStack(Items.DIAMOND_SWORD))
            .displayItems((parameters, output) -> matchingItems(parameters.holders(), MxtDatapackRegistries.WEAPON_BINDING).forEach(output::accept)).build());

    private static <T extends ItemMatcher> java.util.stream.Stream<Item> matchingItems(Provider holders, ResourceKey<Registry<T>> registry) {
        java.util.List<T> bindings = MxtDatapackRegistries.holders(holders, registry).map(reference -> reference.value()).toList();
        return BuiltInRegistries.ITEM.stream().filter(item -> bindings.stream()
                .anyMatch(binding -> binding.entries().stream().anyMatch(entry -> entry.matches(new ItemStack(item)))));
    }

    private MxtCreativeTabs() {
    }
}
