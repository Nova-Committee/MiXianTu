package com.iafenvoy.mxt.runtime.forging;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.forging.ForgingBlueprint;
import com.iafenvoy.mxt.runtime.forging.ForgingSession.Snapshot;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.UUIDUtil;
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
    public static final Codec<ForgingTableState> CODEC = RecordCodecBuilder.create(i -> i.group(
            ForgingBlueprint.CODEC.optionalFieldOf("blueprint").forGetter(ForgingTableState::blueprint),
            ForgingPlan.CODEC.optionalFieldOf("plan").forGetter(ForgingTableState::plan),
            Snapshot.CODEC.optionalFieldOf("session").forGetter(ForgingTableState::session),
            ItemStack.CODEC.listOf().optionalFieldOf("consumed", List.of()).forGetter(ForgingTableState::consumed),
            UUIDUtil.CODEC.optionalFieldOf("starter").forGetter(ForgingTableState::starter)
    ).apply(i, ForgingTableState::new));

    private Holder<ForgingBlueprint> blueprint;
    private ForgingPlan plan;
    private Snapshot session;
    private List<ItemStack> consumed;
    private UUID starter;

    public ForgingTableState() {
        this(Optional.empty(), Optional.empty(), Optional.empty(), List.of(), Optional.empty());
    }

    private ForgingTableState(Optional<Holder<ForgingBlueprint>> blueprint, Optional<ForgingPlan> plan, Optional<Snapshot> session, List<ItemStack> consumed, Optional<UUID> starter) {
        this.blueprint = blueprint.orElse(null);
        this.plan = plan.orElse(null);
        this.session = session.orElse(null);
        this.consumed = copyStacks(consumed);
        this.starter = starter.orElse(null);
        // A blueprint without its plan or session is a save whose plan could not be rebuilt, which is what a pack
        // that no longer provides one of the frozen methods leaves behind: the session is dropped so the table
        // unlocks instead of failing to load. Its consumed stacks are kept here and handed back to the input slots
        // by ForgingWorkstationService#returnStrandedMaterials once the block entity has its level - no return
        // path can settle a state that is no longer active, so nothing else would ever free them.
        if (this.blueprint != null && (this.plan == null || this.session == null)) {
            MiXianTu.LOGGER.warn("Dropping an incomplete forging session for {}: its plan could not be rebuilt", this.blueprintId());
            this.blueprint = null;
            this.plan = null;
            this.session = null;
        }
    }

    // Deliberately not the same question as whether a blueprint is locked: a session holds the materials it
    // consumed, and it is those that have to stay put until it settles.
    public boolean active() {
        return this.session != null;
    }

    public Optional<Holder<ForgingBlueprint>> blueprint() {
        return Optional.ofNullable(this.blueprint);
    }

    private String blueprintId() {
        return this.blueprint.unwrapKey().map(key -> key.identifier().toString()).orElse("?");
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

    // The load-time return of a degraded session keeps whatever the input slots could not take, since a chunk
    // load has no player to hand it to.
    public void retainConsumed(List<ItemStack> leftover) {
        this.consumed = copyStacks(leftover);
    }

    // Informational only: every player may keep striking the shared session.
    public Optional<UUID> starter() {
        return Optional.ofNullable(this.starter);
    }

    // The caller has already removed the consumed stacks from the block entity container.
    public void lock(Holder<ForgingBlueprint> blueprint, ForgingPlan plan, ForgingSession value, List<ItemStack> consumed, UUID starter) {
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
