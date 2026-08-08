package com.iafenvoy.mxt.event;

import com.iafenvoy.mxt.attachment.SectData;
import com.iafenvoy.mxt.data.Sect;
import net.minecraft.core.Holder;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

public abstract class SectEvent extends Event {
    private final SectData data;
    private final Holder<Sect> sect;

    protected SectEvent(SectData data, Holder<Sect> sect) {
        this.data = data;
        this.sect = sect;
    }

    public SectData data() {
        return this.data;
    }

    public Holder<Sect> sect() {
        return this.sect;
    }

    public static final class JoinPre extends SectEvent implements ICancellableEvent {
        public JoinPre(SectData data, Holder<Sect> sect) {
            super(data, sect);
        }
    }

    public static final class JoinPost extends SectEvent {
        public JoinPost(SectData data, Holder<Sect> sect) {
            super(data, sect);
        }
    }

    public static final class LeavePre extends SectEvent implements ICancellableEvent {
        public LeavePre(SectData data, Holder<Sect> sect) {
            super(data, sect);
        }
    }

    public static final class LeavePost extends SectEvent {
        public LeavePost(SectData data, Holder<Sect> sect) {
            super(data, sect);
        }
    }

    public static final class PromotePre extends SectEvent implements ICancellableEvent {
        public PromotePre(SectData data, Holder<Sect> sect) {
            super(data, sect);
        }
    }

    public static final class PromotePost extends SectEvent {
        public PromotePost(SectData data, Holder<Sect> sect) {
            super(data, sect);
        }
    }
}
