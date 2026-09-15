"""Capturas del README, en espanol y con contenido de ejemplo.

    python tools/screenshots.py

Necesita un emulador o telefono conectado; instala la aplicacion con las pruebas de dispositivo.
"""
import os
import subprocess
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
OUTPUT = os.path.join(ROOT, "docs", "capturas")
SCREENS = ("lectura", "creacion", "historial", "resultado")


def adb_path():
    sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    if not sdk:
        properties = os.path.join(ROOT, "local.properties")
        if os.path.isfile(properties):
            with open(properties, encoding="utf-8") as handle:
                for line in handle:
                    if line.startswith("sdk.dir="):
                        sdk = line.split("=", 1)[1].strip().replace("\\\\", "\\").replace("\:", ":")
    if not sdk:
        raise SystemExit("no se encuentra el SDK de Android")
    for name in ("adb.exe", "adb"):
        candidate = os.path.join(sdk, "platform-tools", name)
        if os.path.isfile(candidate):
            return candidate
    raise SystemExit("no se encuentra adb")


def main():
    adb = adb_path()
    gradle = os.path.join(ROOT, "gradlew.bat" if os.name == "nt" else "gradlew")
    subprocess.run(
        [
            gradle,
            ":app:connectedDebugAndroidTest",
            "-Pandroid.testInstrumentationRunnerArguments.class=org.sarambi.signifer.ui.ReadmeScreenshots",
            "-Pandroid.testInstrumentationRunnerArguments.capturas=1",
        ],
        cwd=ROOT,
        check=True,
    )
    os.makedirs(OUTPUT, exist_ok=True)
    for name in SCREENS:
        raw = os.path.join(OUTPUT, f"{name}-completa.png")
        with open(raw, "wb") as handle:
            subprocess.run([adb, "exec-out", "cat", f"/sdcard/readme_{name}.png"], stdout=handle, check=True)
        target = os.path.join(OUTPUT, f"{name}.png")
        subprocess.run([sys.executable, os.path.join(ROOT, "tools", "shrink_png.py"), raw, target, "3"], check=True)
        os.remove(raw)
        print(f"  {name}.png")


if __name__ == "__main__":
    main()
