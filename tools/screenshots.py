"""Capturas reproducibles de la aplicacion.

    python tools/screenshots.py
"""
import os
import subprocess
import sys
import time

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
OUTPUT = os.path.join(ROOT, "docs", "capturas")
PACKAGE = "org.sarambi.signifer"

SCREENS = (
    ("lectura", f"{PACKAGE}.action.SCAN"),
    ("creacion", f"{PACKAGE}.action.CREATE"),
    ("historial", f"{PACKAGE}.action.HISTORY"),
)


def adb_path():
    sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    if not sdk:
        properties = os.path.join(ROOT, "local.properties")
        if os.path.isfile(properties):
            with open(properties, encoding="utf-8") as handle:
                for line in handle:
                    if line.startswith("sdk.dir="):
                        sdk = line.split("=", 1)[1].strip().replace("\\\\", "\\")
    if not sdk:
        raise SystemExit("no se encuentra el SDK de Android")
    for name in ("adb.exe", "adb"):
        candidate = os.path.join(sdk, "platform-tools", name)
        if os.path.isfile(candidate):
            return candidate
    raise SystemExit("no se encuentra adb")


ADB = adb_path()


def adb(*arguments, capture=True):
    return subprocess.run(
        [ADB, *arguments], capture_output=capture, text=True, errors="replace"
    )


def capture(name):
    raw = os.path.join(OUTPUT, f"{name}-completa.png")
    with open(raw, "wb") as handle:
        result = subprocess.run([ADB, "exec-out", "screencap", "-p"], stdout=handle)
    if result.returncode != 0:
        raise SystemExit(f"no se pudo capturar {name}")

    target = os.path.join(OUTPUT, f"{name}.png")
    subprocess.run(
        [sys.executable, os.path.join(ROOT, "tools", "shrink_png.py"), raw, target, "3"],
        check=True,
    )
    os.remove(raw)
    print(f"  {name}.png")


def main():
    os.makedirs(OUTPUT, exist_ok=True)
    if PACKAGE not in adb("shell", "pm", "list", "packages", PACKAGE).stdout:
        raise SystemExit(f"{PACKAGE} no esta instalado")

    print("capturando")
    for name, action in SCREENS:
        adb("shell", "am", "force-stop", PACKAGE)
        time.sleep(1)
        adb("shell", "am", "start", "-a", action, "-p", PACKAGE)
        time.sleep(5 if name == "lectura" else 3)
        capture(name)

    print(f"\nen {OUTPUT}")


if __name__ == "__main__":
    main()
