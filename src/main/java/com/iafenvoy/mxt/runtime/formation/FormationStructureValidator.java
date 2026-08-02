package com.iafenvoy.mxt.runtime.formation;

import com.iafenvoy.mxt.data.Formation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter boundary for structure-template matching; implementations may use vanilla structures.
 */
@FunctionalInterface
public interface FormationStructureValidator {
    boolean matches(ServerLevel level, BlockPos controller, Formation definition);

    FormationStructureValidator ALWAYS = (level, controller, definition) -> true;

    /**
     * Validates the first matching palette in a vanilla structure template. The controller is
     * the template origin, so data packs retain full control over the required layout.
     */
    FormationStructureValidator TEMPLATE = (level, controller, definition) -> level.getStructureManager()
            .get(definition.structureTemplate())
            .map(template -> matchesTemplate(level, controller, template.save(new CompoundTag())))
            .orElse(false);

    private static boolean matchesTemplate(ServerLevel level, BlockPos controller, CompoundTag template) {
        ListTag blocks = template.getListOrEmpty("blocks");
        if (blocks.isEmpty()) return false;
        List<ListTag> palettes = new ArrayList<>();
        template.getList("palettes").ifPresentOrElse(
                values -> {
                    for (int index = 0; index < values.size(); index++) palettes.add(values.getListOrEmpty(index));
                },
                () -> palettes.add(template.getListOrEmpty("palette"))
        );
        return palettes.stream().anyMatch(palette -> matchesPalette(level, controller, blocks, palette));
    }

    private static boolean matchesPalette(ServerLevel level, BlockPos controller, ListTag blocks, ListTag palette) {
        if (palette.isEmpty()) return false;
        for (int index = 0; index < blocks.size(); index++) {
            CompoundTag block = blocks.getCompoundOrEmpty(index);
            ListTag position = block.getListOrEmpty("pos");
            int stateIndex = block.getIntOr("state", -1);
            if (position.size() != 3 || stateIndex < 0 || stateIndex >= palette.size()) return false;
            BlockState expected = NbtUtils.readBlockState(level.registryAccess().lookupOrThrow(Registries.BLOCK), palette.getCompoundOrEmpty(stateIndex));
            BlockPos worldPosition = controller.offset(position.getIntOr(0, 0), position.getIntOr(1, 0), position.getIntOr(2, 0));
            if (!level.getBlockState(worldPosition).equals(expected)) return false;
        }
        return true;
    }
}
