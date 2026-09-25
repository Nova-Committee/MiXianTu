package com.iafenvoy.mxt.runtime.world;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.secretrealm.SecretRealm;
import com.iafenvoy.mxt.data.secretrealm.SecretRealm.StructurePlacement;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.*;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * Furnishes a fresh instance from structure templates. They are the vanilla datapack ones
 * ({@code data/<namespace>/structure/<path>.nbt}), and placement happens before anybody arrives, which is why a
 * landed position can already stand on a placed floor.
 */
public final class SecretRealmStructurePlacer {
    private SecretRealmStructurePlacer() {
    }

    // Resolved before the instance is created, so a typo rejects the entry instead of leaving a half-furnished
    // secret realm behind.
    public static boolean resolvable(StructureTemplateManager manager, SecretRealm definition) {
        return definition.structures().stream().allMatch(placement -> manager.get(placement.nbt()).isPresent());
    }

    public static void place(ServerLevel level, SecretRealmRecord record, Vec3 anchor) {
        StructureTemplateManager manager = level.getStructureManager();
        RandomSource random = RandomSource.create(record.seed() ^ 0x9E3779B97F4A7C15L);
        for (StructurePlacement placement : record.instance().structures()) {
            if (placement.chance() < 1.0D && random.nextDouble() >= placement.chance()) continue;
            Optional<StructureTemplate> template = manager.get(placement.nbt());
            if (template.isEmpty()) {
                MiXianTu.LOGGER.error("Missing secret realm structure template {}", placement.nbt());
                continue;
            }
            BlockPos pos = placement.relativeToEntry() ? offset(anchor, placement.pos()) : placement.pos();
            StructurePlaceSettings settings = new StructurePlaceSettings()
                    .setRotation(rotation(placement.rotation()))
                    .setMirror(mirror(placement.mirror()))
                    .setIgnoreEntities(placement.ignoreEntities())
                    .setLiquidSettings(placement.keepLiquids() ? LiquidSettings.APPLY_WATERLOGGING : LiquidSettings.IGNORE_WATERLOGGING);
            if (placement.integrity() < 1.0D)
                settings.addProcessor(new BlockRotProcessor((float) placement.integrity()));
            level.getChunkAt(pos);
            template.get().placeInWorld(level, pos, pos, settings, random, Block.UPDATE_CLIENTS);
        }
    }

    private static BlockPos offset(Vec3 anchor, BlockPos relative) {
        return new BlockPos(Mth.floor(anchor.x) + relative.getX(), Mth.floor(anchor.y) + relative.getY(),
                Mth.floor(anchor.z) + relative.getZ());
    }

    private static Rotation rotation(SecretRealm.Rotation value) {
        return switch (value) {
            case NONE -> Rotation.NONE;
            case CLOCKWISE_90 -> Rotation.CLOCKWISE_90;
            case CLOCKWISE_180 -> Rotation.CLOCKWISE_180;
            case COUNTERCLOCKWISE_90 -> Rotation.COUNTERCLOCKWISE_90;
        };
    }

    private static Mirror mirror(SecretRealm.Mirror value) {
        return switch (value) {
            case NONE -> Mirror.NONE;
            case LEFT_RIGHT -> Mirror.LEFT_RIGHT;
            case FRONT_BACK -> Mirror.FRONT_BACK;
        };
    }
}
