package com.iafenvoy.mxt.util;

public interface ChangeTrackable {
    void markDirty();

    boolean checkDirty();
}
