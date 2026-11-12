#!/usr/bin/env python3
"""Makes the SOMEON brand images from the icon artwork (someon_icon_source.png):
the launcher icon layer, the in-app icon, and the wordmark and lockups for documents.

The wordmark is drawn the way the app draws it (ui/Wordmark.kt): monoline neon tubes,
"SOME" unlit and "ON" lit pink, the second O a power symbol. Keep the two in step."""
import math
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw, ImageFilter, ImageFont

HERE = Path(__file__).resolve().parent
REPO = HERE.parent.parent
SOURCE = HERE / "someon_icon_source.png"
FONT_BOLD = "/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc"
FONT_REGULAR = "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc"
KOREAN = 1  # index of the KR face in the Noto CJK collections

# The rounded tile inside the artwork (the rest is a white backdrop with a drop shadow).
TILE = (152, 145, 1089, 1098)
TILE_RADIUS = 250

PINK = (247, 76, 147)
PINK_LIGHT = (255, 132, 173)
OFF_DAY = (102, 94, 146)
OFF_NIGHT = (142, 136, 184)
INK = (21, 25, 40)
SLOGAN = "당신의 일상에 썸을 켜다."


def tile_square(size):
    """The tile scaled to a square, its rounded corners filled from the edge inward (full bleed)."""
    src = Image.open(SOURCE).convert("RGB").crop(TILE)
    w, h = src.size
    r = TILE_RADIUS - 6
    inside = r - 18  # corners are filled from here, well inside the tile's grey outline
    px = src.load()
    out = src.copy()
    opx = out.load()
    for cx, cy, xs, ys in (
        (r, r, range(0, r), range(0, r)),
        (w - 1 - r, r, range(w - r, w), range(0, r)),
        (r, h - 1 - r, range(0, r), range(h - r, h)),
        (w - 1 - r, h - 1 - r, range(w - r, w), range(h - r, h)),
    ):
        for y in ys:
            for x in xs:
                d = math.hypot(x - cx, y - cy)
                if d > r:
                    opx[x, y] = px[int(cx + (x - cx) * inside / d), int(cy + (y - cy) * inside / d)]
    # The outline along the straight edges too.
    edge = 10
    for y in range(h):
        for x in list(range(edge)) + list(range(w - edge, w)):
            opx[x, y] = opx[edge if x < edge else w - 1 - edge, y]
    for x in range(w):
        for y in list(range(edge)) + list(range(h - edge, h)):
            opx[x, y] = opx[x, edge if y < edge else h - 1 - edge]
    return out.resize((size, size), Image.LANCZOS)


def rounded_mask(size, radius, scale=4):
    m = Image.new("L", (size * scale, size * scale), 0)
    ImageDraw.Draw(m).rounded_rectangle((0, 0, size * scale - 1, size * scale - 1), radius * scale, fill=255)
    return m.resize((size, size), Image.LANCZOS)


def icon(size):
    """The tile as an icon: rounded corners, transparent outside."""
    art = tile_square(size).convert("RGBA")
    art.putalpha(rounded_mask(size, round(size * TILE_RADIUS / (TILE[2] - TILE[0]))))
    return art


def shadowed(art):
    """A soft drop shadow, so the pale tile keeps its edge on white paper."""
    pad = round(art.width * 0.08)
    out = Image.new("RGBA", (art.width + pad * 2, art.height + pad * 2), (0, 0, 0, 0))
    shade = Image.new("L", out.size, 0)
    shade.paste(art.getchannel("A").point(lambda a: int(a * 0.3)), (pad, pad + round(art.width * 0.025)))
    out.paste(Image.new("RGBA", out.size, (60, 50, 110, 255)), (0, 0), shade.filter(ImageFilter.GaussianBlur(art.width * 0.035)))
    out.alpha_composite(art, (pad, pad))
    return out


def launcher_layer(size=432):
    """Adaptive-icon foreground (108dp): the tile fills the middle 80dp, its colours carry on to the edge."""
    inner = round(size * 80 / 108)
    art = tile_square(inner)
    back = art.resize((24, 24), Image.BILINEAR).resize((size, size), Image.BICUBIC).filter(ImageFilter.GaussianBlur(size / 40))
    feather = Image.new("L", (inner, inner), 0)
    ImageDraw.Draw(feather).rectangle((6, 6, inner - 7, inner - 7), fill=255)
    feather = feather.filter(ImageFilter.GaussianBlur(4))
    off = (size - inner) // 2
    back.paste(art, (off, off), feather)
    return back


