"""Which range shape makes the formation ground scan walk fewer blocks.

The formation ground scan (`BlockAuraService.groundInSphere`) classifies every
16^3 section of the covered chunks three ways:

* the shape does not reach the section (its nearest block centre is outside) ->
  skipped, not one block is read;
* the shape covers the section (its farthest block centre is inside) -> the
  section's palette aggregate answers it, a handful of entries;
* anything else -> all 4096 block centres are tested one by one.

Only the third case touches every block, and it is the only part of the scan
whose cost follows the boundary instead of the volume. This script counts those
sections for the sphere the code uses and for the cube an alternative range
could use, over a sweep of radii and over every position the formation centre
can take inside its own section.

Both shapes are sampled the way the code samples them: the test is against block
centres, and the formation centre is always the centre of one block, so in
block-index offsets the comparison is integer arithmetic. Only the centre's
offset inside its own section (0..15 per axis) can change the grid, and m is the
mirror image of 15 - m, so the sweep runs the 8^3 offsets and the statistics
over all 4096 offsets are exactly two copies of each value -- `validate` checks
that claim against the full grid and against the raw block loop.
"""

from __future__ import annotations

import itertools
from pathlib import Path

import matplotlib

matplotlib.use("Agg")

import matplotlib.pyplot as plt
import numpy as np
from matplotlib.colors import ListedColormap
from matplotlib.patches import Patch

HERE = Path(__file__).resolve().parent
SIDE = 16
SECTION_BLOCKS = SIDE**3
KEYS = ("sphere_cut", "cube_cut", "sphere_covers", "cube_covers")
BLOCKS = "0.7"
CUT_COLOUR = "#d95f02"
COVER_COLOUR = "#1b6ca8"
OUT_COLOUR = "#f2f2f2"

# Integer radii up to 64 (where the shapes' own alignment shows), then every
# second block up to 128 so the sweep stays a readable runtime.
RADII = np.array(list(range(1, 65)) + list(range(66, 129, 2)))
REPORT_RADII = (4, 8, 12, 16, 24, 32, 48, 64, 96, 128)
SENSITIVITY_RADII = (12, 16, 32, 64, 128)
# Below this radius the counts are single digits, so a max/min ratio says nothing.
SWING_FROM_RADIUS = 32
# Range of a cube holding the sphere's volume: 8h^3 = 4/3 pi r^3, so h = r * (pi/6)^(1/3).
EQUAL_VOLUME = (np.pi / 6.0) ** (1.0 / 3.0)


def axes_offsets(limit: int) -> np.ndarray:
    return np.array(list(itertools.product(range(limit), repeat=3)), dtype=np.int32)


def axis_tables(extent: int) -> tuple[np.ndarray, np.ndarray]:
    """Nearest and farthest block-centre distance per (centre offset, section).

    A section with index n covers the block offsets 16n - m .. 16n - m + 15, so
    the nearest sample of the section and its farthest sample are these two
    numbers. The sphere combines them by the squared distance, the cube by the
    per-axis extremum; in both cases over the three axes.
    """
    m = np.arange(SIDE)[:, None]
    n = np.arange(-extent, extent + 1)[None, :]
    low = SIDE * n - m
    high = low + SIDE - 1
    near = np.maximum(np.maximum(low, -high), 0)
    far = np.maximum(np.abs(low), np.abs(high))
    return near, far


def classify(radius: float, offset, near: np.ndarray, far: np.ndarray, cube: bool) -> tuple[int, int]:
    """(boundary sections, covered sections) for one radius and centre offset."""
    mx, my, mz = int(offset[0]), int(offset[1]), int(offset[2])
    nx, ny, nz = near[mx][:, None, None], near[my][None, :, None], near[mz][None, None, :]
    fx, fy, fz = far[mx][:, None, None], far[my][None, :, None], far[mz][None, None, :]
    if cube:
        reached = np.maximum(np.maximum(nx, ny), nz) <= radius
        covered = np.maximum(np.maximum(fx, fy), fz) <= radius
    else:
        limit = radius * radius
        reached = nx * nx + ny * ny + nz * nz <= limit
        covered = fx * fx + fy * fy + fz * fz <= limit
    return int(np.count_nonzero(reached & ~covered)), int(np.count_nonzero(covered))


