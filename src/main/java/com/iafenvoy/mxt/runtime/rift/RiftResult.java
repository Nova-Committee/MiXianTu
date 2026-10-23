package com.iafenvoy.mxt.runtime.rift;

import com.iafenvoy.mxt.runtime.resulting.Failure;
import com.iafenvoy.mxt.runtime.resulting.Failures;
import com.iafenvoy.mxt.runtime.resulting.Result;
import org.jetbrains.annotations.Nullable;

public final class RiftResult extends Result {
    private final @Nullable RiftRecord record;
    private final String detail;

    private RiftResult(@Nullable Failure failure, @Nullable RiftRecord record, String detail) {
        super(failure, null);
        this.record = record;
        this.detail = detail;
    }

    public static RiftResult accepted(RiftRecord record) {
        return new RiftResult(null, record, "");
    }

    public static RiftResult rejected(String detail) {
        return new RiftResult(Failures.INVALID_VALUE, null, detail);
    }

    public @Nullable RiftRecord record() {
        return this.record;
    }

    public String detail() {
        return this.detail;
    }
}
