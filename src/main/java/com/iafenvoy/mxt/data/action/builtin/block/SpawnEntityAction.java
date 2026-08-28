package com.iafenvoy.mxt.data.action.builtin.block;

import com.iafenvoy.mxt.data.context.action.BlockActionContext;

import com.iafenvoy.mxt.data.action.BlockAction;
import com.iafenvoy.mxt.data.action.EntityAction;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.RegistryFixedCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.TagValueInput;
import org.jspecify.annotations.NonNull;

import java.util.Optional;

/**
 * Spawns an entity at the acted block position and applies an optional entity action.
 */
public record SpawnEntityAction(Holder<EntityType<?>> entityType, Optional<CompoundTag> tag,
                                EntityAction entityAction) implements BlockAction {
    public static final MapCodec<SpawnEntityAction> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            RegistryFixedCodec.create(Registries.ENTITY_TYPE).fieldOf("entity_type").forGetter(SpawnEntityAction::entityType),
            CompoundTag.CODEC.optionalFieldOf("tag").forGetter(SpawnEntityAction::tag),
            EntityAction.optionalCodec("entity_action").forGetter(SpawnEntityAction::entityAction)
    ).apply(i, SpawnEntityAction::new));

    @Override
    public void execute(@NonNull BlockActionContext ctx) {
        Level level = ctx.level();
        BlockPos pos = ctx.pos();
        if (!(level instanceof ServerLevel serverLevel) || !level.hasChunkAt(pos)) return;
        Entity entity = this.entityType.value().create(serverLevel, EntitySpawnReason.TRIGGERED);
        if (entity == null) return;
        this.tag.ifPresent(value -> entity.load(TagValueInput.create(ProblemReporter.DISCARDING, serverLevel.registryAccess(), value.copy())));
        entity.setPos(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
        serverLevel.addFreshEntity(entity);
        this.entityAction.execute(entity, ctx);
    }

    @Override
    public @NonNull MapCodec<SpawnEntityAction> codec() {
        return CODEC;
    }
}
