package com.iafenvoy.mxt.event;

import com.iafenvoy.mxt.attachment.SectAttachment;
import com.iafenvoy.mxt.data.Sect;
import net.minecraft.core.Holder;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

public abstract class SectEvent extends Event {
    private final SectAttachment data;
    private final Holder<Sect> sect;

    protected SectEvent(SectAttachment data, Holder<Sect> sect) {
        this.data = data;
        this.sect = sect;
    }

    public SectAttachment data() {
        return this.data;
    }

    public Holder<Sect> sect() {
        return this.sect;
    }

    public static final class JoinPre extends SectEvent implements ICancellableEvent {
        public JoinPre(SectAttachment data, Holder<Sect> sect) {
            super(data, sect);
        }
    }

    public static final class JoinPost extends SectEvent {
        public JoinPost(SectAttachment data, Holder<Sect> sect) {
            super(data, sect);
        }
    }

    public static final class LeavePre extends SectEvent implements ICancellableEvent {
        public LeavePre(SectAttachment data, Holder<Sect> sect) {
            super(data, sect);
        }
    }

    public static final class LeavePost extends SectEvent {
        public LeavePost(SectAttachment data, Holder<Sect> sect) {
            super(data, sect);
        }
    }

    public static final class PromotePre extends SectEvent implements ICancellableEvent {
        public PromotePre(SectAttachment data, Holder<Sect> sect) {
            super(data, sect);
        }
    }

    public static final class PromotePost extends SectEvent {
        public PromotePost(SectAttachment data, Holder<Sect> sect) {
            super(data, sect);
        }
    }
}
