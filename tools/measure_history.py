"""Mide el peso del paquete en cada commit del repositorio.

    python tools/measure_history.py
"""
import os
import shutil
import subprocess
import sys
import tempfile
import zipfile

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
# Las copias de cada commit se compilan fuera del repositorio.
WORKTREES = os.path.join(tempfile.gettempdir(), "signifer-measure")


def run(command, cwd, check=True):
    return subprocess.run(command, cwd=cwd, check=check, capture_output=True, text=True)


def commits():
    output = run(["git", "log", "--reverse", "--format=%h\t%s"], ROOT).stdout
    for line in output.strip().splitlines():
        short, subject = line.split("\t", 1)
        yield short, subject


def measure(folder):
    output = os.path.join(folder, "app", "build", "outputs", "apk", "release")
    if not os.path.isdir(output):
        return {}
    sizes = {}
    for name in os.listdir(output):
        if not name.endswith(".apk"):
            continue
        architecture = name.replace("app-", "").replace("-release-unsigned.apk", "")
        sizes[architecture] = os.path.getsize(os.path.join(output, name))
    return sizes


def composition(folder):
    output = os.path.join(folder, "app", "build", "outputs", "apk", "release")
    candidates = [f for f in os.listdir(output) if "arm64" in f] if os.path.isdir(output) else []
    if not candidates:
        return {}
    parts = {}
    with zipfile.ZipFile(os.path.join(output, candidates[0])) as apk:
        for info in apk.infolist():
            name = info.filename
            if name.endswith(".so"):
                key = "nativo"
            elif name.endswith(".dex"):
                key = "dex"
            elif name == "resources.arsc":
                key = "arsc"
            elif name.startswith("res/"):
                key = "recursos"
            else:
                key = "otros"
            parts[key] = parts.get(key, 0) + info.file_size
    return parts


def main():
    run(["git", "worktree", "prune"], ROOT, check=False)
    shutil.rmtree(WORKTREES, ignore_errors=True)
    os.makedirs(WORKTREES, exist_ok=True)

    rows = []
    for short, subject in commits():
        worktree = os.path.join(WORKTREES, short)
        run(["git", "worktree", "add", "--detach", worktree, short], ROOT)
        try:
            shutil.copy(os.path.join(ROOT, "local.properties"), worktree)
            gradlew = os.path.join(worktree, "gradlew.bat" if os.name == "nt" else "gradlew")
            result = subprocess.run(
            [
                    gradlew,
                    ":app:assembleRelease",
                    "-q",
                    "--no-daemon",
                    "-Dkotlin.compiler.execution.strategy=in-process",
                ],
                cwd=worktree,
                capture_output=True,
                text=True,
            )
            if result.returncode != 0:
                print(f"{short}  no compila: {subject}", file=sys.stderr)
                continue
            rows.append((short, subject, measure(worktree), composition(worktree)))
            sizes = rows[-1][2]
            arm64 = sizes.get("arm64-v8a", 0) / 1024
            print(f"{short}  {arm64:7.0f} KB  {subject.splitlines()[0]}")
        finally:
            run(["git", "worktree", "remove", "--force", worktree], ROOT, check=False)

    print()
    print("| Estado | arm64-v8a | armeabi-v7a | x86 | x86_64 |")
    print("|---|---|---|---|---|")
    for _, subject, sizes, _ in rows:
        cells = " | ".join(
            f"{sizes.get(a, 0) / 1024:.0f}"
            for a in ("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
        )
        print(f"| {subject.splitlines()[0]} | {cells} |")

    print()
    print("| Estado | nativo | dex | arsc | recursos |")
    print("|---|---|---|---|---|")
    for _, subject, _, parts in rows:
        cells = " | ".join(
            f"{parts.get(k, 0) / 1024:.0f}" for k in ("nativo", "dex", "arsc", "recursos")
        )
        print(f"| {subject.splitlines()[0]} | {cells} |")


if __name__ == "__main__":
    main()
