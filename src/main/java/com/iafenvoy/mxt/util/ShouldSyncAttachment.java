package com.iafenvoy.mxt.util;

/**
 * Base class for mutable entity attachments. A mutation marks the attachment
 * locally; {@code MxtAttachments} consumes that flag once during the server tick.
 */
public abstract class ShouldSyncAttachment {
    private boolean dirty;

    public final void markDirty() {
        this.dirty = true;
    }

    public final boolean checkDirty() {
        boolean b = this.dirty;
        this.dirty = false;
        return b;
    }
}
