package com.iafenvoy.mxt.event;

import com.iafenvoy.mxt.attachment.SectComponent;
import com.iafenvoy.mxt.data.Sect;
import net.minecraft.core.Holder;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

public abstract class SectEvent extends Event {
    private final SectComponent data;
    private final Holder<Sect> sect;

    protected SectEvent(SectComponent data, Holder<Sect> sect) {
        this.data = data;
        this.sect = sect;
    }

    public SectComponent data() {
        return this.data;
    }

    public Holder<Sect> sect() {
        return this.sect;
    }

    public static final class JoinPre extends SectEvent implements ICancellableEvent {
        public JoinPre(SectComponent data, Holder<Sect> sect) {
            super(data, sect);
        }
    }

    public static final class JoinPost extends SectEvent {
        public JoinPost(SectComponent data, Holder<Sect> sect) {
            super(data, sect);
        }
    }

    public static final class LeavePre extends SectEvent implements ICancellableEvent {
        public LeavePre(SectComponent data, Holder<Sect> sect) {
            super(data, sect);
        }
    }

    public static final class LeavePost extends SectEvent {
        public LeavePost(SectComponent data, Holder<Sect> sect) {
            super(data, sect);
        }
    }

    public static final class PromotePre extends SectEvent implements ICancellableEvent {
        public PromotePre(SectComponent data, Holder<Sect> sect) {
            super(data, sect);
        }
    }

    public static final class PromotePost extends SectEvent {
        public PromotePost(SectComponent data, Holder<Sect> sect) {
            super(data, sect);
        }
    }
}
