"""Comprueba que el dominio no importa nada de Android.

    python tools/check_purity.py
"""
import os
import re
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
SOURCE = os.path.join(ROOT, "app", "src", "main", "java", "org", "sarambi", "signifer")
ALL_SOURCES = os.path.join(ROOT, "app", "src")
PURE = ("security", "content")
FORBIDDEN = re.compile(r"^import\s+(android|androidx|com\.google\.android)\.", re.MULTILINE)

INVISIBLE = re.compile(
    "[\\x00-\\x08\\x0b\\x0c\\x0e-\\x1f\\x7f"
    "\\u200b-\\u200f\\u2028\\u2029\\u202a-\\u202e\\u2060-\\u2064\\ufeff]"
)


def main():
    offences = []
    for package in PURE:
        folder = os.path.join(SOURCE, package)
        if not os.path.isdir(folder):
            continue
        for name in sorted(os.listdir(folder)):
            if not name.endswith(".kt"):
                continue
            path = os.path.join(folder, name)
            with open(path, encoding="utf-8") as handle:
                for match in FORBIDDEN.finditer(handle.read()):
                    offences.append(f"{package}/{name}: {match.group(0).strip()}")

    hidden = []
    for folder, _, names in os.walk(ALL_SOURCES):
        for name in sorted(names):
            if not name.endswith((".kt", ".kts", ".xml")):
                continue
            path = os.path.join(folder, name)
            relative = os.path.relpath(path, ROOT).replace(os.sep, "/")
            with open(path, encoding="utf-8") as handle:
                for number, line in enumerate(handle, start=1):
                    for match in INVISIBLE.finditer(line):
                        hidden.append(f"{relative}:{number}: U+{ord(match.group(0)):04X}")

    if offences:
        print("el dominio importa Android:")
        for offence in offences:
            print("  " + offence)
    if hidden:
        print("caracteres invisibles literales en el codigo:")
        for place in hidden:
            print("  " + place)
    if offences or hidden:
        sys.exit(1)

    print("dominio puro: security/ y content/ no importan Android")
    print("sin caracteres invisibles literales en el codigo")


if __name__ == "__main__":
    main()
