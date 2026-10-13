package com.iafenvoy.mxt.util.formula.variable;

import com.iafenvoy.mxt.data.cultivation.Element;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.runtime.cultivation.Elements;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.FormulaNames;
import com.iafenvoy.mxt.util.formula.FormulaVariable;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

// Health, vanilla experience level, one name per resource and per attribute the entity has, and one name per
// element answering 1 or 0: all a formula may ask about an element is whether this entity is one of its
// cultivators - the relations themselves are the damage pipeline's business, read the same way there.
public final class EntityVariable implements FormulaVariable {
    private static final Map<EntityType<?>, Set<Holder<Attribute>>> SYNCABLE_ATTRIBUTES = new ConcurrentHashMap<>();

    private final String prefix;
    private final Set<String> prefixes;

    public EntityVariable(String prefix) {
        this.prefix = prefix;
        this.prefixes = Set.of(prefix);
    }

    @Override
    public Set<String> names() {
        return Set.of();
    }

    @Override
    public Set<String> prefixes() {
        return this.prefixes;
    }

    @Override
    public double value(String key, String suffix, FormulaContext context) {
        Entity entity = this.prefix.equals(key) ? context.caster() : context.target();
        if (entity == null) return Double.NaN;
        return switch (suffix) {
            case "health" -> entity instanceof LivingEntity living ? (double) living.getHealth() : Double.NaN;
            case "max_health" -> entity instanceof LivingEntity living ? (double) living.getMaxHealth() : Double.NaN;
            case "level" -> entity instanceof ServerPlayer player ? (double) player.experienceLevel : 0.0D;
            case "element_count" -> (double) Elements.of(entity).size();
            default -> state(entity, suffix);
        };
    }

    // Attributes are asked first, because the context used to expose resources before attributes.
    private static double state(Entity entity, String field) {
        if (!(entity instanceof LivingEntity living)) return Double.NaN;
        Holder<Attribute> attribute = FormulaNames.attribute(field);
        if (attribute != null) {
            AttributeInstance instance = living.getAttributes().getInstance(attribute);
            if (instance == null) return 0.0D;
            if (living.level().isClientSide() && !synchronised(living, attribute)) return 0.0D;
            return instance.getValue();
        }
        Holder<Resource> resource = FormulaNames.resource(living.level().registryAccess(), field);
        if (resource != null) return living.getData(MxtAttachments.RESOURCE_HOLDER).get(resource);
        Holder<Element> element = FormulaNames.element(living.level().registryAccess(), field);
        if (element == null) return Double.NaN;
        return Elements.of(living).contains(element) ? 1.0D : 0.0D;
    }

    // The client only holds the attributes it needs, so a formula must not read a hidden attribute as if it
    // were up to date.
    private static boolean synchronised(LivingEntity entity, Holder<Attribute> attribute) {
        Set<Holder<Attribute>> syncable = SYNCABLE_ATTRIBUTES.get(entity.getType());
        if (syncable == null) {
            Set<Holder<Attribute>> collected = new HashSet<>();
            for (AttributeInstance instance : entity.getAttributes().getSyncableAttributes())
                collected.add(instance.getAttribute());
            syncable = Set.copyOf(collected);
            SYNCABLE_ATTRIBUTES.put(entity.getType(), syncable);
        }
        return syncable.contains(attribute);
    }
}
