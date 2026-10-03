"""Split the native Blockbench furnace export into a 3x3x3 model and matching shapes.

The bottom layer is cut into the 2x2 quadrants of its four corners, 1.5 cells each, so those four blocks draw
the whole base between them; the upper two layers stay cell-per-part, and the cell the shape used to leave
hollow is a wall now, so it gets a part too.
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/mxt"
SOURCE = ASSETS / "models/block/alchemy_furnace.json"
OUTPUT = ASSETS / "models/block/alchemy_furnace"
JAVA = ROOT / "src/main/java/com/iafenvoy/mxt/runtime/alchemy/AlchemyFurnaceShapes.java"
SCALE = 3
CONTROLLER = 10
# The five bottom cells the structure never reads: the corners carry the base, these get no part at all.
FREE_BOTTOM = (1, 3, 4, 5, 7)
# A bottom part is the quadrant anchored at its own corner and reaching 1.5 cells inward, so the four of them
# tile the base exactly. The blockstate's own quarter turn is still the right rotation for it.
BOTTOM_EXTENT = 24
COMPONENTS = {
    "alchemy_furnace": (CONTROLLER, "alchemy_furnace_core"),
    "alchemy_main_input": (14, "alchemy_main_input"),
    "alchemy_auxiliary_input": (12, "alchemy_auxiliary_input"),
    "alchemy_output": (22, "alchemy_output"),
}
# (normal axis, upper face, U axis, U reversed, V axis, V reversed)
FACES = {
    "north": (2, False, 0, True, 1, True),
    "south": (2, True, 0, False, 1, True),
    "east": (0, True, 2, True, 1, True),
    "west": (0, False, 2, False, 1, True),
    "up": (1, True, 0, False, 2, False),
    "down": (1, False, 0, False, 2, True),
}


def save(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def interval(original, clipped, axis, reverse):
    low, high = original[0][axis], original[1][axis]
    a, b = (clipped[0][axis] - low) / (high - low), (clipped[1][axis] - low) / (high - low)
    return (1 - b, 1 - a) if reverse else (a, b)


def clip(element, offset, extents, anchor):
    """Clip one cube to the box at `offset` and return it in the coordinates of the block at `anchor`.

    The two differ only for a bottom part: its box starts half a cell outside its own corner block, so the
    geometry that reaches back over that block lands on negative coordinates - which the model format allows.
    """
    if element.get("rotation", {}).get("angle", 0):
        raise ValueError("The furnace exporter requires axis-aligned Blockbench cubes")
    original = [[v * SCALE for v in element[key]] for key in ("from", "to")]
    bounds = [[max(original[0][a], offset[a]) for a in range(3)],
              [min(original[1][a], offset[a] + extents[a]) for a in range(3)]]
    if any(bounds[0][a] >= bounds[1][a] for a in range(3)):
        return None
    result = {"from": [round(bounds[0][a] - anchor[a], 6) for a in range(3)],
              "to": [round(bounds[1][a] - anchor[a], 6) for a in range(3)], "faces": {}}
    for face, data in element["faces"].items():
        axis, upper, u_axis, u_reverse, v_axis, v_reverse = FACES[face]
        side = int(upper)
        # Grid cuts are internal surfaces, not new visible cube faces.
        if abs(bounds[side][axis] - original[side][axis]) > 1e-8:
            continue
        if data.get("rotation", 0):
            raise ValueError("Rotated UVs need a matching clipping transform")
        u0, u1 = interval(original, bounds, u_axis, u_reverse)
        v0, v1 = interval(original, bounds, v_axis, v_reverse)
        uv = data["uv"]
        result["faces"][face] = {
            "uv": [round(uv[0] + (uv[2] - uv[0]) * u0, 6),
                   round(uv[1] + (uv[3] - uv[1]) * v0, 6),
                   round(uv[0] + (uv[2] - uv[0]) * u1, 6),
                   round(uv[1] + (uv[3] - uv[1]) * v1, 6)],
            "texture": data["texture"],
        }
    for key in ("shade", "light_emission"):
        if key in element:
            result[key] = element[key]
    return result


def part_box(index):
    """Where one part is clipped from, and where the block drawing it sits, in the source model's 48-unit space.

    A bottom part reaches half a cell back over its own corner, so its clip box and its block differ; the
    four of them tile the base exactly.
    """
    x, z, y = index % 3, index // 3 % 3, index // 9
    anchor = [16 * x, 16 * y, 16 * z]
    if y == 0:
        base = 3 * 16 - BOTTOM_EXTENT
        return [0 if x == 0 else base, 0, 0 if z == 0 else base], [BOTTOM_EXTENT, 16, BOTTOM_EXTENT], anchor
    return anchor, [16, 16, 16], anchor


def main():
    source = json.loads(SOURCE.read_text(encoding="utf-8"))
    count = 0
    parts = [index for index in range(27) if index not in FREE_BOTTOM]
    for index in parts:
        offset, extents, anchor = part_box(index)
        elements = [part for e in source["elements"] if (part := clip(e, offset, extents, anchor)) is not None]
        if not elements:
            raise ValueError(f"Empty occupied furnace cell {index}")
        save(OUTPUT / f"part_{index}.json", {"credit": "Blockbench model; split by tools/export_alchemy_furnace.py", "textures": source["textures"], "elements": elements})
        save(OUTPUT / f"part_{index}_lit.json", {"parent": f"mxt:block/alchemy_furnace/part_{index}", "textures": {"3": "mxt:block/alchemy_vent_lit"}})
        count += len(elements)
    # The set of parts a layout change can shrink: leftovers would keep loading, and a stale part would still be
    # referenced by nothing but its own file, so they are dropped rather than left to rot.
    expected = {f"part_{index}{suffix}.json" for index in parts for suffix in ("", "_lit")}
    for stale in OUTPUT.glob("part_*.json"):
        if stale.name not in expected:
            stale.unlink()
    save(ASSETS / "models/block/alchemy_furnace_casing.json", {"parent": "minecraft:block/cube_all", "textures": {"all": "mxt:block/alchemy_bronze"}})
    save(ASSETS / "items/alchemy_furnace_casing.json", {"model": {"type": "minecraft:model", "model": "mxt:block/alchemy_furnace_casing"}})
    for block, (_, model) in COMPONENTS.items():
        textures = {"side": "mxt:block/alchemy_bronze", "top": "mxt:block/alchemy_trim",
                    "front": "mxt:block/alchemy_vent"}
        if block == "alchemy_main_input":
            textures["top"] = "mxt:block/alchemy_bronze"
        elif block == "alchemy_output":
            textures["top"], textures["front"] = textures["front"], textures["top"]
        save(ASSETS / f"models/block/{model}.json", {"parent": "minecraft:block/orientable", "textures": textures})
        save(ASSETS / f"items/{block}.json", {"model": {"type": "minecraft:model", "model": f"mxt:block/{model}"}})
        variants = {}
        for facing, angle in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
            for lit in (False, True):
                suffix = "_lit" if lit else ""
                for formed in (False, True):
                    target = f"alchemy_furnace/part_{COMPONENTS[block][0]}{suffix}" if formed else model
                    variants[f"facing={facing},formed={str(formed).lower()},lit={str(lit).lower()}"] = {"model": f"mxt:block/{target}", "y": angle}
        save(ASSETS / f"blockstates/{block}.json", {"variants": variants})
    casing = {}
    # Cells whose part value can never occur: the five free bottom cells are never claimed, and the four
    # component cells hold their own blocks, so those variants fall back to the plain cube.
    no_part_cells = {*FREE_BOTTOM, *(index for index, _ in COMPONENTS.values())}
    for facing, angle in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        for lit in (False, True):
            suffix = "_lit" if lit else ""
            for part in range(28):
                model = "alchemy_furnace_casing" if part == 0 or part - 1 in no_part_cells else f"alchemy_furnace/part_{part-1}{suffix}"
                casing[f"facing={facing},part={part},lit={str(lit).lower()}"] = {"model": f"mxt:block/{model}", "y": angle}
    save(ASSETS / "blockstates/alchemy_furnace_casing.json", {"variants": casing})
    boxes = ",\n".join("            {" + ", ".join(str(v) for v in e["from"] + e["to"]) + "}" for e in source["elements"])
    java = '''package com.iafenvoy.mxt.runtime.alchemy;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

// Generated from the Blockbench export by tools/export_alchemy_furnace.py; edit the model, not these bounds.
public final class AlchemyFurnaceShapes {
    private static final double[][] BOXES = {
%s
    };
    private static final VoxelShape[][] PARTS = new VoxelShape[27][4];

    static {
        for (int rotation = 0; rotation < 4; rotation++) {
            for (int index = 0; index < 27; index++) PARTS[index][rotation] = build(index, rotation);
        }
    }

    private AlchemyFurnaceShapes() {
    }

    public static VoxelShape shape(int index, Direction facing) {
        return PARTS[index][rotation(facing)];
    }

    private static int rotation(Direction facing) {
        return switch (facing) {
            case EAST -> 1;
            case SOUTH -> 2;
            case WEST -> 3;
            default -> 0;
        };
    }

    private static VoxelShape build(int index, int rotation) {
        int ox = index %% 3 * 16;
        int oy = index / 9 * 16;
        int oz = index / 3 %% 3 * 16;
        VoxelShape result = Shapes.empty();
        for (double[] box : BOXES) {
            double x0 = Math.max(0, box[0] * 3 - ox), y0 = Math.max(0, box[1] * 3 - oy), z0 = Math.max(0, box[2] * 3 - oz);
            double x1 = Math.min(16, box[3] * 3 - ox), y1 = Math.min(16, box[4] * 3 - oy), z1 = Math.min(16, box[5] * 3 - oz);
            if (x0 >= x1 || y0 >= y1 || z0 >= z1) continue;
            for (int turn = 0; turn < rotation; turn++) {
                double oldX0 = x0, oldX1 = x1;
                x0 = 16 - z1;
                x1 = 16 - z0;
                z0 = oldX0;
                z1 = oldX1;
            }
            result = Shapes.or(result, Shapes.box(x0 / 16, y0 / 16, z0 / 16, x1 / 16, y1 / 16, z1 / 16));
        }
        return result.optimize();
    }
}
''' % boxes
    JAVA.parent.mkdir(parents=True, exist_ok=True)
    JAVA.write_text(java, encoding="utf-8")
    print(f"Blockbench furnace: {len(parts)} occupied cells ({len(FREE_BOTTOM)} free bottom cells), {count} clipped elements, 4 functional components x 16 variants, 224 casing variants; Java shapes share {len(source['elements'])} source bounds")


if __name__ == "__main__":
    main()
