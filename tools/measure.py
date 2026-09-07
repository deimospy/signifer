"""Mide el paquete de publicacion: peso por arquitectura y composicion.

    python tools/measure.py            lee lo ya compilado
    python tools/measure.py --build    compila release y despues mide
"""
import os
import subprocess
import sys
import zipfile

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
OUTPUT = os.path.join(ROOT, "app", "build", "outputs", "apk", "release")
CEILING = 3 * 1024 * 1024


def classify(name):
    if name.endswith(".so"):
        return "nativo"
    if name.endswith(".dex"):
        return "dex"
    if name == "resources.arsc":
        return "arsc"
    if name.startswith("res/"):
        return "recursos"
    return "otros"


def measure(path):
    parts = {}
    with zipfile.ZipFile(path) as apk:
        for info in apk.infolist():
            parts[classify(info.filename)] = (
                parts.get(classify(info.filename), 0) + info.file_size
            )
    return os.path.getsize(path), parts


def kb(value):
    return f"{value / 1024:.0f} KB"


def main():
    if "--build" in sys.argv:
        gradlew = os.path.join(ROOT, "gradlew.bat" if os.name == "nt" else "gradlew")
        subprocess.run([gradlew, ":app:assembleRelease", "-q"], cwd=ROOT, check=True)

    if not os.path.isdir(OUTPUT):
        raise SystemExit("no hay paquetes: ejecutar con --build")

    apks = sorted(f for f in os.listdir(OUTPUT) if f.endswith(".apk"))
    if not apks:
        raise SystemExit("no hay paquetes: ejecutar con --build")

    over = False
    print(f"{'arquitectura':<16}{'paquete':>12}{'techo':>10}")
    for name in apks:
        arch = name.replace("app-", "").replace("-release-unsigned.apk", "")
        total, _ = measure(os.path.join(OUTPUT, name))
        margin = CEILING - total
        if margin < 0:
            over = True
        print(f"{arch:<16}{kb(total):>12}{('+' if margin < 0 else '-') + kb(abs(margin)):>10}")

    reference = [f for f in apks if "arm64" in f]
    if reference:
        _, parts = measure(os.path.join(OUTPUT, reference[0]))
        print("\ncomposicion de arm64-v8a, sin comprimir")
        for key in ("nativo", "dex", "arsc", "recursos", "otros"):
            if key in parts:
                print(f"  {key:<10}{kb(parts[key]):>12}")

    if over:
        raise SystemExit("\nalgun paquete supera el techo de 3 MB")


if __name__ == "__main__":
    main()