def brute_classify(radius: float, offset, cube: bool) -> tuple[int, int]:
    """The same two numbers from the raw 4096-block loop, used by `validate`."""
    extent = int(np.ceil(radius)) // SIDE + 2
    boundary = covered = 0
    for section in itertools.product(range(-extent, extent + 1), repeat=3):
        spans = []
        for index, shift in zip(section, offset):
            low = SIDE * index - int(shift)
            spans.append(np.arange(low, low + SIDE))
        a, b, c = np.meshgrid(*spans, indexing="ij")
        if cube:
            inside = np.maximum(np.maximum(np.abs(a), np.abs(b)), np.abs(c)) <= radius
        else:
            inside = a * a + b * b + c * c <= radius * radius
        if inside.all():
            covered += 1
        elif inside.any():
            boundary += 1
    return boundary, covered


def lattice_volumes(maximum: int) -> np.ndarray:
    """Blocks inside the sphere per integer radius: exact, by histogramming a^2+b^2+c^2."""
    axis = np.arange(-maximum, maximum + 1)
    a, b, c = np.meshgrid(axis, axis, axis, indexing="ij")
    histogram = np.bincount((a * a + b * b + c * c).ravel())
    radii = np.arange(maximum + 1)
    return np.cumsum(histogram)[radii * radii]


