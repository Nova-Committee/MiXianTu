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

    /**
     * Matches whichever shape the definition declares.
     *
     * <p>An inline {@code structure} is the cheap path and the reason it exists: the expectation is
     * already parsed and immutable, so a check is one {@code getBlockState} per required block. A
     * {@code structure_template} has to be fetched from the manager, re-serialised to NBT and re-parsed —
     * including a block-state registry lookup per block — every single time, which the ticker repeats for
     * every active formation on every period.</p>
     */
    FormationStructureValidator STRUCTURE = (level, controller, definition) -> {
        if (!definition.structure().isEmpty()) return matchesInline(level, controller, definition.structure());
        return definition.structureTemplate()
                .map(template -> matchesTemplate(level, controller, template))
                .orElse(false);
    };

    /**
     * Compares the world against an inline structure. The controller is the origin, so offsets are read
     * exactly as written.
     */
    private static boolean matchesInline(ServerLevel level, BlockPos controller, List<RequiredBlock> structure) {
        for (RequiredBlock required : structure) {
            if (!level.getBlockState(controller.offset(required.offset())).equals(required.state())) return false;
        }
        return true;
    }

    /**
     * Validates the first matching palette in a vanilla structure template. The controller is the
     * template origin, so data packs retain full control over the required layout.
     */
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

    /**
     * Compares the world against one palette of a saved template.
     *
     * <p>Air is skipped rather than required. A structure saved with a structure block records its whole
     * bounding box, so every empty cell inside the footprint arrives as an air entry, and demanding
     * those cells stay empty would make a formation fail because a torch or a dropped block landed
     * nearby. What the template is for is the shape, so an air entry says nothing about the world.</p>
     *
     * <p>Note that this is the opposite of what placing the template would do:
     * {@code StructureTemplate.placeInWorld} writes air over the target, because a structure's job is to
     * reproduce the saved volume. A formation only asserts that its flags are still standing, which is a
     * weaker question and does not need the empty cells.</p>
     */
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
