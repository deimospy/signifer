"""Audita el paquete compilado, no el codigo fuente.

    python tools/audit.py [ruta-al-apk]
"""
import os
import re
import subprocess
import sys
import zipfile

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
OUTPUT = os.path.join(ROOT, "app", "build", "outputs", "apk", "release")

FORBIDDEN_PERMISSIONS = (
    "android.permission.INTERNET",
    "android.permission.ACCESS_NETWORK_STATE",
    "android.permission.ACCESS_WIFI_STATE",
    "android.permission.CHANGE_WIFI_STATE",
    "android.permission.READ_EXTERNAL_STORAGE",
    "android.permission.WRITE_EXTERNAL_STORAGE",
    "android.permission.READ_MEDIA_IMAGES",
    "android.permission.ACCESS_FINE_LOCATION",
    "android.permission.ACCESS_COARSE_LOCATION",
    "android.permission.READ_CONTACTS",
    "android.permission.WRITE_CONTACTS",
    "android.permission.READ_PHONE_STATE",
    "android.permission.CALL_PHONE",
    "android.permission.SEND_SMS",
    "com.google.android.gms.permission.AD_ID",
)

FORBIDDEN_MARKERS = (
    ("com/google/firebase", "Firebase"),
    ("com/google/android/gms/ads", "Google Mobile Ads"),
    ("com/google/android/gms/analytics", "Google Analytics"),
    ("com/facebook", "SDK de Facebook"),
    ("com/appsflyer", "AppsFlyer"),
    ("com/adjust/sdk", "Adjust"),
    ("io/sentry", "Sentry"),
    ("com/crashlytics", "Crashlytics"),
    ("com/amplitude", "Amplitude"),
    ("com/mixpanel", "Mixpanel"),
    ("com/onesignal", "OneSignal"),
    ("com/android/billingclient", "Facturacion de Play"),
)


def find_aapt2():
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

    tools = os.path.join(sdk, "build-tools")
    versions = sorted(os.listdir(tools), reverse=True)
    for version in versions:
        for name in ("aapt2.exe", "aapt2"):
            candidate = os.path.join(tools, version, name)
            if os.path.isfile(candidate):
                return candidate
    raise SystemExit("no se encuentra aapt2")


def manifest_of(apk):
    result = subprocess.run(
        [find_aapt2(), "dump", "xmltree", apk, "--file", "AndroidManifest.xml"],
        capture_output=True,
        text=True,
        errors="replace",
    )
    return result.stdout


def main():
    if len(sys.argv) > 1:
        apk = sys.argv[1]
    else:
        candidates = [f for f in os.listdir(OUTPUT) if "arm64" in f] if os.path.isdir(OUTPUT) else []
        if not candidates:
            raise SystemExit("no hay paquete: compilar con :app:assembleRelease")
        apk = os.path.join(OUTPUT, candidates[0])

    print(f"paquete\t{os.path.basename(apk)}")
    print(f"peso\t{os.path.getsize(apk) / 1024:.0f} KB\n")

    manifest = manifest_of(apk)
    declared = sorted(set(re.findall(r'android:name.*?="(android\.permission\.[A-Z_]+)"', manifest)))
    print("permisos declarados")
    for permission in declared:
        print("  " + permission)
    if not declared:
        print("  ninguno")

    problems = []

    for permission in FORBIDDEN_PERMISSIONS:
        if permission in manifest:
            problems.append(f"declara {permission}")

    with zipfile.ZipFile(apk) as package:
        names = package.namelist()
        dex = b"".join(package.read(n) for n in names if n.endswith(".dex"))
        libraries = [n for n in names if n.endswith(".so")]

        print("\nbibliotecas nativas")
        for library in libraries:
            print(f"  {library}\t{package.getinfo(library).file_size / 1024:.0f} KB")

        for marker, label in FORBIDDEN_MARKERS:
            if marker.encode() in dex:
                problems.append(f"contiene {label}")

    print("\ncomprobaciones")
    checks = [
        ("sin permiso de red", "android.permission.INTERNET" not in manifest),
        ("sin permisos de almacenamiento", "EXTERNAL_STORAGE" not in manifest),
        ("sin permiso de ubicacion", "LOCATION" not in manifest),
        ("sin identificador de publicidad", "AD_ID" not in manifest),
        ("respaldo del sistema desactivado", "allowBackup" in manifest),
        ("sin SDK de analitica ni publicidad", not any(p.startswith("contiene") for p in problems)),
    ]
    for label, passed in checks:
        print(f"  [{'ok' if passed else '!!'}] {label}")

    if problems:
        print("\nhallazgos")
        for problem in problems:
            print("  " + problem)
        sys.exit(1)

    print("\nel binario cumple lo que promete el proyecto")


if __name__ == "__main__":
    main()
