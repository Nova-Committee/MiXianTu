package com.iafenvoy.mxt.runtime.resulting;

import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

public class ResultWithData<T> extends Result {
    private final T data;

    protected ResultWithData(T data, @Nullable Failure failure, @Nullable Identifier failedResource) {
        super(failure, failedResource);
        this.data = data;
    }

    public static <T> ResultWithData<T> success(T data) {
        return new ResultWithData<>(data, null, null);
    }

    public static <T> ResultWithData<T> failure(T data, Failure failure) {
        return new ResultWithData<>(data, failure, null);
    }

    public static <T> ResultWithData<T> failure(T data, Failure failure, @Nullable Identifier failedResource) {
        return new ResultWithData<>(data, failure, failedResource);
    }

    public T getData() {
        return this.data;
    }
}
