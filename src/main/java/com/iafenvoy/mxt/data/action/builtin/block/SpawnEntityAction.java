package com.iafenvoy.mxt.data.action.builtin.block;

import com.iafenvoy.mxt.data.action.BlockAction;
import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.action.builtin.entity.meta.NoOpAction;
import com.iafenvoy.mxt.util.formula.FormulaContext;
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

import java.util.Optional;

/**
 * Spawns an entity at the acted block position and applies an optional entity action.
 */
public record SpawnEntityAction(Holder<EntityType<?>> entityType, Optional<CompoundTag> tag,
                                EntityAction entityAction) implements BlockAction {
    public static final MapCodec<SpawnEntityAction> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            RegistryFixedCodec.create(Registries.ENTITY_TYPE).fieldOf("entity_type").forGetter(SpawnEntityAction::entityType),
            CompoundTag.CODEC.optionalFieldOf("tag").forGetter(SpawnEntityAction::tag),
            EntityAction.CODEC.optionalFieldOf("entity_action", NoOpAction.INSTANCE).forGetter(SpawnEntityAction::entityAction)
    ).apply(instance, SpawnEntityAction::new));

    @Override
    public void execute(Level level, BlockPos pos, FormulaContext context) {
        if (!(level instanceof ServerLevel serverLevel) || !level.hasChunkAt(pos)) return;
        Entity entity = this.entityType.value().create(serverLevel, EntitySpawnReason.TRIGGERED);
        if (entity == null) return;
        this.tag.ifPresent(value -> entity.load(TagValueInput.create(ProblemReporter.DISCARDING, serverLevel.registryAccess(), value.copy())));
        entity.setPos(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
        serverLevel.addFreshEntity(entity);
        this.entityAction.execute(entity, context);
    }

    @Override
    public MapCodec<SpawnEntityAction> codec() {
        return CODEC;
    }
}
