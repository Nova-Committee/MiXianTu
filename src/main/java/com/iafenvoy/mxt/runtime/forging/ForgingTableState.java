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
 * Persistable, inventory-neutral forging session state owned by a forge table block entity.
 *
 * <p>The container lives on the block entity; this class owns the selected blueprint id, the
 * resolved {@link ForgingPlan}, the live session snapshot and the exact stacks locked away when
 * the session started. Everything else the session needs at settlement time (result item, quality
 * thresholds, failure settlement) is read back from the live blueprint definition, mirroring how
 * the alchemy workstation state stores a recipe id instead of the whole recipe.</p>
 *
 * <p>The plan is snapshotted on purpose: it carries {@code optimalSteps}, and recomputing it
 * after a datapack reload would change the extra-step count of a session already in progress.</p>
 *
 * <p>The consumed snapshot exists so a cancel or a failure can return exactly what was taken,
 * including component data, without trusting the container to still hold it.</p>
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

    /**
     * Whether a session is running.
     *
     * <p>Deliberately "a session exists" rather than "a blueprint is locked". Every state this class produces
     * has the two together - {@link #lock} sets both, {@link #clear} drops both - but they are not the same
     * question, and the difference is what a locked surface is actually protecting: a session holds materials
     * it consumed, and it is those that have to stay put until it settles. Reading the blueprint instead is how
     * a table with nothing to strike, nothing to settle and nothing to cancel still refused to accept or release
     * anything.</p>
     */
    public boolean active() {
        return this.session != null;
    }

    public Optional<Identifier> blueprint() {
        return Optional.ofNullable(this.blueprint);
    }

    /**
     * The plan frozen when the session started, still valid after a datapack reload.
     */
    public Optional<ForgingPlan> plan() {
        return Optional.ofNullable(this.plan);
    }

    public Optional<Snapshot> session() {
        return Optional.ofNullable(this.session);
    }

    /**
     * The material stacks removed from the container when the session started.
     */
    public List<ItemStack> consumed() {
        return this.consumed;
    }

    /**
     * Who opened the shared session. Every player may keep striking; this is informational only.
     */
    public Optional<UUID> starter() {
        return Optional.ofNullable(this.starter);
    }

    /**
     * Opens a session. The caller has already removed {@code consumed} from the block entity container.
     */
    public void lock(Identifier blueprint, ForgingPlan plan, ForgingSession value, List<ItemStack> consumed, UUID starter) {
        if (this.active()) throw new IllegalStateException("Forging session already active");
        this.blueprint = blueprint;
        this.plan = plan;
        this.session = value.snapshot();
        this.consumed = copyStacks(consumed);
        this.starter = starter;
    }

    /**
     * Replaces the live session snapshot after a strike.
     */
    public void update(ForgingSession value) {
        if (!this.active()) throw new IllegalStateException("No forging session is active");
        this.session = value.snapshot();
    }

    /**
     * Fully resets the workstation state.
     *
     * <p>The only reset there is, and deliberately so. Dropping the session while keeping the blueprint - which
     * this class used to offer, so that a retry would not need a re-pick - produces a shape with no plan and no
     * history behind the blueprint. That shape is rejected by this class's own codec, so it cannot survive a
     * save, and it is <em>also</em> still a locked surface: {@link #active()} used to read the blueprint, so the
     * inputs stayed frozen and the blueprint slots stayed shut for a session that no longer existed and could no
     * longer be cancelled. The table was simply stuck. "Do not make the player pick again" is the client's job
     * anyway, and it already does it: the pick is UI state that outlives the session.
     */
    public void clear() {
        this.blueprint = null;
        this.plan = null;
        this.session = null;
        this.consumed = new ArrayList<>();
        this.starter = null;
    }

    /**
     * Copies decoded state into this instance. Block entities hold a final field, so the codec
     * result is copied in rather than replacing the reference.
     */
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
