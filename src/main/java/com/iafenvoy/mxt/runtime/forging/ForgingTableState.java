package com.iafenvoy.mxt.runtime.forging;

import com.iafenvoy.mxt.runtime.forging.ForgingSession.Snapshot;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistable, inventory-neutral forging session state owned by a forge table block entity: the selected
 * blueprint id, the resolved {@link ForgingPlan}, the live session snapshot and the stacks locked away when the
 * session started. The plan is snapshotted because it carries {@code optimalSteps}, and recomputing it after a
 * datapack reload would change the extra-step count of a session already in progress.
 */
public final class ForgingTableState {
    public static final MapCodec<ForgingTableState> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Identifier.CODEC.optionalFieldOf("blueprint").forGetter(ForgingTableState::blueprint),
            ForgingPlan.CODEC.optionalFieldOf("plan").forGetter(ForgingTableState::plan),
            Snapshot.CODEC.optionalFieldOf("session").forGetter(ForgingTableState::session),
            ItemStack.CODEC.listOf().optionalFieldOf("consumed", List.of()).forGetter(ForgingTableState::consumed),
            UUIDUtil.CODEC.optionalFieldOf("starter").forGetter(ForgingTableState::starter)
    ).apply(i, ForgingTableState::new));
    public static final Codec<ForgingTableState> CODEC = MAP_CODEC.codec();

    private Identifier blueprint;
    private ForgingPlan plan;
    private Snapshot session;
    private List<ItemStack> consumed;
    private UUID starter;

    public ForgingTableState() {
        this(Optional.empty(), Optional.empty(), Optional.empty(), List.of(), Optional.empty());
    }

    private ForgingTableState(Optional<Identifier> blueprint, Optional<ForgingPlan> plan, Optional<Snapshot> session,
                              List<ItemStack> consumed, Optional<UUID> starter) {
        this.blueprint = blueprint.orElse(null);
        this.plan = plan.orElse(null);
        this.session = session.orElse(null);
        this.consumed = copyStacks(consumed);
        this.starter = starter.orElse(null);
        if (this.blueprint != null && (this.plan == null || this.session == null)) {
            throw new IllegalArgumentException("Incomplete forging table state");
        }
    }

    // Deliberately not the same question as whether a blueprint is locked: a session holds the materials it
    // consumed, and it is those that have to stay put until it settles.
    public boolean active() {
        return this.session != null;
    }

    public Optional<Identifier> blueprint() {
        return Optional.ofNullable(this.blueprint);
    }

    // Frozen when the session started, so it stays valid after a datapack reload.
    public Optional<ForgingPlan> plan() {
        return Optional.ofNullable(this.plan);
    }

    public Optional<Snapshot> session() {
        return Optional.ofNullable(this.session);
    }

    public List<ItemStack> consumed() {
        return this.consumed;
    }

    // Informational only: every player may keep striking the shared session.
    public Optional<UUID> starter() {
        return Optional.ofNullable(this.starter);
    }

    // The caller has already removed the consumed stacks from the block entity container.
    public void lock(Identifier blueprint, ForgingPlan plan, ForgingSession value, List<ItemStack> consumed, UUID starter) {
        if (this.active()) throw new IllegalStateException("Forging session already active");
        this.blueprint = blueprint;
        this.plan = plan;
        this.session = value.snapshot();
        this.consumed = copyStacks(consumed);
        this.starter = starter;
    }

    public void update(ForgingSession value) {
        if (!this.active()) throw new IllegalStateException("No forging session is active");
        this.session = value.snapshot();
    }

    // The only reset there is: dropping the session while keeping the blueprint would leave a shape with no
    // plan behind it, which this class's own codec rejects and which would keep the surface locked for nothing.
    public void clear() {
        this.blueprint = null;
        this.plan = null;
        this.session = null;
        this.consumed = new ArrayList<>();
        this.starter = null;
    }

    // Block entities hold a final field, so the codec result is copied in rather than replacing the reference.
    public void copyFrom(ForgingTableState other) {
        this.blueprint = other.blueprint;
        this.plan = other.plan;
        this.session = other.session;
        this.consumed = copyStacks(other.consumed);
        this.starter = other.starter;
    }

    private static List<ItemStack> copyStacks(List<ItemStack> values) {
        List<ItemStack> result = new ArrayList<>(values.size());
        for (ItemStack stack : values) {
            if (!stack.isEmpty()) result.add(stack.copy());
        }
        return result;
    }
}
