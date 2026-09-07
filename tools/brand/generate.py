"""Genera todas las piezas de identidad visual a partir de la grilla.

    python tools/brand/generate.py
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from grid import BONE, INK, SIZE, check_symmetry, rectangles  # noqa: E402
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


def path_data(scale, offset):
    parts = []
    for row, col, width, height in rectangles():
        x = offset + col * scale
        y = offset + row * scale
        w = width * scale
        h = height * scale
        parts.append(f"M{fmt(x)},{fmt(y)}h{fmt(w)}v{fmt(h)}h{fmt(-w)}z")
    return "".join(parts)


def fmt(value):
    text = f"{value:.2f}".rstrip("0").rstrip(".")
    return text if text else "0"


def rounded_square(size, radius, ink, bone, inset_scale):
    """Icono cuadrado de esquinas redondeadas, con la S centrada."""
    big = size * SUPERSAMPLE
    r = radius * SUPERSAMPLE
    module = big * inset_scale / SIZE
    origin = (big - module * SIZE) / 2.0
    cells = {(row, col, w, h) for row, col, w, h in rectangles()}

    coarse = []
    for y in range(big):
        line = bytearray(big)
        inside_y = y
        for row, col, w, h in cells:
            y0 = origin + row * module
            y1 = y0 + h * module
            if y0 <= inside_y < y1:
                x0 = int(origin + col * module)
                x1 = int(origin + (col + w) * module)
                for x in range(max(0, x0), min(big, x1)):
                    line[x] = 1
        coarse.append(line)

    rows = []
    for y in range(size):
        out = []
        for x in range(size):
            r_sum = g_sum = b_sum = a_sum = 0
            for dy in range(SUPERSAMPLE):
                for dx in range(SUPERSAMPLE):
                    px, py = x * SUPERSAMPLE + dx, y * SUPERSAMPLE + dy
                    if not in_rounded(px, py, big, r):
                        color = (0, 0, 0, 0)
                    elif coarse[py][px]:
                        color = ink
                    else:
                        color = bone
                    r_sum += color[0] * color[3]
                    g_sum += color[1] * color[3]
                    b_sum += color[2] * color[3]
                    a_sum += color[3]
            if a_sum == 0:
                out.append((0, 0, 0, 0))
            else:
                n = SUPERSAMPLE * SUPERSAMPLE
                out.append((r_sum // a_sum, g_sum // a_sum, b_sum // a_sum, a_sum // n))
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


def circle(size, ink, bone, inset_scale):
    return rounded_square(size, size / 2.0, ink, bone, inset_scale)


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
        f'        android:fillColor="{INK}"',
        '        android:pathData="M24,0A24,24 0 1,1 24,48A24,24 0 1,1 24,0z" />',
        "    <path",
        f'        android:fillColor="{BONE}"',
        '        android:pathData="' + icon_path(matrix, scale, offset) + '" />',
        "</vector>",
        "",
    ]
    with open(name, "w", encoding="utf-8", newline=NEWLINE) as handle:
        handle.write(NEWLINE.join(lines))


def main():
    if not check_symmetry():
        raise SystemExit("la grilla perdio la simetria de rotacion")

    for name, matrix in ICONS.items():
        if len(matrix) != 12 or any(len(row) != 12 for row in matrix):
            raise SystemExit(f"la matriz del icono «{name}» no es de 12 x 12")

    ink = rgba(INK)
    bone = rgba(BONE)

    os.makedirs(os.path.join(RES, "drawable"), exist_ok=True)

    vector(
        path_data(1, 0), SIZE, 1, 0, "?attr/colorPrimary",
        os.path.join(RES, "drawable", "ic_signum.xml"),
    )

    scale = 66.0 / SIZE
    offset = (108 - 66) / 2.0
    vector(
        path_data(scale, offset), 108, scale, offset, INK,
        os.path.join(RES, "drawable", "ic_launcher_foreground.xml"),
    )
    # Version monocroma: Android 13 la tine con el color del sistema.
    vector(
        path_data(scale, offset), 108, scale, offset, "#FFFFFFFF",
        os.path.join(RES, "drawable", "ic_launcher_monochrome.xml"),
    )

    for density, size in DENSITIES:
        folder = os.path.join(RES, f"mipmap-{density}")
        os.makedirs(folder, exist_ok=True)
        square = rounded_square(size, size * 0.22, ink, bone, 0.72)
        write_png(os.path.join(folder, "ic_launcher.png"), square, size, size)
        round_icon = circle(size, ink, bone, 0.66)
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

    store = rounded_square(512, 0, ink, bone, 0.72)
    write_png(os.path.join(os.path.dirname(os.path.abspath(__file__)), "store_icon_512.png"), store, 512, 512)

    print("piezas generadas en", RES)


if __name__ == "__main__":
    main()
