package com.iafenvoy.mxt.runtime.formation;

import com.iafenvoy.mxt.data.Formation;
import com.iafenvoy.mxt.data.Formation.RequiredBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter boundary for structure matching; implementations may use vanilla structures.
 */
@FunctionalInterface
public interface FormationStructureValidator {
    boolean matches(ServerLevel level, BlockPos controller, Formation definition);

    FormationStructureValidator ALWAYS = (level, controller, definition) -> true;

    // The definition chooses: a formation may declare that standing on the right blocks is not part of raising it.
    static FormationStructureValidator of(Formation definition) {
        return definition.structureCheck() == Formation.StructureCheck.ALWAYS ? ALWAYS : STRUCTURE;
    }

    // Matches whichever shape the definition declares. An inline structure is the cheap path, since it is
    // already parsed and immutable; a structure_template is re-fetched and re-parsed every period.
    FormationStructureValidator STRUCTURE = (level, controller, definition) -> {
        if (!definition.structure().isEmpty()) return matchesInline(level, controller, definition.structure());
        return definition.structureTemplate()
                .map(template -> matchesTemplate(level, controller, template))
                .orElse(false);
    };

    // The controller is the origin, so offsets are read exactly as written.
    private static boolean matchesInline(ServerLevel level, BlockPos controller, List<RequiredBlock> structure) {
        for (RequiredBlock required : structure) {
            if (!level.getBlockState(controller.offset(required.offset())).equals(required.state())) return false;
        }
        return true;
    }

    // Validates the first matching palette; the controller is the template origin, so data packs retain full
    // control over the required layout.
    private static boolean matchesTemplate(ServerLevel level, BlockPos controller, Identifier template) {
        return level.getStructureManager().get(template)
                .map(loaded -> matchesSavedTemplate(level, controller, loaded.save(new CompoundTag())))
                .orElse(false);
    }

    private static boolean matchesSavedTemplate(ServerLevel level, BlockPos controller, CompoundTag template) {
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

    // Air is skipped rather than required, because the saved bounding box would otherwise make a formation fail
    // over a torch that landed nearby.
    private static boolean matchesPalette(ServerLevel level, BlockPos controller, ListTag blocks, ListTag palette) {
        if (palette.isEmpty()) return false;
        for (int index = 0; index < blocks.size(); index++) {
            CompoundTag block = blocks.getCompoundOrEmpty(index);
            ListTag position = block.getListOrEmpty("pos");
            int stateIndex = block.getIntOr("state", -1);
            if (position.size() != 3 || stateIndex < 0 || stateIndex >= palette.size()) return false;
            BlockState expected = NbtUtils.readBlockState(level.registryAccess().lookupOrThrow(Registries.BLOCK), palette.getCompoundOrEmpty(stateIndex));
            if (expected.isAir()) continue;
            BlockPos worldPosition = controller.offset(position.getIntOr(0, 0), position.getIntOr(1, 0), position.getIntOr(2, 0));
            if (!level.getBlockState(worldPosition).equals(expected)) return false;
        }
        return true;
    }
}
