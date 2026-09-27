package com.iafenvoy.mxt.testmod;

import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Test-mod fires. Parent calls {@link #register} from {@code MxtTestMod}. */
public final class AlchemyTestFireItems {
    public static final DeferredRegister.Items REGISTRY = DeferredRegister.createItems(MxtTestMod.MOD_ID);
    /** 200 maximum, 40 degrees per tick. Matches the old wide furnace heating rate. */
    public static final DeferredItem<Item> FIRE = REGISTRY.registerItem("alchemy_test_fire",
            properties -> new AlchemyTestFireItem(properties, 200.0D, 40.0D));
    /** 80 maximum, 10 degrees per tick. Cannot reach a 100±5 recipe. */
    public static final DeferredItem<Item> WEAK_FIRE = REGISTRY.registerItem("alchemy_weak_fire",
            properties -> new AlchemyTestFireItem(properties, 80.0D, 10.0D));

    private AlchemyTestFireItems() {
    }

    public static void register(IEventBus bus) {
        REGISTRY.register(bus);
    }
}
