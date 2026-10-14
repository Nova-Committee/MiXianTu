package com.iafenvoy.mxt.util;

/**
 * Base class for mutable entity attachments. A mutation marks the attachment
 * locally; {@code MxtAttachments} consumes that flag once during the server tick.
 */
public abstract class AbstractChangeTrackable implements ChangeTrackable {
    private boolean dirty;

    @Override
    public final void markDirty() {
        this.dirty = true;
    }

    @Override
    public boolean checkDirty() {
        boolean b = this.dirty;
        this.dirty = false;
        return b;
    }
}
