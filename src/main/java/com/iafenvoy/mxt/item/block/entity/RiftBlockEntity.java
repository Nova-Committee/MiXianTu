package com.iafenvoy.mxt.item.block.entity;

import com.iafenvoy.mxt.registry.MxtBlockEntities;
import com.iafenvoy.mxt.runtime.rift.RiftColors;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;

/**
 * State of a single rift block: where it leads and what colour it is. No direction is stored: the rift is drawn as
 * a point with lines to the rifts around it, so which ones it links to follows from the block position alone.
 */
public class RiftBlockEntity extends BlockEntity {
    // Without an override the colour follows the target dimension; see RiftColors.
    private Identifier target = Level.OVERWORLD.identifier();
    private int color = RiftColors.AUTO;

    public RiftBlockEntity(BlockPos pos, BlockState state) {
        this(MxtBlockEntities.RIFT.get(), pos, state);
    }

    protected RiftBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public Identifier target() {
        return this.target;
    }

    // The colour override, or RiftColors.AUTO when the colour follows the target dimension.
    public int color() {
        return this.color;
    }

    public void setTarget(Identifier target) {
        this.target = target;
        this.markChangedAndSync();
    }

    public void setColor(int color) {
        this.color = color;
        this.markChangedAndSync();
    }

    // Both fields at once, so placing or reconfiguring a rift syncs one update instead of two.
    public void configure(Identifier target, int color) {
        this.target = target;
        this.color = color;
        this.markChangedAndSync();
    }

    public Vec3 center() {
        return new Vec3(0.5, 0.5, 0.5);
    }

    @Override
    protected void loadAdditional(@NonNull ValueInput input) {
        super.loadAdditional(input);
        input.read("target", Identifier.CODEC).ifPresent(value -> this.target = value);
        input.read("color", Codec.INT).ifPresent(value -> this.color = value);
    }

    @Override
    protected void saveAdditional(@NonNull ValueOutput output) {
        super.saveAdditional(output);
        output.store("target", Identifier.CODEC, this.target);
        if (this.color != RiftColors.AUTO) output.store("color", Codec.INT, this.color);
    }

    // The links and triangles are computed on the client from the block position, so a client only has to be told
    // the destination and the colour.
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public @NonNull CompoundTag getUpdateTag(@NonNull Provider registries) {
        return this.saveWithoutMetadata(registries);
    }

    private void markChangedAndSync() {
        this.setChanged();
        if (this.level != null && !this.level.isClientSide())
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
    }
}
