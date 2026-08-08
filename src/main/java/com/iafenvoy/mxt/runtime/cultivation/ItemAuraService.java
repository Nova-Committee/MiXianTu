package com.iafenvoy.mxt.runtime.cultivation;

import com.iafenvoy.mxt.attachment.SpiritData;
import com.iafenvoy.mxt.data.aura.ItemAura;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerLevel;

import java.util.Optional;

/**
 * Server-authoritative furnace-style fuel handling for item-provided aura.
 */
public final class ItemAuraService {
    private static final double EPSILON = 1.0E-9D;

    private ItemAuraService() {
    }

    public static Optional<Holder<ItemAura>> find(Provider access, ItemStack stack) {
        if (stack.isEmpty()) return Optional.empty();
        return MxtDatapackRegistries.holders(access, MxtDatapackRegistries.ITEM_AURA)
                .filter(holder -> holder.value().entries().stream().anyMatch(entry -> entry.matches(stack)))
                .map(holder -> (Holder<ItemAura>) holder)
                .findFirst();
    }

    public static Optional<Holder<ItemAura>> find(LivingEntity entity, ItemStack stack) {
        return find(entity.level().registryAccess(), stack);
    }

    /**
     * Advances the current fuel by one server tick. An empty bar consumes one matching
     * stack from the main hand, falling back to the off hand, and loads its total aura.
     */
    public static TickResult tick(LivingEntity entity, SpiritData spirit, FormulaContext context) {
        if (spirit.itemAuraRemaining() <= EPSILON) {
            LoadedFuel fuel = consumeHeldItem(entity, context);
            if (fuel != null) spirit.chargeItemAura(fuel.aura(), fuel.total());
        }

        Holder<ItemAura> active = spirit.itemAura().orElse(null);
        if (active == null || spirit.itemAuraRemaining() <= EPSILON) return TickResult.EMPTY;
        double speed = active.value().consumeSpeed().evaluate(context);
        if (!Double.isFinite(speed) || speed <= 0.0D) return TickResult.INVALID;
        double before = spirit.itemAuraRemaining();
        double consumed = Math.min(before, speed);
        spirit.setItemAuraRemaining(before - consumed <= EPSILON ? 0.0D : before - consumed);
        boolean exhausted = spirit.itemAuraRemaining() <= EPSILON;
        if (exhausted) active.value().exhaustedAction().execute(entity, context);
        return new TickResult(consumed, exhausted, true);
    }

    private static LoadedFuel consumeHeldItem(LivingEntity entity, FormulaContext context) {
        ItemStack main = entity.getMainHandItem();
        Holder<ItemAura> holder = find(entity, main).orElse(null);
        LoadedFuel fuel = consume(entity, holder, main, context);
        if (fuel != null) return fuel;
        ItemStack off = entity.getOffhandItem();
        holder = find(entity, off).orElse(null);
        fuel = consume(entity, holder, off, context);
        return fuel;
    }

    private static LoadedFuel consume(LivingEntity entity, Holder<ItemAura> holder, ItemStack stack, FormulaContext context) {
        if (holder == null) return null;
        double total = holder.value().totalAura().evaluate(context);
        if (!Double.isFinite(total) || total <= 0.0D) return null;
        stack.shrink(1);
        holder.value().resultStack().ifPresent(template -> {
            ItemStack result = template.create();
            if (result.isEmpty()) return;
            if (entity instanceof Player player) player.getInventory().placeItemBackInInventory(result);
            else if (entity.level() instanceof ServerLevel level) entity.spawnAtLocation(level, result);
        });
        return new LoadedFuel(holder, total);
    }

    public record TickResult(double consumed, boolean exhausted, boolean active) {
        private static final TickResult EMPTY = new TickResult(0.0D, false, false);
        private static final TickResult INVALID = new TickResult(0.0D, false, true);
    }

    private record LoadedFuel(Holder<ItemAura> aura, double total) {
    }
}
