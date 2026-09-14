"""Copia al proyecto la geometria de los iconos Lucide que usa la aplicacion.

    python tools/brand/lucide_import.py <carpeta de lucide-react>
"""
import json
import os
import re
import shutil
import sys

HERE = os.path.dirname(os.path.abspath(__file__))

USED = [
    "scan-line", "qr-code", "history", "flashlight", "image", "settings", "copy",
    "share-2", "external-link", "folder-open", "star", "search", "trash-2", "check",
    "triangle-alert", "ban", "download", "barcode", "x", "wifi", "user-plus", "mail",
    "phone", "message-square", "map-pin", "calendar-plus", "user", "scale",
]

NODE = re.compile(r'\[\s*"(\w+)",\s*\{(.*?)\}\s*\]', re.S)
ATTRIBUTE = re.compile(r'(\w+):\s*"([^"]*)"')


def read_icon(folder, name):
    path = os.path.join(folder, "dist", "esm", "icons", f"{name}.js")
    with open(path, encoding="utf-8") as handle:
        source = handle.read()
    block = source[source.index("__iconNode = [") : source.index("];")]
    nodes = []
    for tag, attributes in NODE.findall(block):
        values = {key: value for key, value in ATTRIBUTE.findall(attributes) if key != "key"}
        nodes.append([tag, values])
    return nodes


def main():
    if len(sys.argv) != 2:
        raise SystemExit(__doc__)
    folder = sys.argv[1]
    with open(os.path.join(folder, "package.json"), encoding="utf-8") as handle:
        version = json.load(handle)["version"]

    icons = {name: read_icon(folder, name) for name in USED}
    lines = [
        f'"""Geometria de los iconos Lucide {version} que usa la aplicacion.',
        "",
        "Generado por tools/brand/lucide_import.py. No editar a mano.",
        "Lucide: ISC, ver tools/brand/lucide/LICENSE.",
        '"""',
        "",
        f'VERSION = "{version}"',
        "",
        "ICONS = {",
    ]
    for name, nodes in icons.items():
        lines.append(f'    "{name}": [')
        for tag, values in nodes:
            lines.append(f"        ({tag!r}, {values!r}),")
        lines.append("    ],")
    lines.append("}")
    with open(os.path.join(HERE, "lucide_icons.py"), "w", encoding="utf-8", newline="\n") as handle:
        handle.write("\n".join(lines) + "\n")

    os.makedirs(os.path.join(HERE, "lucide"), exist_ok=True)
    shutil.copyfile(os.path.join(folder, "LICENSE"), os.path.join(HERE, "lucide", "LICENSE"))
    print(f"{len(icons)} iconos de Lucide {version} copiados")


if __name__ == "__main__":
    main()
