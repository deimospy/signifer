"""El estandarte: la marca de Signifer.

    python tools/brand/banner.py
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from grid import BONE, CRIMSON  # noqa: E402

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
OUT = os.path.join(ROOT, "docs", "marca")

VIEW = 144

MODULE = 8
PITCH = 10
S_LEFT = 48
S_TOP = 45

MODULES = [
    (0, 1), (0, 2), (0, 3),
    (1, 3), (1, 4),
    (3, 1), (3, 2), (3, 3),
    (5, 0), (5, 1),
    (6, 1), (6, 2), (6, 3),
]

EYES = [(1, 0), (4, 3)]
EYE = PITCH + MODULE
EYE_RING = 2.5


def square(x, y, width, height):
    return [(x, y), (x + width, y), (x + width, y + height), (x, y + height)]


def polygons(staff_bottom=VIEW):
    """Los contornos, en el orden en que se anidan."""
    outline = [
        (67, 11), (77, 11), (77, 21), (73.5, 21), (73.5, 29), (113, 29), (113, 26),
        (122, 26), (122, 35), (113, 35), (113, 32), (108, 32), (108, 120),
        (73.5, 120), (73.5, staff_bottom), (70.5, staff_bottom), (70.5, 120),
        (36, 120), (36, 32), (31, 32), (31, 35), (22, 35), (22, 26), (31, 26),
        (31, 29), (70.5, 29), (70.5, 21), (67, 21),
    ]
    shapes = [
        outline,
        square(39, 33, 31.5, 4),
        square(73.5, 33, 31.5, 4),
    ]
    for row, column in MODULES:
        shapes.append(square(S_LEFT + column * PITCH, S_TOP + row * PITCH, MODULE, MODULE))
    for row, column in EYES:
        x = S_LEFT + column * PITCH
        y = S_TOP + row * PITCH
        # Con par-impar, cada cuadrado anidado alterna: anillo, hueco, centro.
        for step in range(3):
            inset = step * EYE_RING
            shapes.append(square(x + inset, y + inset, EYE - 2 * inset, EYE - 2 * inset))
    return shapes


def fmt(value):
    text = f"{value:.3f}".rstrip("0").rstrip(".")
    return text if text and text != "-0" else "0"


def path_data(staff_bottom=VIEW, separator=" "):
    """El trazado en unidades de la grilla, con ordenes horizontales y verticales."""
    parts = []
    for shape in polygons(staff_bottom):
        (x, y), rest = shape[0], shape[1:]
        command = [f"M{fmt(x)}{separator}{fmt(y)}"]
        for nx, ny in rest:
            command.append(f"H{fmt(nx)}" if ny == y else f"V{fmt(ny)}")
            x, y = nx, ny
        parts.append("".join(command) + "Z")
    return "".join(parts)


def coverage(size, scale, dx, dy, staff_bottom=VIEW, supersample=4):
    """Cobertura de la marca en cada pixel, de 0 a 1, con regla par-impar."""
    edges = []
    for shape in polygons(staff_bottom):
        for (x0, y0), (x1, y1) in zip(shape, shape[1:] + shape[:1]):
            if x0 == x1 and y0 != y1:
                top, bottom = sorted((y0 * scale + dy, y1 * scale + dy))
                edges.append((x0 * scale + dx, top, bottom))

    samples = size * supersample
    grid = [[0] * size for _ in range(size)]
    for sy in range(samples):
        y = (sy + 0.5) / supersample
        crossings = sorted(x for x, top, bottom in edges if top <= y < bottom)
        for start, end in zip(crossings[0::2], crossings[1::2]):
            first = max(0, int(start * supersample + 0.5))
            last = min(samples, int(end * supersample + 0.5))
            row = grid[sy // supersample]
            for sx in range(first, last):
                row[sx // supersample] += 1
    total = supersample * supersample
    return [[value / total for value in row] for row in grid]


def svg(mark_color, background=None, title="Signifer"):
    lines = [
        f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {VIEW} {VIEW}" width="{VIEW * 4}" height="{VIEW * 4}">',
        f"  <title>{title}</title>",
    ]
    if background:
        lines.append(f'  <rect width="{VIEW}" height="{VIEW}" fill="{background}"/>')
    lines.append(f'  <path fill="{mark_color}" fill-rule="evenodd" d="{path_data()}"/>')
    lines.append("</svg>")
    return "\n".join(lines) + "\n"


def write_svgs():
    os.makedirs(OUT, exist_ok=True)
    pieces = {
        "signifer-icono.svg": svg(BONE, CRIMSON),
        "signifer-marca.svg": svg(CRIMSON),
        "signifer-marca-clara.svg": svg(BONE),
    }
    for name, content in pieces.items():
        with open(os.path.join(OUT, name), "w", encoding="utf-8", newline="\n") as handle:
            handle.write(content)
        print(f"escrito docs/marca/{name}")


if __name__ == "__main__":
    write_svgs()
