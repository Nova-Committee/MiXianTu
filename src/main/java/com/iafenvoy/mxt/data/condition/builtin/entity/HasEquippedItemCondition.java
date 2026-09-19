package com.iafenvoy.mxt.data.condition.builtin.entity;

import com.iafenvoy.mxt.compat.CuriosIntegration;
import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.condition.ItemCondition;
import com.iafenvoy.mxt.data.context.condition.EntityConditionContext;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NonNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Whether the entity wears or holds an item that satisfies an item condition. A passive that should only count
 * while the holder is equipped a certain way is written with this and an item condition such as {@code mxt:item_tag}
 * or {@code mxt:item_matcher}, which is the shape the equipment-side abilities already use for their own gating.
 *
 * <p>A slot is named either by a vanilla equipment slot or, for the Curios slots that have no vanilla counterpart,
 * by {@code curios:<slot id>}. Naming none asks every vanilla slot and every Curios slot, which is what "carrying
 * something like this" means for a passive; naming one asks only that slot, so an {@code mxt:or} of several slots
 * is not needed to express "either hand". An unknown name simply never matches.</p>
 */
public record HasEquippedItemCondition(ItemCondition itemCondition, List<String> slots) implements EntityCondition {
    public static final MapCodec<HasEquippedItemCondition> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            ItemCondition.optionalCodec("item_condition").forGetter(HasEquippedItemCondition::itemCondition),
            Codec.STRING.listOf().optionalFieldOf("slots", List.of()).forGetter(HasEquippedItemCondition::slots)
    ).apply(i, HasEquippedItemCondition::new));
    /**
     * The vanilla slots by their own names, resolved once: the codec carries names, and a condition is evaluated
     * every tick for a passive.
     */
    private static final Map<String, EquipmentSlot> VANILLA_SLOTS = vanillaSlots();
    private static final String CURIOS_PREFIX = "curios:";

    @Override
    public boolean test(@NonNull EntityConditionContext ctx) {
        Entity entity = ctx.entity();
        if (!(entity instanceof LivingEntity living)) return false;
        if (this.slots.isEmpty()) {
            for (EquipmentSlot slot : EquipmentSlot.values())
                if (matches(living, living.getItemBySlot(slot), ctx)) return true;
            return CuriosIntegration.equipped(living).stream().anyMatch(stack -> matches(living, stack, ctx));
        }
        for (String name : this.slots) {
            if (name.startsWith(CURIOS_PREFIX)) {
                if (CuriosIntegration.equippedIn(living, name.substring(CURIOS_PREFIX.length())).stream()
                        .anyMatch(stack -> matches(living, stack, ctx))) return true;
                continue;
            }
            EquipmentSlot slot = VANILLA_SLOTS.get(name);
            if (slot != null && matches(living, living.getItemBySlot(slot), ctx)) return true;
        }
        return false;
    }

    private boolean matches(LivingEntity entity, ItemStack stack, EntityConditionContext ctx) {
        return !stack.isEmpty() && this.itemCondition.test(entity, stack, ctx);
    }

    private static Map<String, EquipmentSlot> vanillaSlots() {
        Map<String, EquipmentSlot> slots = new HashMap<>();
        for (EquipmentSlot slot : EquipmentSlot.values()) slots.put(slot.getName(), slot);
        return Map.copyOf(slots);
    }

    @Override
    public @NonNull MapCodec<HasEquippedItemCondition> codec() {
        return CODEC;
    }
}
