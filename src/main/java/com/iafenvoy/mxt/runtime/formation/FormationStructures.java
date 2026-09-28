package com.iafenvoy.mxt.runtime.formation;

import com.iafenvoy.mxt.data.Formation;
import com.iafenvoy.mxt.data.Formation.RequiredBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads a formation's declared shape as offsets from the controller, for previewing it. Matching does not go through
 * here: it accepts any palette of a template while a preview can only show one, so the first palette is shown. Air
 * is dropped for the same reason matching skips it - a saved bounding box is mostly air.
 */
public final class FormationStructures {
    private FormationStructures() {
    }

    public static List<RequiredBlock> declared(ServerLevel level, Formation definition) {
        if (!definition.structure().isEmpty()) return definition.structure();
        return definition.structureTemplate()
                .flatMap(level.getStructureManager()::get)
                .map(template -> fromTemplate(level, template))
                .orElse(List.of());
    }

    private static List<RequiredBlock> fromTemplate(ServerLevel level, StructureTemplate template) {
        CompoundTag saved = template.save(new CompoundTag());
        ListTag blocks = saved.getListOrEmpty("blocks");
        ListTag palette = firstPalette(saved);
        List<RequiredBlock> required = new ArrayList<>();
        for (int index = 0; index < blocks.size(); index++) {
            CompoundTag block = blocks.getCompoundOrEmpty(index);
            ListTag position = block.getListOrEmpty("pos");
            int stateIndex = block.getIntOr("state", -1);
            if (position.size() != 3 || stateIndex < 0 || stateIndex >= palette.size()) continue;
            BlockState state = NbtUtils.readBlockState(level.registryAccess().lookupOrThrow(Registries.BLOCK),
                    palette.getCompoundOrEmpty(stateIndex));
            if (state.isAir()) continue;
            required.add(new RequiredBlock(
                    new BlockPos(position.getIntOr(0, 0), position.getIntOr(1, 0), position.getIntOr(2, 0)), state));
        }
        return List.copyOf(required);
    }

    private static ListTag firstPalette(CompoundTag saved) {
        return saved.getList("palettes").map(values -> values.getListOrEmpty(0))
                .orElseGet(() -> saved.getListOrEmpty("palette"));
    }
}
