package com.iafenvoy.mxt.runtime.cultivation;

import com.iafenvoy.mxt.attachment.SpiritData;
import com.iafenvoy.mxt.event.LifeSpanEndEvent;
import com.iafenvoy.mxt.event.LifeSpanEndEvent.Post;
import com.iafenvoy.mxt.event.LifeSpanEndEvent.Pre;
import com.iafenvoy.mxt.registry.MxtAttachments;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Decrements optional lifespan state and leaves death/reincarnation to content listeners.
 */
public final class LifeSpanService {
    private LifeSpanService() {
    }

    public static boolean tick(Entity entity) {
        SpiritData spirit = entity.getData(MxtAttachments.SPIRIT_DATA);
        if (spirit.lifespanRemaining() < 0L) return false;
        if (spirit.lifespanRemaining() > 0L) spirit.setLifespanRemaining(spirit.lifespanRemaining() - 1L);
        if (spirit.lifespanRemaining() != 0L) return false;
        Pre event = new Pre(entity, spirit);
        if (NeoForge.EVENT_BUS.post(event).isCanceled()) {
            spirit.setLifespanRemaining(-1L);
            return false;
        }
        NeoForge.EVENT_BUS.post(new Post(entity, spirit));
        if (spirit.lifespanRemaining() == 0L) spirit.setLifespanRemaining(-1L);
        return true;
    }
}