# --- wordmark: letters as strokes in units of the cap height (y down, 0 = top, 1 = baseline) ---
STROKE = 0.15
GAP = 0.2


def arc(cx, cy, rx, ry, start, sweep, steps=64):
    return [(cx + rx * math.cos(math.radians(start + sweep * i / steps)), cy + ry * math.sin(math.radians(start + sweep * i / steps))) for i in range(steps + 1)]


def letters():
    """(width, [strokes]) per letter, and which are lit. Matches Wordmark.kt."""
    s = arc(0.31, 0.235, 0.29, 0.235, -20, -250) + arc(0.31, 0.735, 0.31, 0.265, -90, 250)[1:]
    o = arc(0.5, 0.5, 0.5, 0.5, 0, 360, 96)
    m = [(0, 1), (0, 0), (0.42, 0.62), (0.84, 0), (0.84, 1)]
    e = [[(0.52, 0), (0, 0), (0, 1), (0.52, 1)], [(0, 0.5), (0.44, 0.5)]]
    power = [arc(0.5, 0.5, 0.5, 0.5, -55, 290, 90), [(0.5, -0.06), (0.5, 0.4)]]
    n = [(0, 1), (0, 0), (0.74, 1), (0.74, 0)]
    return [
        (0.62, [s], False),
        (1.0, [o], False),
        (0.84, [m], False),
        (0.52, e, False),
        (1.0, power, True),
        (0.74, [n], True),
    ]


def wordmark_width():
    return sum(w for w, _, _ in letters()) + GAP * (len(letters()) - 1) + STROKE


def draw_strokes(draw, strokes, unit, ox, oy, width, fill):
    """Round-capped, round-joined strokes, stamped as discs (PIL's own joints leave specks)."""
    r = width / 2
    for line in strokes:
        pts = [(ox + x * unit, oy + y * unit) for x, y in line]
        for (x0, y0), (x1, y1) in zip(pts, pts[1:] or pts):
            n = max(1, int(math.hypot(x1 - x0, y1 - y0) / 1.5))
            for i in range(n + 1):
                x, y = x0 + (x1 - x0) * i / n, y0 + (y1 - y0) * i / n
                draw.ellipse((x - r, y - r, x + r, y + r), fill=fill)


