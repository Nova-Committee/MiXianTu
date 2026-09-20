"""Offline preview of the rift mesh, mirroring RiftMesh.java and RiftConnections.java exactly.

The shape a rift block draws cannot be checked on a dedicated server, and the client cannot be run here, so the
Java geometry is mirrored in Python and rendered with a plain perspective camera and a painter's algorithm. The
point of the picture is the shape, not the shader: flat colours with a little shading, the same alphas the block
entity renderer uses, so a lone block has to read as a point, two neighbours as a line, and a closed loop as a
filled triangle.

Coordinates are whole blocks: a rift sits at the centre of its own block, so the mesh for the block at (x, y, z)
is built around (x + 0.5, y + 0.5, z + 0.5).

Run:  python research/rift_mesh_preview.py [output.png] [thickness]
"""

from __future__ import annotations

import math
import sys
from dataclasses import dataclass

from PIL import Image, ImageDraw

THICKNESS = 0.125
SIZE = 400
CENTRE = (0.5, 0.5, 0.5)

Vec = tuple[float, float, float]


# --- mesh builders, mirrors of RiftMesh.java -------------------------------------------------------------------

def _sub(a: Vec, b: Vec) -> Vec:
    return (a[0] - b[0], a[1] - b[1], a[2] - b[2])


def _add(a: Vec, b: Vec) -> Vec:
    return (a[0] + b[0], a[1] + b[1], a[2] + b[2])


def _scale(a: Vec, factor: float) -> Vec:
    return (a[0] * factor, a[1] * factor, a[2] * factor)


def _dot(a: Vec, b: Vec) -> float:
    return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]


def _cross(a: Vec, b: Vec) -> Vec:
    return (a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])


def _normalize(a: Vec) -> Vec:
    length = math.sqrt(_dot(a, a))
    return (0.0, 0.0, 0.0) if length < 1.0e-9 else _scale(a, 1.0 / length)


def node(thickness: float) -> list[tuple[Vec, Vec, Vec, Vec]]:
    low = 0.5 - thickness / 2.0
    high = 0.5 + thickness / 2.0
    a = (low, low, low)
    b = (high, low, low)
    c = (high, high, low)
    d = (low, high, low)
    e = (low, low, high)
    f = (high, low, high)
    g = (high, high, high)
    h = (low, high, high)
    return [(a, e, h, d), (b, c, g, f), (a, b, f, e), (d, h, g, c), (a, d, c, b), (e, f, g, h)]


def beam(frm: Vec, to: Vec, radius: float) -> list[tuple[Vec, Vec, Vec, Vec]]:
    axis = _sub(to, frm)
    if _dot(axis, axis) < 1.0e-9:
        return []
    unit = _normalize(axis)
    helper = (0.0, 1.0, 0.0) if abs(unit[1]) < 0.9 else (1.0, 0.0, 0.0)
    side = _normalize(_cross(unit, helper))
    up = _normalize(_cross(unit, side))
    opposite = _scale(side, -1.0)
    ring = [
        _scale(_add(side, up), radius),
        _scale(_sub(side, up), radius),
        _scale(_sub(opposite, up), radius),
        _scale(_add(opposite, up), radius),
    ]
    quads = []
    for index in range(4):
        start = ring[index]
        end = ring[(index + 1) % 4]
        quads.append((_add(frm, start), _add(frm, end), _add(to, end), _add(to, start)))
    return quads


def link_share(neighbour: Vec, thickness: float) -> list[tuple[Vec, Vec, Vec, Vec]]:
    return beam(CENTRE, _scale(_add(CENTRE, neighbour), 0.5), thickness / 2.0)


def triangle_face(own: Vec, forward: Vec, backward: Vec) -> tuple[Vec, Vec, Vec, Vec]:
    centroid = _scale(_add(_add(own, forward), backward), 1.0 / 3.0)
    return (own, _scale(_add(own, forward), 0.5), centroid, _scale(_add(own, backward), 0.5))


