package com.iafenvoy.mxt.runtime.hold;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Consumable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The vanilla {@code minecraft:consumable} component a module writes to drive a use cycle, and the record of which
 * component it wrote. Two modules write one - a hold and a pill bound to an item that has no use of its own - and
 * each takes back only its own; what they share is this record, not the policy of when to write or drop it.
 *
 * <p>A record is per logical side: in a single-player JVM the client and the integrated server share these statics,
 * and one side's take-back must not consume the other side's record. The component is compared by identity, so an
 * item carrying an equal component of its own is never touched.
 */
public final class TemporaryUseComponent {
    private static final Map<UUID, Written> CLIENT = new HashMap<>();
    private static final Map<UUID, Written> SERVER = new HashMap<>();

    private TemporaryUseComponent() {
    }

    // Writes the component on the stack and remembers it, so a later take-back knows exactly what to remove.
    public static void write(LivingEntity holder, ItemStack stack, Consumable component) {
        stack.set(DataComponents.CONSUMABLE, component);
        side(holder).put(holder.getUUID(), new Written(component));
    }

    // Removes the remembered component from wherever the stack ended up: the whole inventory of a player, the two
    // hands of anything else. A record whose component is gone is simply dropped.
    public static void takeBack(LivingEntity holder) {
        Written written = side(holder).remove(holder.getUUID());
        if (written == null) return;
        for (ItemStack stack : carried(holder))
            if (stack.get(DataComponents.CONSUMABLE) == written.component()) stack.remove(DataComponents.CONSUMABLE);
    }

    // Whether this side is holding a record for the entity: a take-back pass is only worth starting while one is.
    public static boolean hasFor(LivingEntity holder) {
        return side(holder).containsKey(holder.getUUID());
    }

    private static Map<UUID, Written> side(Entity entity) {
        return entity.level().isClientSide() ? CLIENT : SERVER;
    }

    private static List<ItemStack> carried(LivingEntity holder) {
        if (!(holder instanceof Player player)) return List.of(holder.getMainHandItem(), holder.getOffhandItem());
        Inventory inventory = player.getInventory();
        List<ItemStack> stacks = new ArrayList<>(inventory.getContainerSize());
        for (int index = 0; index < inventory.getContainerSize(); index++) stacks.add(inventory.getItem(index));
        return stacks;
    }

    private record Written(Consumable component) {
    }
}
