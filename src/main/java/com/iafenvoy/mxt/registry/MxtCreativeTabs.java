package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.aura.SpiritStorageComponent;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@SuppressWarnings("unused")
public final class MxtCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> REGISTRY = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MiXianTu.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = REGISTRY.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.mxt.main"))
            .icon(() -> new ItemStack(MxtItems.SPIRIT_STONE_BAG.get()))
            .displayItems((parameters, output) -> {
                MxtItems.registeredItems().forEach(item -> output.accept(item.get()));
                MxtItems.spiritStones().forEach(item -> {
                    ItemStack empty = new ItemStack(item.get());
                    // A store with an empty map is a stone that was drained, which is what the creative menu is
                    // offering: without the component at all it would read as a pristine, full one. What it is
                    // empty of is its own definition's answer, so nothing has to be recorded here.
                    empty.set(MxtDataComponents.SPIRIT_STORAGE, SpiritStorageComponent.EMPTY);
                    output.accept(empty);
                });
            }).build());
}
