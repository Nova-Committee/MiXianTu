package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.timeline.ActionEntry;
import com.iafenvoy.mxt.data.timeline.IdleEntry;
import com.iafenvoy.mxt.data.timeline.TimelineEntry;
import com.iafenvoy.mxt.data.timeline.WaitForEntry;
import com.mojang.serialization.MapCodec;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The built-in timeline entries. A datapack selects one by writing its id in an entry's {@code type}.
 */
@SuppressWarnings("unused")
public final class MxtTimelineEntries {
    public static final DeferredRegister<MapCodec<? extends TimelineEntry>> REGISTRY = DeferredRegister.create(MxtRegistries.TIMELINE_ENTRY_TYPE, MiXianTu.MOD_ID);

    public static final DeferredHolder<MapCodec<? extends TimelineEntry>, MapCodec<ActionEntry>> ACTION = REGISTRY.register("action", () -> ActionEntry.CODEC);
    public static final DeferredHolder<MapCodec<? extends TimelineEntry>, MapCodec<IdleEntry>> IDLE = REGISTRY.register("idle", () -> IdleEntry.CODEC);
    public static final DeferredHolder<MapCodec<? extends TimelineEntry>, MapCodec<WaitForEntry>> WAIT_FOR = REGISTRY.register("wait_for", () -> WaitForEntry.CODEC);

    private MxtTimelineEntries() {
    }
}
