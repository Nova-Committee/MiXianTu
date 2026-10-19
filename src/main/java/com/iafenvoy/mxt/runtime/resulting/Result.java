package com.iafenvoy.mxt.runtime.resulting;

import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * What an action answered when it refused: why, and which resource the reason was about. Going through is the same
 * type with both empty, so a caller reads any result the same way - {@code failure() == null} is the answer.
 *
 * <p>A class rather than a record on purpose: a result that also carries state of its own extends this and adds its
 * own fields, so the two questions above are answered in one place for every kind of result.
 */
public class Result {
    private final @Nullable Failure failure;
    private final @Nullable Identifier failedResource;

    protected Result(@Nullable Failure failure, @Nullable Identifier failedResource) {
        this.failure = failure;
        this.failedResource = failedResource;
    }

    public static Result success() {
        return new Result(null, null);
    }

    public static Result failure(Failure failure) {
        return new Result(failure, null);
    }

    public static Result failure(Failure failure, @Nullable Identifier failedResource) {
        return new Result(failure, failedResource);
    }

    public boolean isSuccess() {
        return this.failure == null;
    }

    public boolean isFailure() {
        return this.failure != null;
    }

    /**
     * Why it did not go through; empty when it did.
     */
    public @Nullable Failure failure() {
        return this.failure;
    }

    /**
     * Which resource the reason was about, for the reasons that name one.
     */
    public @Nullable Identifier failedResource() {
        return this.failedResource;
    }

    @Override
    public String toString() {
        return this.failure == null ? "Result[ok]" : "Result[failure=" + this.failure + ", resource=" + this.failedResource + "]";
    }
}
