"""Generate resource-pack-overridable classic container GUI textures."""

from pathlib import Path

from PIL import Image, ImageDraw


ROOT = Path(__file__).parents[1] / "src/main/resources/assets/mxt/textures/gui/classic"


def panel(size: tuple[int, int], title_height: int = 0) -> Image.Image:
    width, height = size
    image = Image.new("RGBA", size, (198, 198, 198, 255))
    draw = ImageDraw.Draw(image)
    draw.rectangle((0, 0, width - 1, height - 1), fill=(0, 0, 0, 255))
    draw.rectangle((1, 1, width - 2, height - 2), fill=(255, 255, 255, 255))
    draw.rectangle((2, 2, width - 3, height - 3), fill=(139, 139, 139, 255))
    draw.rectangle((3, 3, width - 4, height - 4), fill=(198, 198, 198, 255))
    if title_height:
        draw.rectangle((4, 4, width - 5, title_height), fill=(184, 184, 184, 255))
        draw.line((4, title_height, width - 5, title_height), fill=(86, 86, 86, 255))
        draw.line((4, title_height + 1, width - 5, title_height + 1), fill=(238, 238, 238, 255))
    return image


def slot(size: int, selected: bool = False) -> Image.Image:
    image = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    if selected:
        draw.rectangle((0, 0, size - 1, size - 1), fill=(255, 245, 150, 255))
        draw.rectangle((1, 1, size - 2, size - 2), fill=(201, 174, 74, 255))
    else:
        draw.rectangle((0, 0, size - 1, size - 1), fill=(55, 55, 55, 255))
        draw.rectangle((1, 1, size - 2, size - 2), fill=(139, 139, 139, 255))
    draw.rectangle((2, 2, size - 3, size - 3), fill=(55, 55, 55, 255))
    draw.line((2, 2, size - 3, 2), fill=(29, 29, 29, 255))
    draw.line((2, 2, 2, size - 3), fill=(29, 29, 29, 255))
    draw.line((3, size - 3, size - 3, size - 3), fill=(139, 139, 139, 255))
    draw.line((size - 3, 3, size - 3, size - 3), fill=(139, 139, 139, 255))
    return image


def progress_panel() -> Image.Image:
    image = panel((132, 122), 0)
    draw = ImageDraw.Draw(image)
    draw.rectangle((5, 5, 126, 116), fill=(169, 169, 169, 255))
    draw.rectangle((6, 6, 125, 115), outline=(75, 75, 75, 255))
    return image


def hotbar_configuration_panel() -> Image.Image:
    return panel((232, 260), 24)


def player_preview() -> Image.Image:
    image = Image.new("RGBA", (120, 96), (139, 139, 139, 255))
    draw = ImageDraw.Draw(image)
    draw.rectangle((0, 0, 119, 95), fill=(55, 55, 55, 255))
    draw.rectangle((1, 1, 118, 94), fill=(255, 255, 255, 255))
    draw.rectangle((2, 2, 117, 93), fill=(139, 139, 139, 255))
    draw.rectangle((3, 3, 116, 92), fill=(198, 198, 198, 255))
    return image


def progress_bar() -> Image.Image:
    width = 112
    image = Image.new("RGBA", (width, 7), (55, 55, 55, 255))
    draw = ImageDraw.Draw(image)
    draw.rectangle((0, 0, width - 1, 6), fill=(0, 0, 0, 255))
    draw.rectangle((1, 1, width - 2, 5), fill=(139, 139, 139, 255))
    draw.rectangle((2, 2, width - 3, 4), fill=(55, 55, 55, 255))
    return image


def main() -> None:
    ROOT.mkdir(parents=True, exist_ok=True)
    hotbar_configuration_panel().save(ROOT / "hotbar_configuration.png")
    panel((510, 315), 24).save(ROOT / "information_panel.png")
    progress_panel().save(ROOT / "spirit_crafting_progress.png")
    player_preview().save(ROOT / "player_preview.png")
    progress_bar().save(ROOT / "progress_bar.png")
    slot(22).save(ROOT / "slot_22.png")
    slot(22, True).save(ROOT / "slot_22_selected.png")
    slot(24).save(ROOT / "slot_24.png")
    slot(24, True).save(ROOT / "slot_24_selected.png")


if __name__ == "__main__":
    main()
