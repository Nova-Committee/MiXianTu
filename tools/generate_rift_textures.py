"""Generate the rift textures: the surface pattern, the block's item icon and the anchor's item icon.

Run with `python tools/generate_rift_textures.py`. Every output lands in
`src/main/resources/assets/mxt/textures/`.
"""

from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).parents[1] / "src/main/resources/assets/mxt/textures"


def periodic_noise(rng: np.random.Generator, size: int, cells: int) -> np.ndarray:
    """A smooth, wrapping value noise: the lattice repeats every `cells` steps, so the tile is seamless."""
    lattice = rng.random((cells, cells))
    coords = np.arange(size) * cells / size
    low = np.floor(coords).astype(int) % cells
    high = (low + 1) % cells
    weight = coords - np.floor(coords)
    weight = weight * weight * (3.0 - 2.0 * weight)
    along_x = weight[:, None]
    along_y = weight[None, :]
    corners = (
        lattice[np.ix_(low, low)] * (1 - along_x) * (1 - along_y)
        + lattice[np.ix_(high, low)] * along_x * (1 - along_y)
        + lattice[np.ix_(low, high)] * (1 - along_x) * along_y
        + lattice[np.ix_(high, high)] * along_x * along_y
    )
    return corners


def filament_field(size: int = 128, seed: int = 20260920) -> np.ndarray:
    """A ridged fractal field in [0, 1]: thin bright lines rather than soft blobs, which reads as a tear."""
    rng = np.random.default_rng(seed)
    total = np.zeros((size, size))
    amplitude = 1.0
    for cells in (4, 8, 16, 32, 64):
        total += periodic_noise(rng, size, cells) * amplitude
        amplitude *= 0.55
    total = (total - total.min()) / (total.max() - total.min())
    ridged = 1.0 - np.abs(total * 2.0 - 1.0)
    return np.clip(ridged, 0.0, 1.0) ** 1.6


def surface_texture() -> Image.Image:
    """RGB is the pattern brightness, alpha is how much of the surface the pattern covers."""
    field = filament_field()
    brightness = 0.25 + 0.75 * field
    alpha = np.clip((field - 0.10) / 0.90, 0.0, 1.0) ** 0.7
    rgba = np.zeros((*field.shape, 4), dtype=np.uint8)
    rgba[..., 0] = np.clip(brightness * 255, 0, 255)
    rgba[..., 1] = np.clip(brightness * 255, 0, 255)
    rgba[..., 2] = np.clip(brightness * 280, 0, 255)
    rgba[..., 3] = np.clip(alpha * 255, 0, 255)
    return Image.fromarray(rgba, "RGBA")


TEAR = [(32, 1), (40, 18), (43, 33), (32, 63), (21, 33), (24, 18)]


def scaled(points: list[tuple[int, int]], factor: float) -> list[tuple[float, float]]:
    centre_x = sum(point[0] for point in points) / len(points)
    centre_y = sum(point[1] for point in points) / len(points)
    return [
        (centre_x + (x - centre_x) * factor, centre_y + (y - centre_y) * factor)
        for x, y in points
    ]


def rift_icon(scale: int = 4, size: int = 16) -> Image.Image:
    """The rift block's icon: a lens-shaped tear with a bright core and a violet glow, drawn large and downsampled."""
    canvas = (size * scale, size * scale)
    glow = Image.new("RGBA", canvas, (0, 0, 0, 0))
    ImageDraw.Draw(glow).polygon(TEAR, fill=(126, 82, 255, 210))
    glow = glow.filter(ImageFilter.GaussianBlur(scale * 1.6))

    shell = Image.new("RGBA", canvas, (0, 0, 0, 0))
    draw = ImageDraw.Draw(shell)
    draw.polygon(scaled(TEAR, 1.0), fill=(38, 12, 74, 255))
    draw.polygon(scaled(TEAR, 0.62), fill=(176, 148, 255, 255))
    draw.polygon(scaled(TEAR, 0.30), fill=(240, 240, 255, 255))

    image = Image.alpha_composite(glow, shell)
    for point in ((32, 12), (27, 30), (37, 30), (32, 48)):
        draw = ImageDraw.Draw(image)
        draw.ellipse(
            [point[0] - scale * 0.6, point[1] - scale * 0.6, point[0] + scale * 0.6, point[1] + scale * 0.6],
            fill=(255, 255, 255, 255),
        )
    return image.resize((size, size), Image.LANCZOS)


def anchor_icon(scale: int = 4, size: int = 16) -> Image.Image:
    """The anchor's icon: an anchor glyph, so the tool reads as the thing that ties a rift to a dimension."""
    canvas = (size * scale, size * scale)
    unit = scale
    dark = (38, 12, 74, 255)
    mid = (126, 82, 255, 255)
    light = (200, 184, 255, 255)
    glow = Image.new("RGBA", canvas, (0, 0, 0, 0))
    ImageDraw.Draw(glow).line([(8 * unit, 3 * unit), (8 * unit, 12 * unit)], fill=(126, 82, 255, 200), width=int(2.2 * unit))
    glow = glow.filter(ImageFilter.GaussianBlur(scale * 1.4))

    shell = Image.new("RGBA", canvas, (0, 0, 0, 0))
    draw = ImageDraw.Draw(shell)
    # Shaft, ring at the top, crossbar and the curved arms at the bottom: the shape reads as an anchor even at
    # sixteen pixels, and the violet palette keeps it in the same family as the rift itself.
    draw.line([(8 * unit, 4 * unit), (8 * unit, 13 * unit)], fill=dark, width=int(2.6 * unit))
    draw.line([(8 * unit, 4 * unit), (8 * unit, 13 * unit)], fill=mid, width=int(1.4 * unit))
    draw.ellipse([6 * unit, 1.4 * unit, 10 * unit, 5.4 * unit], outline=dark, width=int(1.6 * unit))
    draw.ellipse([6.7 * unit, 2.1 * unit, 9.3 * unit, 4.7 * unit], outline=light, width=int(0.9 * unit))
    draw.line([(4 * unit, 6.5 * unit), (12 * unit, 6.5 * unit)], fill=dark, width=int(2.2 * unit))
    draw.line([(4 * unit, 6.5 * unit), (12 * unit, 6.5 * unit)], fill=light, width=int(1.0 * unit))
    draw.arc([2.6 * unit, 6 * unit, 13.4 * unit, 15 * unit], start=20, end=160, fill=dark, width=int(2.4 * unit))
    draw.arc([2.6 * unit, 6 * unit, 13.4 * unit, 15 * unit], start=20, end=160, fill=mid, width=int(1.2 * unit))
    for fluke in ((3.4, 10.6), (12.6, 10.6)):
        draw.polygon(
            [
                ((fluke[0] + 1.6) * unit, fluke[1] * unit),
                ((fluke[0] - 0.2) * unit, (fluke[1] - 1.4) * unit),
                ((fluke[0] - 0.2) * unit, (fluke[1] + 1.4) * unit),
            ],
            fill=light,
        )
    image = Image.alpha_composite(glow, shell)
    return image.resize((size, size), Image.LANCZOS)


def main() -> None:
    (ROOT / "entity").mkdir(parents=True, exist_ok=True)
    (ROOT / "item").mkdir(parents=True, exist_ok=True)
    surface_texture().save(ROOT / "entity" / "rift.png")
    rift_icon().save(ROOT / "item" / "rift.png")
    anchor_icon().save(ROOT / "item" / "rift_anchor.png")


if __name__ == "__main__":
    main()
