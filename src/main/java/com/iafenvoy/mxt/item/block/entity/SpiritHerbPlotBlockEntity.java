package com.iafenvoy.mxt.item.block.entity;

import com.iafenvoy.mxt.data.alchemy.SpiritHerb;
import com.iafenvoy.mxt.registry.MxtBlockEntities;
import com.iafenvoy.mxt.runtime.alchemy.SpiritHerbGrowthService;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Locale;

/**
 * One soil bed and at most one plant. The saved seed is the stack that was sown, not a freshly built one, so a
 * definition that no longer loads can still be pulled.
 */
public final class SpiritHerbPlotBlockEntity extends BlockEntity {
    public enum Pause {
        NONE, MISSING, CAPPED, CONDITION, AURA, GROWTH;

        public static final Codec<Pause> CODEC = Codec.STRING.comapFlatMap(SpiritHerbPlotBlockEntity::decodePause,
                value -> value.name().toLowerCase(Locale.ROOT));
    }

    @Nullable
    private Holder<SpiritHerb> herb;
    private ItemStack seed = ItemStack.EMPTY;
    private float progress;
    private int remainder;
    private Pause pause = Pause.NONE;

    public SpiritHerbPlotBlockEntity(BlockPos pos, BlockState state) {
        super(MxtBlockEntities.SPIRIT_HERB_PLOT.get(), pos, state);
    }

    public static void serverTick(ServerLevel level, BlockPos pos, BlockState state, SpiritHerbPlotBlockEntity plot) {
        SpiritHerbGrowthService.tick(level, pos, plot);
    }

    public boolean occupied() {
        return this.herb != null || !this.seed.isEmpty();
    }

    public @Nullable Holder<SpiritHerb> herb() {
        return this.herb;
    }

    public ItemStack seed() {
        return this.seed;
    }

    public float progress() {
        return this.progress;
    }

    public int age() {
        return (int) Math.floor(this.progress);
    }

    public int remainder() {
        return this.remainder;
    }

    public Pause pause() {
        return this.pause;
    }

    public void plant(Holder<SpiritHerb> herb, ItemStack seed) {
        this.herb = herb;
        this.seed = seed.copyWithCount(1);
        this.progress = 0.0F;
        this.remainder = 0;
        this.pause = Pause.NONE;
        this.sync();
    }

    /**
     * Restores a plant that was already sown, including one whose definition no longer loads.
     */
    public void restore(@Nullable Holder<SpiritHerb> herb, ItemStack seed, float progress, int remainder, Pause pause) {
        this.herb = herb;
        this.seed = seed.isEmpty() ? ItemStack.EMPTY : seed.copyWithCount(1);
        this.progress = Math.max(0.0F, progress);
        this.remainder = Math.max(0, remainder);
        this.pause = pause == null ? Pause.NONE : pause;
        this.sync();
    }

    public void setGrowth(float progress, int remainder, Pause pause) {
        this.progress = progress;
        this.remainder = remainder;
        this.pause = pause;
    }

    public void clear() {
        this.herb = null;
        this.seed = ItemStack.EMPTY;
        this.progress = 0.0F;
        this.remainder = 0;
        this.pause = Pause.NONE;
    }

    public void sync() {
        this.setChanged();
        if (this.level != null && !this.level.isClientSide())
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (this.level instanceof ServerLevel server) SpiritHerbGrowthService.dropOnRemove(server, pos, this);
    }

    @Override
    protected void loadAdditional(@NonNull ValueInput input) {
        super.loadAdditional(input);
        this.herb = input.read("herb", SpiritHerb.CODEC).orElse(null);
        this.seed = input.read("seed", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
        this.progress = input.read("progress", Codec.FLOAT).orElse(0.0F);
        this.remainder = input.read("remainder", Codec.INT).orElse(0);
        this.pause = input.read("pause", Pause.CODEC).orElse(Pause.NONE);
    }

    @Override
    protected void saveAdditional(@NonNull ValueOutput output) {
        super.saveAdditional(output);
        if (this.herb != null) output.store("herb", SpiritHerb.CODEC, this.herb);
        if (!this.seed.isEmpty()) output.store("seed", ItemStack.CODEC, this.seed);
        output.store("progress", Codec.FLOAT, this.progress);
        output.store("remainder", Codec.INT, this.remainder);
        output.store("pause", Pause.CODEC, this.pause);
    }

    // Plots saved while disabled tags existed stored this pause. The definition is simply absent now.
    private static DataResult<Pause> decodePause(String raw) {
        String name = raw.toUpperCase(Locale.ROOT);
        if (name.equals("DISABLED")) return DataResult.success(Pause.MISSING);
        try {
            return DataResult.success(Pause.valueOf(name));
        } catch (IllegalArgumentException exception) {
            return DataResult.error(() -> "Unknown herb pause " + raw);
        }
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public @NonNull CompoundTag getUpdateTag(@NonNull Provider registries) {
        return this.saveWithoutMetadata(registries);
    }
}
