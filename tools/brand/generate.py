"""Genera todas las piezas visuales: la marca y los iconos de la interfaz.

    python tools/brand/generate.py
"""
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import banner  # noqa: E402
from grid import BONE, CRIMSON  # noqa: E402
from lucide_icons import ICONS as LUCIDE  # noqa: E402
from pngwriter import rgba, write_png  # noqa: E402

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
RES = os.path.join(ROOT, "app", "src", "main", "res")

# Densidades del lanzador.
DENSITIES = (("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192))

SUPERSAMPLE = 4

NEWLINE = chr(10)


def fmt(value):
    text = f"{value:.2f}".rstrip("0").rstrip(".")
    return text if text else "0"


def badge(size, radius, mark_scale, background, mark):
    """El icono en mapa de bits: fondo carmesi recortado y el estandarte encima."""
    scale = size * mark_scale / banner.VIEW
    offset = (size - banner.VIEW * scale) / 2.0
    staff_bottom = (size - offset) / scale + 1
    cover = banner.coverage(size, scale, offset, offset, staff_bottom, SUPERSAMPLE)

    big = size * SUPERSAMPLE
    r = radius * SUPERSAMPLE
    rows = []
    for y in range(size):
        out = []
        for x in range(size):
            inside = 0
            for dy in range(SUPERSAMPLE):
                for dx in range(SUPERSAMPLE):
                    if in_rounded(x * SUPERSAMPLE + dx, y * SUPERSAMPLE + dy, big, r):
                        inside += 1
            alpha = inside / (SUPERSAMPLE * SUPERSAMPLE)
            if alpha == 0:
                out.append((0, 0, 0, 0))
                continue
            amount = cover[y][x]
            color = tuple(
                round(mark[i] * amount + background[i] * (1 - amount)) for i in range(3)
            )
            out.append(color + (round(255 * alpha),))
        rows.append(out)
    return rows


def in_rounded(x, y, size, radius):
    if radius <= 0:
        return True
    cx = min(max(x + 0.5, radius), size - radius)
    cy = min(max(y + 0.5, radius), size - radius)
    dx = x + 0.5 - cx
    dy = y + 0.5 - cy
    return dx * dx + dy * dy <= radius * radius


ICON_MAP = {
    "scan": "scan-line",
    "create": "qr-code",
    "history": "history",
    "torch": "flashlight",
    "image": "image",
    "settings": "settings",
    "copy": "copy",
    "share": "share-2",
    "open": "external-link",
    "import": "folder-open",
    "star": "star",
    "search": "search",
    "delete": "trash-2",
    "check": "check",
    "warning": "triangle-alert",
    "blocked": "ban",
    "download": "download",
    "formats": "barcode",
    "close": "x",
    "wifi": "wifi",
    "contact": "user-plus",
    "mail": "mail",
    "phone": "phone",
    "sms": "message-square",
    "map": "map-pin",
    "event": "calendar-plus",
    "author": "user",
    "license": "scale",
}


NUMBER = re.compile(r"[-+]?(?:\d+\.?\d*|\.\d+)(?:[eE][-+]?\d+)?")


WHITESPACE = " ,\t\n\r"


def normalize_path(d):
    """Reescribe un trazado SVG con cada numero completo y separado."""
    out = []
    command = ""
    index = 0
    argument = 0
    while index < len(d):
        character = d[index]
        if character.isalpha():
            command = character
            argument = 0
            out.append(character)
            index += 1
            continue
        if character in WHITESPACE:
            index += 1
            continue
        if command in "aA" and argument % 7 in (3, 4):
            out.append(("," if out and not out[-1].isalpha() else "") + character)
            index += 1
            argument += 1
            continue
        match = NUMBER.match(d, index)
        if not match:
            raise SystemExit(f"trazado ilegible en la posicion {index}: {d}")
        value = float(match.group())
        text = f"{value:.3f}".rstrip("0").rstrip(".")
        if text in ("", "-0"):
            text = "0"
        out.append(("," if out and not out[-1].isalpha() else "") + text)
        index = match.end()
        argument += 1
    return "".join(out)


def element_path(tag, a):
    """Un elemento SVG de Lucide como datos de trazado de Android."""
    if tag == "path":
        d = a["d"].lstrip()
        if d.startswith("m"):
            d = "M0 0" + d
        return normalize_path(d)
    if tag in ("circle", "ellipse"):
        cx, cy = float(a["cx"]), float(a["cy"])
        rx = float(a.get("r", a.get("rx", 0)))
        ry = float(a.get("r", a.get("ry", 0)))
        return (
            f"M{fmt(cx - rx)},{fmt(cy)}a{fmt(rx)},{fmt(ry)} 0 1,0 {fmt(2 * rx)},0"
            f"a{fmt(rx)},{fmt(ry)} 0 1,0 {fmt(-2 * rx)},0"
        )
    if tag == "rect":
        x, y = float(a.get("x", 0)), float(a.get("y", 0))
        w, h = float(a["width"]), float(a["height"])
        rx = float(a.get("rx", a.get("ry", 0)))
        ry = float(a.get("ry", rx))
        if rx == 0:
            return f"M{fmt(x)},{fmt(y)}h{fmt(w)}v{fmt(h)}h{fmt(-w)}z"
        return (
            f"M{fmt(x + rx)},{fmt(y)}H{fmt(x + w - rx)}A{fmt(rx)},{fmt(ry)} 0 0,1 {fmt(x + w)},{fmt(y + ry)}"
            f"V{fmt(y + h - ry)}A{fmt(rx)},{fmt(ry)} 0 0,1 {fmt(x + w - rx)},{fmt(y + h)}"
            f"H{fmt(x + rx)}A{fmt(rx)},{fmt(ry)} 0 0,1 {fmt(x)},{fmt(y + h - ry)}"
            f"V{fmt(y + ry)}A{fmt(rx)},{fmt(ry)} 0 0,1 {fmt(x + rx)},{fmt(y)}z"
        )
    if tag == "line":
        x1, y1, x2, y2 = (float(a[k]) for k in ("x1", "y1", "x2", "y2"))
        return f"M{fmt(x1)},{fmt(y1)}L{fmt(x2)},{fmt(y2)}"
    if tag in ("polyline", "polygon"):
        values = [float(v) for v in a["points"].replace(",", " ").split()]
        points = list(zip(values[0::2], values[1::2]))
        data = f"M{fmt(points[0][0])},{fmt(points[0][1])}" + "".join(
            f"L{fmt(x)},{fmt(y)}" for x, y in points[1:]
        )
        return data + ("z" if tag == "polygon" else "")
    raise SystemExit(f"elemento de Lucide sin convertir: {tag}")


def resistance_icon(level):
    """Resistencia a danos: un codigo con un hueco que crece en cada nivel."""
    side = (2, 5, 8, 11)[level - 1]
    start = 12 - side / 2
    return [
        ("rect", {"width": "18", "height": "18", "x": "3", "y": "3", "rx": "3"}),
        ("rect", {"width": fmt(side), "height": fmt(side), "x": fmt(start), "y": fmt(start), "rx": "1"}),
    ]


def stroke_vector(nodes, name, filled=False):
    """Un icono de trazo como vector de 24 dp."""
    data = "".join(element_path(tag, attributes) for tag, attributes in nodes)
    fill = "#FF000000" if filled else "#00000000"
    lines = [
        '<?xml version="1.0" encoding="utf-8"?>',
        "<!-- Generado por tools/brand/generate.py a partir de Lucide (ISC). No editar a mano. -->",
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
        '    android:width="24dp"',
        '    android:height="24dp"',
        '    android:viewportWidth="24"',
        '    android:viewportHeight="24">',
        "    <path",
        f'        android:fillColor="{fill}"',
        '        android:strokeColor="#FF000000"',
        '        android:strokeWidth="2"',
        '        android:strokeLineCap="round"',
        '        android:strokeLineJoin="round"',
        '        android:pathData="' + data + '" />',
        "</vector>",
        "",
    ]
    with open(name, "w", encoding="utf-8", newline=NEWLINE) as handle:
        handle.write(NEWLINE.join(lines))


def shortcut(nodes, name):
    """Un icono de atajo: circulo carmesi con el icono de trazo en hueso."""
    data = "".join(element_path(tag, attributes) for tag, attributes in nodes)
    lines = [
        '<?xml version="1.0" encoding="utf-8"?>',
        "<!-- Generado por tools/brand/generate.py a partir de Lucide (ISC). No editar a mano. -->",
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
        '    android:width="48dp"',
        '    android:height="48dp"',
        '    android:viewportWidth="48"',
        '    android:viewportHeight="48">',
        "    <path",
        f'        android:fillColor="{CRIMSON}"',
        '        android:pathData="M24,0A24,24 0 1,1 24,48A24,24 0 1,1 24,0z" />',
        "    <group",
        '        android:scaleX="1.1667"',
        '        android:scaleY="1.1667"',
        '        android:translateX="10"',
        '        android:translateY="10">',
        "        <path",
        '            android:fillColor="#00000000"',
        f'            android:strokeColor="{BONE}"',
        '            android:strokeWidth="2"',
        '            android:strokeLineCap="round"',
        '            android:strokeLineJoin="round"',
        '            android:pathData="' + data + '" />',
        "    </group>",
        "</vector>",
        "",
    ]
    with open(name, "w", encoding="utf-8", newline=NEWLINE) as handle:
        handle.write(NEWLINE.join(lines))


def banner_vector(name, viewport, color, scale=1.0, offset=0.0, staff_bottom=banner.VIEW):
    """El estandarte como vector de Android: un trazado con regla par-impar."""
    lines = [
        '<?xml version="1.0" encoding="utf-8"?>',
        "<!-- Generado por tools/brand/generate.py. No editar a mano. -->",
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
        f'    android:width="{fmt(viewport)}dp"',
        f'    android:height="{fmt(viewport)}dp"',
        f'    android:viewportWidth="{fmt(viewport)}"',
        f'    android:viewportHeight="{fmt(viewport)}">',
    ]
    indent = "    "
    if scale != 1.0 or offset != 0.0:
        lines += [
            "    <group",
            f'        android:scaleX="{fmt(scale)}"',
            f'        android:scaleY="{fmt(scale)}"',
            f'        android:translateX="{fmt(offset)}"',
            f'        android:translateY="{fmt(offset)}">',
        ]
        indent = "        "
    lines += [
        f"{indent}<path",
        f'{indent}    android:fillColor="{color}"',
        f'{indent}    android:fillType="evenOdd"',
        f'{indent}    android:pathData="' + banner.path_data(staff_bottom, ",") + '" />',
    ]
    if indent != "    ":
        lines.append("    </group>")
    lines += ["</vector>", ""]
    with open(name, "w", encoding="utf-8", newline=NEWLINE) as handle:
        handle.write(NEWLINE.join(lines))


def main():
    crimson = rgba(CRIMSON)
    bone = rgba(BONE)

    os.makedirs(os.path.join(RES, "drawable"), exist_ok=True)

    banner_vector(os.path.join(RES, "drawable", "ic_signum.xml"), banner.VIEW, "@color/signum_mark")

    scale = 0.45
    offset = 54 - banner.VIEW / 2 * scale
    staff_bottom = round((108 - offset) / scale) + 1
    banner_vector(
        os.path.join(RES, "drawable", "ic_launcher_foreground.xml"), 108, BONE, scale, offset, staff_bottom,
    )
    # Version monocroma: Android 13 la tine con el color del sistema.
    banner_vector(
        os.path.join(RES, "drawable", "ic_launcher_monochrome.xml"), 108, "#FFFFFFFF", scale, offset, staff_bottom,
    )

    for density, size in DENSITIES:
        folder = os.path.join(RES, f"mipmap-{density}")
        os.makedirs(folder, exist_ok=True)
        square = badge(size, size * 0.22, 0.92, crimson, bone)
        write_png(os.path.join(folder, "ic_launcher.png"), square, size, size)
        round_icon = badge(size, size / 2.0, 0.84, crimson, bone)
        write_png(os.path.join(folder, "ic_launcher_round.png"), round_icon, size, size)

    drawable = os.path.join(RES, "drawable")
    for name, lucide in sorted(ICON_MAP.items()):
        stroke_vector(LUCIDE[lucide], os.path.join(drawable, f"ic_{name}.xml"))
    stroke_vector(LUCIDE["star"], os.path.join(drawable, "ic_star_filled.xml"), filled=True)
    for level in range(1, 5):
        stroke_vector(resistance_icon(level), os.path.join(drawable, f"ic_resist_{level}.xml"))

    for name in ("scan", "create", "history"):
        shortcut(LUCIDE[ICON_MAP[name]], os.path.join(drawable, f"ic_shortcut_{name}.xml"))

    store = badge(512, 0, 1.0, crimson, bone)
    write_png(os.path.join(os.path.dirname(os.path.abspath(__file__)), "store_icon_512.png"), store, 512, 512)

    banner.write_svgs()

    print("piezas generadas en", RES)


if __name__ == "__main__":
    main()
