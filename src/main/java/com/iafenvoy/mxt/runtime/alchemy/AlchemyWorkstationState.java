package com.iafenvoy.mxt.runtime.alchemy;

import com.iafenvoy.mxt.runtime.alchemy.AlchemySession.Snapshot;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

/**
 * Persistable inventory-neutral state for a cauldron or other alchemy workstation.
 * A concrete block entity owns insertion and extraction; this class owns only server-side
 * recipe inputs, the active session snapshot, and completed output stacks.
 */
public final class AlchemyWorkstationState {
    public static final MapCodec<AlchemyWorkstationState> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            ItemStack.CODEC.listOf().optionalFieldOf("inputs", List.of()).forGetter(AlchemyWorkstationState::inputs),
            ItemStack.CODEC.listOf().optionalFieldOf("outputs", List.of()).forGetter(AlchemyWorkstationState::outputs),
            Snapshot.CODEC.optionalFieldOf("session").forGetter(AlchemyWorkstationState::session)
    ).apply(i, AlchemyWorkstationState::new));
    public static final Codec<AlchemyWorkstationState> CODEC = MAP_CODEC.codec();

    private final List<ItemStack> inputs;
    private final List<ItemStack> outputs;
    private Snapshot session;

    public AlchemyWorkstationState() {
        this(List.of(), List.of(), Optional.empty());
    }

    private AlchemyWorkstationState(List<ItemStack> inputs, List<ItemStack> outputs, Optional<Snapshot> session) {
        this.inputs = new LinkedList<>(copyStacks(inputs));
        this.outputs = new LinkedList<>(copyStacks(outputs));
        this.session = session.orElse(null);
    }

    public List<ItemStack> inputs() {
        return this.inputs;
    }

    public List<ItemStack> outputs() {
        return this.outputs;
    }

    public Optional<Snapshot> session() {
        return Optional.ofNullable(this.session);
    }

    public boolean active() {
        return this.session != null && !this.session.complete();
    }

    public void setInputs(List<ItemStack> values) {
        if (this.active()) throw new IllegalStateException("Cannot change alchemy inputs during an active session");
        this.inputs.clear();
        this.inputs.addAll(copyStacks(values));
    }

    public void lock(AlchemySession value) {
        if (this.active()) throw new IllegalStateException("Alchemy session already active");
        this.inputs.clear();
        this.session = value.snapshot();
    }

    public void update(AlchemySession value) {
        this.session = value.snapshot();
    }

    public void addOutputs(List<ItemStack> values) {
        this.outputs.addAll(copyStacks(values));
    }

    public List<ItemStack> takeOutputs() {
        List<ItemStack> result = copyStacks(this.outputs);
        this.outputs.clear();
        return result;
    }

    public void clearSession() {
        this.session = null;
    }

    private static List<ItemStack> copyStacks(List<ItemStack> values) {
        return values.stream().filter(stack -> !stack.isEmpty()).map(ItemStack::copy).toList();
    }
}
