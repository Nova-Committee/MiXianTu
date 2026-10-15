package com.iafenvoy.mxt.runtime.hold;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Consumable;

import java.util.*;

/**
 * The vanilla {@code minecraft:consumable} component a module writes to drive a use cycle, and the record of which
 * component it wrote. Two modules write one - a hold and a pill bound to an item that has no use of its own - and
 * each takes back only its own; what they share is this record, not the policy of when to write or drop it.
 *
 * <p>The record is therefore per owner as well as per body: both cycles can be armed for the same reader, and the
 * one that ends first must not strip the other's component - with it would go the consumption the pill is there for.
 *
 * <p>A record is per logical side: in a single-player JVM the client and the integrated server share these statics,
 * and one side's take-back must not consume the other side's record. The component is compared by identity, so an
 * item carrying an equal component of its own is never touched.
 */
public final class TemporaryUseComponent {
    /** Which module opened a cycle. It is the only thing keeping two of them on one body apart. */
    public enum Owner {
        HOLD,
        PILL
    }

    private static final Map<UUID, EnumMap<Owner, Written>> CLIENT = new HashMap<>();
    private static final Map<UUID, EnumMap<Owner, Written>> SERVER = new HashMap<>();

    private TemporaryUseComponent() {
    }

    // Writes the component on the stack and remembers it, so a later take-back knows exactly what to remove.
    public static void write(LivingEntity holder, ItemStack stack, Consumable component, Owner owner) {
        stack.set(DataComponents.CONSUMABLE, component);
        side(holder).computeIfAbsent(holder.getUUID(), id -> new EnumMap<>(Owner.class)).put(owner, new Written(component));
    }

    // Removes the remembered component from wherever the stack ended up: the whole inventory of a player, the two
    // hands of anything else. A record whose component is gone is simply dropped, and another owner's record is
    // left exactly as it is.
    public static void takeBack(LivingEntity holder, Owner owner) {
        Map<Owner, Written> written = side(holder).get(holder.getUUID());
        if (written == null) return;
        Written record = written.remove(owner);
        if (written.isEmpty()) side(holder).remove(holder.getUUID());
        if (record == null) return;
        for (ItemStack stack : carried(holder))
            if (stack.get(DataComponents.CONSUMABLE) == record.component()) stack.remove(DataComponents.CONSUMABLE);
    }

    // Whether this side is holding a record for the entity: a take-back pass is only worth starting while one is.
    public static boolean hasFor(LivingEntity holder, Owner owner) {
        Map<Owner, Written> written = side(holder).get(holder.getUUID());
        return written != null && written.containsKey(owner);
    }

    private static Map<UUID, EnumMap<Owner, Written>> side(Entity entity) {
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