def triangle_share(own: Vec, forward: Vec, backward: Vec, thickness: float) -> list[tuple[Vec, Vec, Vec, Vec]]:
    normal = _normalize(_cross(_sub(forward, own), _sub(backward, own)))
    if _dot(normal, normal) < 1.0e-9:
        return []
    offset = _scale(normal, thickness / 2.0)
    face = triangle_face(own, forward, backward)
    top = [_add(vertex, offset) for vertex in face]
    bottom = [_sub(vertex, offset) for vertex in face]
    return [
        (top[0], top[1], top[2], top[3]),
        (bottom[0], bottom[1], bottom[2], bottom[3]),
        (top[0], top[1], bottom[1], bottom[0]),
        (top[3], top[0], bottom[0], bottom[3]),
    ]


# --- connectivity, mirrors of RiftConnections.java -------------------------------------------------------------

OFFSETS = [(x, y, z)
           for x in (-1, 0, 1) for y in (-1, 0, 1) for z in (-1, 0, 1)
           if (x, y, z) != (0, 0, 0)]


@dataclass(frozen=True)
class Rift:
    pos: tuple[int, int, int]


def _centre(pos: tuple[int, int, int]) -> Vec:
    return (pos[0] + 0.5, pos[1] + 0.5, pos[2] + 0.5)


def connected(one: Rift, rifts: dict[tuple[int, int, int], Rift]) -> list[Rift]:
    found = []
    for offset in OFFSETS:
        other = rifts.get((one.pos[0] + offset[0], one.pos[1] + offset[1], one.pos[2] + offset[2]))
        if other is not None:
            found.append(other)
    return found


def adjacent(one: tuple[int, int, int], other: tuple[int, int, int]) -> bool:
    return (one != other
            and abs(one[0] - other[0]) <= 1
            and abs(one[1] - other[1]) <= 1
            and abs(one[2] - other[2]) <= 1)


def loops(self_pos: tuple[int, int, int], links: list[tuple[int, int, int]]) -> list[tuple[tuple[int, int, int], tuple[int, int, int]]]:
    found = []
    for first in range(len(links)):
        for second in range(first + 1, len(links)):
            one = links[first]
            other = links[second]
            if not adjacent(one, other):
                continue
            triple = sorted((self_pos, one, other))
            index = triple.index(self_pos)
            found.append((triple[(index + 1) % 3], triple[(index + 2) % 3]))
    return found


# --- rendering -------------------------------------------------------------------------------------------------

NODE_COLOR = (227, 172, 245)
LINK_COLOR = (200, 90, 235)
ALPHA = 1.0
BACKGROUND = (16, 14, 22, 255)
GRID = (58, 54, 74, 255)


@dataclass
class Face:
    vertices: list[Vec]
    color: tuple[int, int, int]
    alpha: float
    role: str


def build_faces(rifts: list[Rift]) -> list[Face]:
    """Everything the client would draw, block by block, in world coordinates."""
    lookup = {rift.pos: rift for rift in rifts}
    faces: list[Face] = []
    for rift in rifts:
        base = _centre(rift.pos)
        for quad in node(THICKNESS):
            faces.append(Face([_add(base, vertex) for vertex in quad], NODE_COLOR, ALPHA, "node"))
        links = connected(rift, lookup)
        for link in links:
            neighbour = _sub(_centre(link.pos), rift.pos)
            for quad in link_share(neighbour, THICKNESS):
                faces.append(Face([_add(base, vertex) for vertex in quad], LINK_COLOR, ALPHA, "link"))
        for forward, backward in loops(rift.pos, [link.pos for link in links]):
            ahead = _sub(_centre(forward), rift.pos)
            behind = _sub(_centre(backward), rift.pos)
            for quad in triangle_share(CENTRE, ahead, behind, THICKNESS):
                faces.append(Face([_add(base, vertex) for vertex in quad], LINK_COLOR, ALPHA, "fill"))
    return faces


