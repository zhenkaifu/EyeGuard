"""Split the approved Qi-1 illustration into a static body and movable ribbons.

The generated master is never modified. Coordinates refer to its 1024px canvas.
"""
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
MASTER = ROOT / ".android-build/art/taoist_qi_1.png"
OUT = ROOT / "app/src/main/res/drawable"
PREVIEW = ROOT / ".android-build/art/taoist_layers_preview.png"

RIBBONS = {
    "left": [
        (327, 337), (300, 361), (266, 389), (226, 408), (195, 434),
        (177, 469), (179, 506), (199, 540), (229, 572), (241, 604),
        (225, 637), (187, 662), (136, 682), (86, 713), (40, 764),
        (86, 751), (135, 740), (187, 723), (235, 694), (270, 652),
        (284, 608), (271, 563), (239, 522), (217, 486), (220, 454),
        (248, 431), (286, 413), (318, 390), (340, 362),
    ],
    "right": [
        (650, 247), (681, 269), (704, 305), (731, 342), (766, 376),
        (801, 391), (838, 389), (868, 399), (896, 428), (909, 465),
        (906, 513), (882, 484), (875, 450), (853, 433), (824, 434),
        (789, 436), (749, 421), (716, 397), (689, 365), (669, 332),
        (648, 292),
    ],
}


def cropped(image: Image.Image) -> Image.Image:
    box = image.getchannel("A").getbbox()
    return image.crop(box) if box else image


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    master = Image.open(MASTER).convert("RGBA")
    base = master.copy()
    # The provider's small provenance label is outside the character silhouette.
    ImageDraw.Draw(base).rectangle((0, 929, 125, 1023), fill=(0, 0, 0, 0))
    preview = Image.new("RGBA", master.size, (225, 238, 224, 255))

    for name, points in RIBBONS.items():
        mask = Image.new("L", master.size, 0)
        ImageDraw.Draw(mask).polygon(points, fill=255)
        ribbon = Image.new("RGBA", master.size, (0, 0, 0, 0))
        ribbon.paste(master, (0, 0), mask)
        cropped(ribbon).save(OUT / f"taoist_qi_1_ribbon_{name}.png")
        base.paste((0, 0, 0, 0), (0, 0, master.width, master.height), mask)

    base.save(OUT / "taoist_qi_1_base.png")
    preview.alpha_composite(base)
    for name in RIBBONS:
        ribbon = Image.open(OUT / f"taoist_qi_1_ribbon_{name}.png")
        # The preview uses the original full-canvas layers for alignment.
        mask = Image.new("L", master.size, 0)
        ImageDraw.Draw(mask).polygon(RIBBONS[name], fill=255)
        layer = Image.new("RGBA", master.size, (0, 0, 0, 0))
        layer.paste(master, (0, 0), mask)
        preview.alpha_composite(layer)
    preview.convert("RGB").save(PREVIEW, quality=92)
    for path in sorted(OUT.glob("taoist_qi_1_*.png")):
        with Image.open(path) as image:
            print(path.name, image.size, path.stat().st_size)


if __name__ == "__main__":
    main()
