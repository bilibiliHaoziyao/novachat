from PIL import Image

RES = "/workspace/apps/android/src/main/res"

# Source artwork: existing adaptive foreground (transparent background).
src = Image.open(f"{RES}/mipmap-xxxhdpi/ic_launcher_foreground.png").convert("RGBA")
art = src.crop(src.getchannel("A").getbbox())  # 324x324

mono = Image.open(f"{RES}/mipmap-xxxhdpi/ic_launcher_monochrome.png").convert("RGBA")
mono_art = mono.crop(mono.getchannel("A").getbbox())

DENS = {
    "mdpi": 1.0,
    "hdpi": 1.5,
    "xhdpi": 2.0,
    "xxhdpi": 3.0,
    "xxxhdpi": 4.0,
}


def paste_centered(canvas, img, content_ratio):
    size = round(canvas * content_ratio)
    scaled = img.resize((size, size), Image.LANCZOS)
    off = (canvas - size) // 2
    out = Image.new("RGBA", (canvas, canvas), (0, 0, 0, 0))
    out.paste(scaled, (off, off), scaled)
    return out


def white_silhouette(img):
    out = Image.new("RGBA", img.size, (255, 255, 255, 0))
    out.putalpha(img.getchannel("A"))
    return out


# 1) Adaptive foreground / monochrome: 108dp canvas, artwork occupies ~62% (safe zone).
for name, ratio in (("ic_launcher_foreground", 0.62), ("ic_launcher_monochrome", 0.62)):
    for den, scale in DENS.items():
        canvas = round(108 * scale)
        img = paste_centered(canvas, art if name == "ic_launcher_foreground" else mono_art, ratio)
        img.save(f"{RES}/mipmap-{den}/{name}.png")

# 2) Welcome-screen logo (replaces the old Delta Chat intro image).
paste_centered(800, art, 0.72).save(f"{RES}/drawable/intro1.png")

# 3) Notification icons: white silhouettes of the logo, 24dp base.
mono_white = white_silhouette(mono_art)
for name in ("icon_notification", "notification_permanent"):
    for den, scale in DENS.items():
        canvas = round(24 * scale)
        paste_centered(canvas, mono_white, 0.92).save(f"{RES}/drawable-{den}/{name}.png")

print("done")