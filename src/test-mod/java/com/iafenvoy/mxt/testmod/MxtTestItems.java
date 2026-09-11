package com.iafenvoy.mxt.testmod;

import com.iafenvoy.mxt.data.forging.BlueprintBinding;
import com.iafenvoy.mxt.data.forging.ToolBinding;
import com.iafenvoy.mxt.registry.MxtDataComponents;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredRegister.Items;

import java.util.function.Function;

/** Items owned only by the development scenario. */
public final class MxtTestItems {
    public static final Items REGISTRY = DeferredRegister.createItems(MxtTestMod.MOD_ID);
    public static final DeferredItem<Item> QINGXIAO_SPIRIT_CRYSTAL = register("qingxiao_spirit_crystal", Item::new);

    /**
     * A hammer and a manual that carry {@code mxt:tool_binding} / {@code mxt:blueprint_binding}.
     *
     * <p>The forge table's tool and blueprint slots only accept stacks with those components, and
     * nothing in the mod puts them on an item yet, so without these the gated path is unreachable
     * and both selector lists can only ever take the "no item placed, fall back to the registry"
     * branch. The component is attached to the item's own component map once the binding holder is
     * known.</p>
     */
    public static final DeferredItem<Item> TEST_HAMMER = register("test_hammer", Item::new);
    public static final DeferredItem<Item> TEST_MANUAL = register("test_manual", Item::new);

    private MxtTestItems() {
    }

    /** A hammer stack bound to a tool binding, or an empty stack when the binding is absent. */
    public static ItemStack hammer(Holder<ToolBinding> binding) {
        if (binding == null) return ItemStack.EMPTY;
        ItemStack stack = new ItemStack(TEST_HAMMER.get());
        stack.set(MxtDataComponents.TOOL_BINDING.get(), binding);
        return stack;
    }

    /** A manual stack bound to a blueprint binding, or an empty stack when the binding is absent. */
    public static ItemStack manual(Holder<BlueprintBinding> binding) {
        if (binding == null) return ItemStack.EMPTY;
        ItemStack stack = new ItemStack(TEST_MANUAL.get());
        stack.set(MxtDataComponents.BLUEPRINT_BINDING.get(), binding);
        return stack;
    }

    private static <T extends Item> DeferredItem<T> register(String path, Function<Properties, T> factory) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MxtTestMod.MOD_ID, path));
        return REGISTRY.register(path, () -> factory.apply(new Properties().setId(key)));
    }
}
