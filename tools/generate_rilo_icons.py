"""Deterministically derive RILO launcher resources from app/icon-source/222.png.

Requires Pillow. The source already has a rounded black tile, so only its white
symbol is used as the transparent adaptive foreground. Android supplies the
black adaptive background and applies its own mask.
"""

from pathlib import Path
from PIL import Image, ImageOps

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "app/icon-source/222.png"
RES = ROOT / "app/src/main/res"

source = Image.open(SOURCE).convert("RGB")
if source.width != source.height:
    raise ValueError("The icon source must be square")

size = source.width
center = (size - 1) / 2
limit_squared = (size * 0.44) ** 2
gray = ImageOps.grayscale(source)
alpha = Image.new("L", source.size)
gray_pixels = gray.load()
alpha_pixels = alpha.load()
for y in range(size):
    dy_squared = (y - center) ** 2
    for x in range(size):
        if (x - center) ** 2 + dy_squared < limit_squared:
            value = gray_pixels[x, y]
            alpha_pixels[x, y] = 0 if value <= 32 else round((value - 32) * 255 / 223)

symbol = Image.new("RGBA", source.size, "white")
symbol.putalpha(alpha)
scaled_size = round(size * 0.92)
symbol = symbol.resize((scaled_size, scaled_size), Image.Resampling.LANCZOS)
foreground = Image.new("RGBA", source.size)
foreground.alpha_composite(symbol, ((size - scaled_size) // 2, (size - scaled_size) // 2))

foreground_path = RES / "drawable-nodpi/rilo_launcher_foreground.png"
foreground_path.parent.mkdir(parents=True, exist_ok=True)
foreground.resize((432, 432), Image.Resampling.LANCZOS).save(foreground_path)

for density, pixels in [("mdpi", 48), ("hdpi", 72), ("xhdpi", 96),
                        ("xxhdpi", 144), ("xxxhdpi", 192)]:
    directory = RES / f"mipmap-{density}"
    directory.mkdir(parents=True, exist_ok=True)
    flat = Image.new("RGBA", source.size, "black")
    flat.alpha_composite(foreground)
    flat.resize((pixels, pixels), Image.Resampling.LANCZOS).save(directory / "ic_launcher.png")

    round_icon = Image.new("RGBA", source.size)
    round_mask = Image.new("L", source.size)
    from PIL import ImageDraw
    ImageDraw.Draw(round_mask).ellipse((0, 0, size - 1, size - 1), fill=255)
    round_icon.paste(flat, (0, 0), round_mask)
    round_icon.resize((pixels, pixels), Image.Resampling.LANCZOS).save(directory / "ic_launcher_round.png")

print("Generated adaptive foreground, regular and round legacy launchers")
