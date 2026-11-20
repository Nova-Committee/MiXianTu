package com.iafenvoy.mxt.runtime.alchemy;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.DefaultDataComponentsBoundEvent;
import net.neoforged.neoforge.event.TagsUpdatedEvent;


@EventBusSubscriber
public final class HerbDataValidation {
    // Both events run in updateComponentsAndStaticRegistryTags; item templates are usable only in the second.
    private static final ThreadLocal<TagsUpdatedEvent.ServerDataLoad> PENDING = new ThreadLocal<>();

    private HerbDataValidation() {
    }

    @SubscribeEvent
    public static void onServerDataLoad(TagsUpdatedEvent.ServerDataLoad event) {
        PENDING.set(event);
    }

    @SubscribeEvent
    public static void onComponentsBound(DefaultDataComponentsBoundEvent event) {
        if (event.getUpdateCause() != DefaultDataComponentsBoundEvent.UpdateCause.SERVER_DATA_LOAD) return;
        TagsUpdatedEvent.ServerDataLoad reload = PENDING.get();
        PENDING.remove();
        if (reload == null) return;
        SpiritHerbService.validateExclusiveClaims(reload.getRegistries());

    }


}