def wordmark(cap, off=OFF_DAY, glow=1.0, scale=4):
    """The wordmark with its cap height `cap` px, on transparency (room around it for the glow)."""
    u = cap * scale
    pad = u * 0.45
    w = round(wordmark_width() * u + pad * 2)
    h = round((1 + STROKE) * u + pad * 2)
    ox, oy = pad + STROKE * u / 2, pad + STROKE * u / 2
    unlit = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    lit_mask = Image.new("L", (w, h), 0)
    core = Image.new("L", (w, h), 0)
    du, dl, dc = ImageDraw.Draw(unlit), ImageDraw.Draw(lit_mask), ImageDraw.Draw(core)
    x = 0.0
    for width, strokes, lit in letters():
        if lit:
            draw_strokes(dl, strokes, u, ox + x * u, oy, STROKE * u, 255)
            draw_strokes(dc, strokes, u, ox + x * u, oy, STROKE * u * 0.2, 255)
        else:
            draw_strokes(du, strokes, u, ox + x * u, oy, STROKE * u, off + (255,))
        x += width + GAP
    # Lit tubes: a pink gradient, a soft glow around them, a pale core.
    lit_start = round(ox + (sum(width for width, _, _ in letters()[:4]) + GAP * 4) * u)
    gradient = Image.new("L", (w, h), 255)
    gradient.paste(0, (0, 0, lit_start, h))
    gradient.paste(Image.linear_gradient("L").rotate(90).resize((round(w - pad) - lit_start, h)), (lit_start, 0))
    lit = Image.composite(Image.new("RGBA", (w, h), PINK_LIGHT + (255,)), Image.new("RGBA", (w, h), PINK + (255,)), gradient)
    out = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    if glow:
        halo = lit_mask.filter(ImageFilter.MaxFilter(9)).filter(ImageFilter.GaussianBlur(u * 0.16))
        halo = halo.point(lambda a: int(a * 0.75 * glow))
        out.paste(Image.new("RGBA", (w, h), PINK + (255,)), (0, 0), halo)
    out.alpha_composite(unlit)
    lit.putalpha(lit_mask)
    out.alpha_composite(lit)
    core = core.filter(ImageFilter.GaussianBlur(u * 0.012)).point(lambda a: int(a * 0.4))
    out.paste(Image.new("RGBA", (w, h), (255, 236, 244, 255)), (0, 0), ImageChops.multiply(core, lit_mask))
    return out.resize((w // scale, h // scale), Image.LANCZOS)


def font(size, bold=True):
    return ImageFont.truetype(FONT_BOLD if bold else FONT_REGULAR, size, index=KOREAN)


def lockup(width, height, night=False, slogan=True):
    """Icon, wordmark and slogan on the app's background: covers and title cards."""
    bg = (11, 13, 18) if night else (246, 242, 250)
    card = Image.new("RGBA", (width, height), bg + (255,))
    if not night:
        tint = Image.linear_gradient("L").rotate(90).resize((width, height))
        card = Image.composite(Image.new("RGBA", (width, height), (239, 226, 242, 255)), card, tint)
    ic = shadowed(icon(round(height * 0.34))) if not night else icon(round(height * 0.34))
    mark = wordmark(round(height * 0.11), OFF_NIGHT if night else OFF_DAY)
    text_h = round(height * 0.05)
    gap = round(height * 0.05)
    total = ic.height + gap + mark.height + (gap + text_h if slogan else 0)
    y = (height - total) // 2
    card.alpha_composite(ic, ((width - ic.width) // 2, y))
    y += ic.height + gap - round(mark.height * 0.1)
    card.alpha_composite(mark, ((width - mark.width) // 2, y))
    if slogan:
        d = ImageDraw.Draw(card)
        f = font(text_h)
        head, tail = "당신의 일상에 ", "썸을 켜다."
        tw = d.textlength(head + tail, font=f)
        x0 = (width - tw) / 2
        ty = y + mark.height - round(mark.height * 0.08)
        d.text((x0, ty), head, font=f, fill=(255, 255, 255) if night else INK)
        d.text((x0 + d.textlength(head, font=f), ty), tail, font=f, fill=PINK)
    return card


def logo(icon_px):
    """Icon and wordmark side by side, on transparency (document covers)."""
    ic = shadowed(icon(icon_px))
    mark = wordmark(round(icon_px * 0.36))
    gap = round(icon_px * 0.12)
    out = Image.new("RGBA", (ic.width + gap + mark.width, max(ic.height, mark.height)), (0, 0, 0, 0))
    out.alpha_composite(ic, (0, (out.height - ic.height) // 2))
    out.alpha_composite(mark, (ic.width + gap, (out.height - mark.height) // 2))
    return out


def main():
    res = REPO / "app/src/main/res"
    (res / "drawable-xxxhdpi").mkdir(exist_ok=True)
    launcher_layer(432).save(res / "drawable-xxxhdpi/ic_launcher_art.png", optimize=True)
    icon(512).save(res / "drawable-nodpi/logo_icon.png", optimize=True)

    brand = REPO / "submission/brand"
    brand.mkdir(parents=True, exist_ok=True)
    icon(1024).save(brand / "SOMEON_icon.png", optimize=True)
    wordmark(240).save(brand / "SOMEON_wordmark.png", optimize=True)
    wordmark(240, OFF_NIGHT).save(brand / "SOMEON_wordmark_night.png", optimize=True)
    logo(360).save(brand / "SOMEON_logo.png", optimize=True)
    logo(360).save(REPO / "tools/manual/img/logo.png", optimize=True)
    lockup(1920, 1080).convert("RGB").save(brand / "SOMEON_lockup.png", optimize=True)
    lockup(1920, 1080, night=True).convert("RGB").save(brand / "SOMEON_lockup_night.png", optimize=True)


if __name__ == "__main__":
    main()
