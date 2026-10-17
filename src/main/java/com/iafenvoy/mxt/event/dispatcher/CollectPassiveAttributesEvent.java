package com.iafenvoy.mxt.event.dispatcher;

import com.iafenvoy.mxt.data.AttributeEntry;
import com.iafenvoy.mxt.runtime.Sources;
import com.iafenvoy.mxt.runtime.ability.PassiveAttributeService;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.event.entity.living.LivingEvent;

import java.util.List;

/**
 * Asks every declaring system for the passive attribute entries of the definitions its body carries. The modifiers are
 * rebuilt from the whole answer, so an entry a system stops naming is removed.
 */
public final class CollectPassiveAttributesEvent extends LivingEvent {
    private final List<PassiveAttributeService.Entry> collector;

    public CollectPassiveAttributesEvent(LivingEntity entity, List<PassiveAttributeService.Entry> collector) {
        super(entity);
        this.collector = collector;
    }

    public void add(Sources.Declaration kind, Identifier source, List<AttributeEntry> values) {
        for (int index = 0; index < values.size(); index++)
            this.collector.add(new PassiveAttributeService.Entry(kind, source, index, values.get(index)));
    }
}