def render(rifts: list[Rift], size: int, eye: Vec, target: Vec, fov: float = 42.0) -> Image.Image:
    image = Image.new("RGBA", (size, size), BACKGROUND)
    forward = _normalize(_sub(target, eye))
    right = _normalize(_cross(forward, (0.0, 1.0, 0.0)))
    up = _cross(right, forward)
    focal = (size / 2.0) / math.tan(math.radians(fov) / 2.0)

    def project(point: Vec):
        relative = _sub(point, eye)
        depth = _dot(relative, forward)
        if depth <= 0.05:
            return None, depth
        return (size / 2.0 + _dot(relative, right) * focal / depth,
                size / 2.0 - _dot(relative, up) * focal / depth), depth

    # A floor grid under the structure, so the picture has a horizon to read depth against.
    draw = ImageDraw.Draw(image)
    for step in range(-3, 6):
        for ends in ((step, -3, step, 6), (-3, step, 6, step)):
            a, _ = project((ends[0], 0.0, ends[1]))
            b, _ = project((ends[2], 0.0, ends[3]))
            if a and b:
                draw.line([a, b], fill=GRID, width=1)

    faces = build_faces(rifts)
    shaded = []
    for face in faces:
        points = []
        depths = []
        for vertex in face.vertices:
            point, depth = project(vertex)
            if point is None:
                points = []
                break
            points.append(point)
            depths.append(depth)
        if not points:
            continue
        normal = _normalize(_cross(_sub(face.vertices[1], face.vertices[0]), _sub(face.vertices[2], face.vertices[0])))
        light = 0.55 + 0.45 * abs(_dot(normal, _normalize((0.4, 0.85, 0.35))))
        color = tuple(min(255, int(channel * light)) for channel in face.color)
        shaded.append((sum(depths) / len(depths), points, color, face.alpha, face.role))

    # Painter's algorithm: far faces first. Enough for a preview, and it keeps translucent fills behind the
    # links that bound them, which is what the real draw order does too.
    shaded.sort(key=lambda entry: -entry[0])
    for _, points, color, alpha, _ in shaded:
        layer = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        ImageDraw.Draw(layer).polygon(points, fill=color + (int(alpha * 255),))
        image = Image.alpha_composite(image, layer)
    return image.convert("RGB")


def scene(name: str, rifts: list[Rift]) -> Image.Image:
    positions = [_centre(rift.pos) for rift in rifts]
    target = tuple(sum(axis) / len(positions) for axis in zip(*positions))
    distance = 3.4 + max(len(rifts) ** 0.5, 1.0) * 0.9
    eye = (target[0] + distance * 0.72, target[1] + distance * 0.55, target[2] + distance * 0.72)
    faces = build_faces(rifts)
    counts = {role: sum(1 for face in faces if face.role == role) for role in ("node", "link", "fill")}
    print(f"  {name}: {len(rifts)} blocks, faces node={counts['node']} link={counts['link']} fill={counts['fill']}")
    return render(rifts, SIZE, eye, target)


def main() -> None:
    global THICKNESS
    output = sys.argv[1] if len(sys.argv) > 1 else "research/rift_mesh_preview.png"
    if len(sys.argv) > 2:
        THICKNESS = float(sys.argv[2])

    single = [Rift((0, 0, 0))]
    pair = [Rift((0, 0, 0)), Rift((1, 0, 0))]
    loop = [Rift((0, 0, 0)), Rift((1, 0, 0)), Rift((0, 1, 0))]
    wall = [Rift((x, y, 0)) for x in range(3) for y in range(3)]
    # Every block of a 2x2x2 reaches every other one, so this is the densest shape a single 3x3x3 can hold.
    blob = [Rift((x, y, z)) for x in range(2) for y in range(2) for z in range(2)]
    step = [Rift((x, x, x)) for x in range(3)]
    frame = [Rift((x, y, z)) for x in range(3) for y in range(3) for z in (0, 2)]

    shapes = [
        ("one block", single),
        ("two linked", pair),
        ("closed loop", loop),
        ("3x3 wall", wall),
        ("2x2x2 blob", blob),
        ("space diagonal", step),
        ("two layers", frame),
    ]
    print(f"thickness {THICKNESS}, drawn fully opaque:")
    panels = [scene(name, rifts) for name, rifts in shapes]

    sheet = Image.new("RGB", (SIZE * len(panels), SIZE))
    for index, panel in enumerate(panels):
        sheet.paste(panel, (SIZE * index, 0))
    sheet.save(output)
    print(f"wrote {output} ({sheet.width}x{sheet.height})")


if __name__ == "__main__":
    main()
