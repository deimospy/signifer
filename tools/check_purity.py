"""Comprueba que el dominio no importa nada de Android.

    python tools/check_purity.py
"""
import os
import re
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
SOURCE = os.path.join(ROOT, "app", "src", "main", "java", "org", "sarambi", "signifer")
PURE = ("security", "content")
FORBIDDEN = re.compile(r"^import\s+(android|androidx|com\.google\.android)\.", re.MULTILINE)


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

    if offences:
        print("el dominio importa Android:")
        for offence in offences:
            print("  " + offence)
        sys.exit(1)

    print("dominio puro: security/ y content/ no importan Android")


if __name__ == "__main__":
    main()
