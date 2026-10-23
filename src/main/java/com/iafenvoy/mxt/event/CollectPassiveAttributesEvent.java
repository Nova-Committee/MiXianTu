package com.iafenvoy.mxt.event;

import com.iafenvoy.mxt.data.AttributeEntry;
import com.iafenvoy.mxt.runtime.Sources;
import com.iafenvoy.mxt.runtime.ability.PassiveAttributeService;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.event.entity.living.LivingEvent;

import java.util.LinkedList;
import java.util.List;

/**
 * Asks every declaring system for the passive attribute entries of the definitions its body carries. The modifiers are
 * rebuilt from the whole answer, so an entry a system stops naming is removed.
 */
public final class CollectPassiveAttributesEvent extends LivingEvent {
    private final List<PassiveAttributeService.Entry> entries = new LinkedList<>();

    public CollectPassiveAttributesEvent(LivingEntity entity) {
        super(entity);
    }

    public void add(Sources.Declaration kind, Identifier source, List<AttributeEntry> values) {
        for (int index = 0; index < values.size(); index++)
            this.entries.add(new PassiveAttributeService.Entry(kind, source, index, values.get(index)));
    }

    public List<PassiveAttributeService.Entry> getEntries() {
        return List.copyOf(this.entries);
    }
}
