"""Genera todas las piezas de identidad visual a partir de la geometria de la marca.

    python tools/brand/generate.py
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import banner  # noqa: E402
from grid import BONE, CRIMSON  # noqa: E402
from icons import ICONS  # noqa: E402
from pngwriter import rgba, write_png  # noqa: E402

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
RES = os.path.join(ROOT, "app", "src", "main", "res")

# Densidades del lanzador.
DENSITIES = (("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192))

SUPERSAMPLE = 4

NEWLINE = chr(10)


def vector(path, viewport, scale, offset, color, name):
    lines = [
        '<?xml version="1.0" encoding="utf-8"?>',
        "<!-- Generado por tools/brand/generate.py. No editar a mano. -->",
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
        f'    android:width="{viewport}dp"',
        f'    android:height="{viewport}dp"',
        f'    android:viewportWidth="{viewport}"',
        f'    android:viewportHeight="{viewport}">',
        "    <path",
        f'        android:fillColor="{color}"',
        '        android:pathData="' + path + '" />',
        "</vector>",
        "",
    ]
    with open(name, "w", encoding="utf-8", newline="\n") as handle:
        handle.write("\n".join(lines))


def merge(matrix):
    """Los modulos encendidos de una matriz, fusionados en rectangulos."""
    size = len(matrix)
    runs = []
    for r, row in enumerate(matrix):
        c = 0
        while c < len(row):
            if row[c] == "#":
                start = c
                while c < len(row) and row[c] == "#":
                    c += 1
                runs.append([r, start, c - start, 1])
            else:
                c += 1
    merged = []
    for run in runs:
        for done in merged:
            if done[1] == run[1] and done[2] == run[2] and done[0] + done[3] == run[0]:
                done[3] += 1
                break
        else:
            merged.append(run)
    return [tuple(m) for m in merged], size


def icon_path(matrix, scale, offset):
    parts = []
    for row, col, width, height in merge(matrix)[0]:
        x = offset + col * scale
        y = offset + row * scale
        w = width * scale
        h = height * scale
        parts.append(f"M{fmt(x)},{fmt(y)}h{fmt(w)}v{fmt(h)}h{fmt(-w)}z")
    return "".join(parts)


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


def shortcut(matrix, name):
    """Un icono de atajo: circulo de tinta con los modulos en hueso encima."""
    scale = 28.0 / 12.0
    offset = (48 - 28) / 2.0
    lines = [
        '<?xml version="1.0" encoding="utf-8"?>',
        "<!-- Generado por tools/brand/generate.py. No editar a mano. -->",
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
        '    android:width="48dp"',
        '    android:height="48dp"',
        '    android:viewportWidth="48"',
        '    android:viewportHeight="48">',
        "    <path",
        f'        android:fillColor="{CRIMSON}"',
        '        android:pathData="M24,0A24,24 0 1,1 24,48A24,24 0 1,1 24,0z" />',
        "    <path",
        f'        android:fillColor="{BONE}"',
        '        android:pathData="' + icon_path(matrix, scale, offset) + '" />',
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
    for name, matrix in ICONS.items():
        if len(matrix) != 12 or any(len(row) != 12 for row in matrix):
            raise SystemExit(f"la matriz del icono «{name}» no es de 12 x 12")

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

    for name, matrix in sorted(ICONS.items()):
        vector(
            icon_path(matrix, 24.0 / 12.0, 0.0), 24, 1, 0, "#FF000000",
            os.path.join(RES, "drawable", f"ic_{name}.xml"),
        )

    for name in ("scan", "create", "history"):
        shortcut(
            ICONS[name],
            os.path.join(RES, "drawable", f"ic_shortcut_{name}.xml"),
        )

    store = badge(512, 0, 1.0, crimson, bone)
    write_png(os.path.join(os.path.dirname(os.path.abspath(__file__)), "store_icon_512.png"), store, 512, 512)

    banner.write_svgs()

    print("piezas generadas en", RES)


if __name__ == "__main__":
    main()