def sweep(radii: np.ndarray, offsets: np.ndarray) -> dict[str, np.ndarray]:
    results = {key: np.zeros((len(radii), len(offsets)), dtype=np.int32) for key in KEYS}
    for index, radius in enumerate(radii):
        near, far = axis_tables(int(np.ceil(radius)) // SIDE + 2)
        for slot, offset in enumerate(offsets):
            for cube, prefix in ((False, "sphere"), (True, "cube")):
                boundary, covered = classify(radius, offset, near, far, cube)
                results[f"{prefix}_cut"][index, slot] = boundary
                results[f"{prefix}_covers"][index, slot] = covered
    return results


def sweep_equal_volume(radii: np.ndarray, offsets: np.ndarray) -> np.ndarray:
    """Cube boundary sections for a cube holding as much as the sphere of the same baseline.

    A cube whose range is h covers 8h^3 blocks against the sphere's 4/3 pi r^3, so
    an equal-volume cube has half-extent h = r * (pi/6)^(1/3) = 0.806 r, which makes
    its edge 1.612 r. Passing the edge as the range instead would cover eight times
    the volume, so the two conventions differ by a factor of two in every axis.
    """
    boundary = np.zeros((len(radii), len(offsets)), dtype=np.int32)
    for index, radius in enumerate(radii):
        half = radius * EQUAL_VOLUME
        near, far = axis_tables(int(np.ceil(half)) // SIDE + 2)
        for slot, offset in enumerate(offsets):
            boundary[index, slot] = classify(half, offset, near, far, cube=True)[0]
    return boundary


def validate() -> None:
    """Cross-check the closed form against the full offset grid and the block loop."""
    print("validation: 8^3 offsets vs 4096 offsets vs raw block loop")
    full = axes_offsets(SIDE)
    mirrored = axes_offsets(SIDE // 2)
    for radius in (1, 7.5, 12, 16, 33):
        near, far = axis_tables(int(np.ceil(radius)) // SIDE + 2)
        for cube in (False, True):
            shape = "cube" if cube else "sphere"
            reduced = np.array([classify(radius, offset, near, far, cube) for offset in mirrored])
            whole = np.array([classify(radius, offset, near, far, cube) for offset in full])
            same_mean = np.allclose(reduced.mean(axis=0), whole.mean(axis=0))
            same_range = reduced.min(axis=0).tolist() == whole.min(axis=0).tolist() and \
                reduced.max(axis=0).tolist() == whole.max(axis=0).tolist()
            for offset in ((0, 0, 0), (3, 5, 7), (2, 9, 14)):
                closed = classify(radius, offset, near, far, cube)
                raw = brute_classify(radius, offset, cube)
                if closed != raw:
                    raise AssertionError(f"{shape} r={radius} offset={offset}: {closed} != raw {raw}")
            print(f"  r={radius:>5} {shape:<6} symmetric mean {same_mean} range {same_range} block loop OK")


def report(radii, results, sphere_blocks, equal_volume, cube_blocks) -> None:
    print()
    print("same radius (the nominal parameter, but the cube then covers 8/4.19 = 1.9x the blocks)")
    print("radius | sphere cut (min/mean/max) | cube cut (min/mean/max) | cube/sphere | "
          "sphere blocks | cube blocks | sphere block tests/period")
    for radius in REPORT_RADII:
        index = int(np.flatnonzero(radii == radius)[0])
        sphere = results["sphere_cut"][index]
        cube = results["cube_cut"][index]
        sphere_mean, cube_mean = sphere.mean(), cube.mean()
        ratio = cube_mean / sphere_mean if sphere_mean else float("nan")
        print(f"{radius:>6} | {sphere.min():>4}/{sphere_mean:>8.2f}/{sphere.max():>4} | "
              f"{cube.min():>4}/{cube_mean:>8.2f}/{cube.max():>4} | {ratio:>10.2f} | "
              f"{sphere_blocks[radius]:>12} | {cube_blocks[radius]:>11} | {sphere_mean * SECTION_BLOCKS:>12.0f}")

    print()
    print(f"equal volume: cube range = radius * (pi/6)^(1/3) = radius * {EQUAL_VOLUME:.3f} "
          f"(edge = radius * {2 * EQUAL_VOLUME:.3f})")
    print("radius | cube range | sphere cut | equal-volume cube cut | cube/sphere | "
          "sphere blocks | cube blocks (discrete)")
    for radius in REPORT_RADII:
        index = int(np.flatnonzero(radii == radius)[0])
        half = radius * EQUAL_VOLUME
        sphere = results["sphere_cut"][index].mean()
        cube = equal_volume[index].mean()
        cube_count = (2 * int(np.floor(half)) + 1) ** 3
        print(f"{radius:>6} | {half:>10.2f} | {sphere:>10.2f} | {cube:>21.2f} | {cube / sphere:>10.3f} | "
              f"{sphere_blocks[radius]:>12} | {cube_count:>21}")


def style() -> None:
    plt.rcParams.update({
        "font.sans-serif": ["Microsoft YaHei", "SimHei", "DejaVu Sans"],
        "axes.unicode_minus": False,
        "axes.edgecolor": BLOCKS,
        "axes.labelcolor": "0.15",
        "axes.titlesize": 11,
        "figure.facecolor": "white",
        "grid.alpha": 0.25,
    })


def plot_cut_vs_radius(radii, results) -> None:
    sphere = results["sphere_cut"].mean(axis=1)
    cube = results["cube_cut"].mean(axis=1)
    figure, (left, right) = plt.subplots(1, 2, figsize=(13, 5.2), dpi=160)

    for key, values, colour, name in (("sphere_cut", sphere, COVER_COLOUR, "球体（现状）"),
                                      ("cube_cut", cube, CUT_COLOUR, "立方体（对照）")):
        left.plot(radii, values, color=colour, label=name)
        left.fill_between(radii, results[key].min(axis=1), results[key].max(axis=1),
                          color=colour, alpha=0.15, linewidth=0)
    left.set_xlabel("阵法半径 radius（格）")
    left.set_ylabel("需要逐格遍历的子区块数（每周期）")
    left.set_title("边界段数量：均值曲线，阴影为 4096 种中心偏移的极值")
    left.grid(True)
    left.legend()

    ratio = np.divide(cube, sphere, out=np.full_like(sphere, np.nan), where=sphere > 0)
    right.plot(radii, ratio, color="#444444")
    right.axhline(1.0, color=BLOCKS, linewidth=0.8)
    right.set_xlabel("阵法半径 radius（格）")
    right.set_ylabel("立方体 ÷ 球体")
    right.set_title("同半径下的边界段倍数（全部 > 1 表示球体更省）")
    right.grid(True)

    figure.tight_layout()
    figure.savefig(HERE / "01_cut_sections_vs_radius.png")
    plt.close(figure)


def plot_offset_sensitivity(radii, results) -> None:
    figure, (left, right) = plt.subplots(1, 2, figsize=(13.5, 5.2), dpi=160)

    sphere_data, cube_data = [], []
    for radius in SENSITIVITY_RADII:
        index = int(np.flatnonzero(radii == radius)[0])
        sphere_data.append(results["sphere_cut"][index] / results["sphere_cut"][index].mean())
        cube_data.append(results["cube_cut"][index] / results["cube_cut"][index].mean())
    both = sphere_data + cube_data
    positions = [slot * 3 + 0.9 for slot in range(len(sphere_data))] + \
        [slot * 3 + 1.9 for slot in range(len(cube_data))]
    boxes = left.boxplot(both, positions=positions, widths=0.6, patch_artist=True, showfliers=False)
    for index, box in enumerate(boxes["boxes"]):
        box.set_facecolor(COVER_COLOUR if index < len(sphere_data) else CUT_COLOUR)
        box.set_alpha(0.55)
    for median in boxes["medians"]:
        median.set_color("0.15")
    left.set_xticks([slot * 3 + 1.4 for slot in range(len(SENSITIVITY_RADII))])
    left.set_xticklabels([str(radius) for radius in SENSITIVITY_RADII])
    left.set_xlabel("阵法半径 radius（格）")
    left.set_ylabel("该半径均值 = 1 时的相对偏移")
    left.set_title("中心在子区块内的位置带来的波动（左蓝 = 球体，右橙 = 立方体）")
    left.grid(True, axis="y")

    for key, colour, name in (("sphere_cut", COVER_COLOUR, "球体"), ("cube_cut", CUT_COLOUR, "立方体")):
        keep = radii >= SWING_FROM_RADIUS
        swing = results[key].max(axis=1) / np.maximum(results[key].min(axis=1), 1)
        right.plot(radii[keep], swing[keep], color=colour, label=name)
    right.set_xlabel(f"阵法半径 radius（格，只画 ≥ {SWING_FROM_RADIUS} 的部分）")
    right.set_ylabel("同一半径下最大 ÷ 最小")
    right.set_title("最坏位置与最好位置的差距：立方体随对齐振荡，球体几乎不受影响")
    right.set_ylim(1.0, 2.6)
    right.grid(True)
    right.legend()

    figure.tight_layout()
    figure.savefig(HERE / "02_offset_sensitivity.png")
    plt.close(figure)


def plot_cost_split(radii, results) -> None:
    figure, axis = plt.subplots(figsize=(9.5, 5.5), dpi=160)
    for key, colour, name, style_ in (("sphere_cut", COVER_COLOUR, "球体：边界段（逐格）", "-"),
                                      ("sphere_covers", COVER_COLOUR, "球体：完整段（调色板计数）", "--"),
                                      ("cube_cut", CUT_COLOUR, "立方体：边界段（逐格）", "-"),
                                      ("cube_covers", CUT_COLOUR, "立方体：完整段（调色板计数）", "--")):
        values = results[key].mean(axis=1)
        axis.plot(radii, np.where(values > 0, values, np.nan), color=colour, linestyle=style_, label=name)
    axis.set_yscale("log")
    axis.set_ylim(0.3, None)
    axis.set_xlabel("阵法半径 radius（格）")
    axis.set_ylabel("子区块数（每周期，均值）")
    axis.set_title("两类段的规模：逐格那段是唯一按块计的成本")
    axis.grid(True, which="both")
    axis.legend()
    figure.tight_layout()
    figure.savefig(HERE / "03_section_cost_split.png")
    plt.close(figure)


def plot_cut_per_volume(radii, results, sphere_blocks, cube_blocks) -> None:
    figure, axis = plt.subplots(figsize=(9.5, 5.5), dpi=160)
    sphere_volume = np.array([sphere_blocks[int(np.floor(radius))] for radius in radii])
    cube_volume = np.array([(2 * int(np.floor(radius)) + 1) ** 3 for radius in radii])
    axis.loglog(sphere_volume, results["sphere_cut"].mean(axis=1), color=COVER_COLOUR, label="球体（现状）")
    axis.loglog(cube_volume, results["cube_cut"].mean(axis=1), color=CUT_COLOUR, label="立方体（对照）")
    for radius in (8, 16, 32, 64, 128):
        index = int(np.flatnonzero(radii == radius)[0])
        axis.annotate(f"r={radius}", (sphere_volume[index], results["sphere_cut"][index].mean()),
                      textcoords="offset points", xytext=(4, -10), fontsize=8, color=COVER_COLOUR)
    axis.set_xlabel("覆盖到的方块数（体积）")
    axis.set_ylabel("需要逐格遍历的子区块数（每周期）")
    axis.set_title("按覆盖量看边界成本：各条曲线按半径参数化")
    axis.grid(True, which="both")
    axis.legend()
    figure.tight_layout()
    figure.savefig(HERE / "04_cut_per_volume.png")
    plt.close(figure)


def plot_section_maps(radius: float, offset) -> None:
    near, far = axis_tables(int(np.ceil(radius)) // SIDE + 2)
    extent = near.shape[1] // 2
    mx, my, mz = (int(value) for value in offset)
    nx, ny, nz = near[mx], near[my], near[mz]
    fx, fy, fz = far[mx], far[my], far[mz]
    colours = ListedColormap([OUT_COLOUR, CUT_COLOUR, COVER_COLOUR])
    figure, panels = plt.subplots(2, 2, figsize=(12.5, 10), dpi=160)
    for row, cube in enumerate((False, True)):
        if cube:
            reached = np.maximum(np.maximum(nx[:, None, None], ny[None, :, None]), nz[None, None, :]) <= radius
            covered = np.maximum(np.maximum(fx[:, None, None], fy[None, :, None]), fz[None, None, :]) <= radius
        else:
            limit = radius * radius
            reached = nx[:, None, None] ** 2 + ny[None, :, None] ** 2 + nz[None, None, :] ** 2 <= limit
            covered = fx[:, None, None] ** 2 + fy[None, :, None] ** 2 + fz[None, None, :] ** 2 <= limit
        category = np.where(covered, 2, np.where(reached, 1, 0))
        for column, (slice_index, xlabel, ylabel, title) in enumerate((
                ((slice(None), extent, slice(None)), "子区块 x", "子区块 z", "过阵心的水平层（y 居中）"),
                ((slice(None), slice(None), extent), "子区块 x", "子区块 y", "过阵心的竖直层（z 居中）"))):
            panel = panels[row][column]
            panel.imshow(category[slice_index], origin="lower", cmap=colours, vmin=0, vmax=2)
            panel.set_xlabel(xlabel)
            panel.set_ylabel(ylabel)
            panel.set_title(f"{'立方体' if cube else '球体'}：{title}")
            panel.set_xticks([])
            panel.set_yticks([])
    handles = [Patch(facecolor=OUT_COLOUR, edgecolor=BLOCKS, label="跳过（一根方块都不读）"),
               Patch(facecolor=CUT_COLOUR, label="边界段：逐格遍历 4096 格"),
               Patch(facecolor=COVER_COLOUR, label="完整段：调色板计数")]
    figure.legend(handles=handles, loc="lower center", ncol=3, frameon=False)
    figure.suptitle(f"子区块分类：浅 = 跳过，橙 = 逐格遍历，蓝 = 调色板计数"
                    f"（radius {radius:g}，中心偏移 {tuple(offset)}）", fontsize=11)
    figure.tight_layout(rect=(0, 0.04, 1, 1))
    figure.savefig(HERE / "05_section_map.png")
    plt.close(figure)


def plot_alignment_sawtooth(radii, results, offsets) -> None:
    """A cube's faces can land on section boundaries, and then a whole layer of
    boundary sections disappears; the sphere's surface never aligns, so its cost
    has no such notch."""
    def slot_of(offset):
        return int(np.flatnonzero((offsets == np.array(offset)).all(axis=1))[0])

    figure, axis = plt.subplots(figsize=(10, 5.5), dpi=160)
    for offset in ((0, 0, 0), (7, 7, 7)):
        axis.plot(radii, results["cube_cut"][:, slot_of(offset)], color=CUT_COLOUR,
                  linestyle="-" if offset[0] == 0 else "--",
                  label=f"立方体，中心偏移 {offset}")
    axis.plot(radii, results["sphere_cut"][:, slot_of((5, 5, 5))], color=COVER_COLOUR,
              label="球体，中心偏移 (5, 5, 5)")
    axis.set_xlabel("阵法半径 radius（格）")
    axis.set_ylabel("需要逐格遍历的子区块数（每周期）")
    axis.set_title("立方体的边界段随半径每 16 格掉一截（面与子区块边界对齐），球体没有这种缺口")
    axis.grid(True)
    axis.legend()
    figure.tight_layout()
    figure.savefig(HERE / "06_alignment_sawtooth.png")
    plt.close(figure)


def surface_grid(radius: float, cube: bool) -> np.ndarray:
    """Boundary sections for the 16x16 positions of the centre inside its section.

    The vertical offset is averaged out: the grid is what is left of the shape's
    cost once the centre's height inside its section stops being a knob.
    """
    near, far = axis_tables(int(np.ceil(radius)) // SIDE + 2)
    grid = np.zeros((SIDE, SIDE))
    for mx in range(SIDE):
        for mz in range(SIDE):
            grid[mx, mz] = np.mean([classify(radius, (mx, my, mz), near, far, cube)[0]
                                    for my in range(SIDE)])
    return grid


def plot_equal_volume(radii, results, equal_volume) -> None:
    """Same covered volume instead of the same nominal radius: the cube is then a
    smaller box (range 0.806 r, edge 1.612 r) and drops below the sphere."""
    sphere = results["sphere_cut"].mean(axis=1)
    cube = results["cube_cut"].mean(axis=1)
    equal = equal_volume.mean(axis=1)
    figure, (left, right) = plt.subplots(1, 2, figsize=(13, 5.2), dpi=160)

    left.plot(radii, sphere, color=COVER_COLOUR, label="球体（现状，基准半径 r）")
    left.plot(radii, equal, color=CUT_COLOUR, label="等体积立方体（range 0.806r，边长 1.612r）")
    left.plot(radii, cube, color=CUT_COLOUR, linestyle=":", alpha=0.7, label="同半径立方体（range r，体积 1.9 倍）")
    left.set_xlabel("球体半径 radius（格）")
    left.set_ylabel("需要逐格遍历的子区块数（每周期）")
    left.set_title("统一体积后：同一条曲线上两侧覆盖的方块数相等")
    left.grid(True)
    left.legend()

    right.plot(radii, np.divide(equal, sphere, out=np.full_like(sphere, np.nan), where=sphere > 0),
               color=CUT_COLOUR, label="等体积立方体 ÷ 球体")
    right.plot(radii, np.divide(cube, sphere, out=np.full_like(sphere, np.nan), where=sphere > 0),
               color=CUT_COLOUR, linestyle=":", alpha=0.7, label="同半径立方体 ÷ 球体")
    right.axhline(1.0, color=BLOCKS, linewidth=0.8)
    right.set_xlabel("球体半径 radius（格）")
    right.set_ylabel("立方体 ÷ 球体")
    right.set_title("< 1 表示立方体更省（等体积口径下它一直 < 1）")
    right.set_ylim(0.7, 1.4)
    right.grid(True)
    right.legend()

    figure.tight_layout()
    figure.savefig(HERE / "08_equal_volume.png")
    plt.close(figure)


def plot_surfaces_3d(radii, equal_volume: bool = False) -> None:
    axis_x, axis_y = np.meshgrid(np.arange(SIDE), np.arange(SIDE), indexing="ij")
    figure = plt.figure(figsize=(17, 5.8), dpi=160, layout="constrained")
    for panel, radius in enumerate(radii, start=1):
        sphere = surface_grid(radius, cube=False)
        cube = surface_grid(radius * EQUAL_VOLUME if equal_volume else radius, cube=True)
        floor = min(sphere.min(), cube.min()) * 0.92
        axes = figure.add_subplot(1, len(radii), panel, projection="3d")
        if equal_volume:
            # Two filled surfaces this close together read as one muddy slab, so the
            # equal-volume view keeps the same two shapes as wireframes.
            axes.plot_wireframe(axis_x, axis_y, sphere, color=COVER_COLOUR, linewidth=0.7, alpha=0.85)
            axes.plot_wireframe(axis_x, axis_y, cube, color=CUT_COLOUR, linewidth=0.7, alpha=0.85)
        else:
            axes.plot_surface(axis_x, axis_y, sphere, color=COVER_COLOUR, alpha=0.45,
                              linewidth=0.3, edgecolor=(0, 0, 0, 0.10), antialiased=True)
            axes.plot_surface(axis_x, axis_y, cube, color=CUT_COLOUR, alpha=0.45,
                              linewidth=0.3, edgecolor=(0, 0, 0, 0.10), antialiased=True)
        for values, colour in ((sphere, COVER_COLOUR), (cube, CUT_COLOUR)):
            axes.contour(axis_x, axis_y, values, levels=6, zdir="z", offset=floor,
                         colors=colour, alpha=0.5, linewidths=0.8)
        axes.set_zlim(floor, max(sphere.max(), cube.max()) * 1.04)
        axes.set_xlabel("阵心 x 偏移", fontsize=8, labelpad=-2)
        axes.set_ylabel("阵心 z 偏移", fontsize=8, labelpad=-2)
        axes.set_zlabel("段数", fontsize=8, labelpad=-4)
        axes.set_title(f"radius {radius}", fontsize=10, pad=0)
        axes.view_init(elev=32, azim=-60)
        axes.tick_params(labelsize=7, pad=0)
    handles = [Patch(facecolor=COVER_COLOUR, alpha=0.55, label="球体（现状）"),
               Patch(facecolor=CUT_COLOUR, alpha=0.55,
                     label=f"等体积立方体（range {EQUAL_VOLUME:.3f}r）" if equal_volume else "立方体（对照，range r）")]
    figure.legend(handles=handles, loc="outside lower center", ncol=2, frameon=False)
    figure.suptitle("固定半径下的需要逐格遍历的子区块数：XY = 阵心在自己段内的 16x16 位置，"
                    "Z = 边界段数（16 种竖直偏移取均值，底面 = 等高线投影）"
                    + ("；立方体按等体积取 range" if equal_volume else ""), fontsize=11)
    figure.savefig(HERE / ("09_surface_3d_equal_volume.png" if equal_volume else "07_surface_3d.png"))
    plt.close(figure)


def main() -> None:
    style()
    validate()
    offsets = axes_offsets(SIDE // 2)
    print()
    print(f"sweep: {len(RADII)} radii x {len(offsets)} centre offsets")
    results = sweep(RADII, offsets)
    equal_volume = sweep_equal_volume(RADII, offsets)
    sphere_blocks = lattice_volumes(int(RADII.max()))
    cube_blocks = {radius: (2 * int(np.floor(radius)) + 1) ** 3 for radius in range(int(RADII.max()) + 1)}
    report(RADII, results, sphere_blocks, equal_volume, cube_blocks)
    plot_cut_vs_radius(RADII, results)
    plot_offset_sensitivity(RADII, results)
    plot_cost_split(RADII, results)
    plot_cut_per_volume(RADII, results, sphere_blocks, cube_blocks)
    plot_equal_volume(RADII, results, equal_volume)
    plot_section_maps(32, (5, 5, 5))
    fine = np.array([[0, 0, 0], [5, 5, 5], [7, 7, 7]], dtype=np.int32)
    fine_radii = np.arange(1, 129)
    plot_alignment_sawtooth(fine_radii, sweep(fine_radii, fine), fine)
    plot_surfaces_3d((16, 32, 64))
    plot_surfaces_3d((16, 32, 64), equal_volume=True)
    print()
    print("figures:", ", ".join(sorted(path.name for path in HERE.glob("*.png"))))


if __name__ == "__main__":
    main()
