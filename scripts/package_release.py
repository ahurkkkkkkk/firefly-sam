"""Build an optional source-only archive without local supplied model assets."""
from pathlib import Path
import hashlib
import zipfile

ROOT = Path(__file__).resolve().parents[1]
VERSION = "2.0.0"
DIST = ROOT / "dist"
OUTPUT = DIST / f"Firefly-SAM-{VERSION}-source.zip"
ROOT_FILES = (
    "build.gradle", "settings.gradle", "gradle.properties", "gradlew", "gradlew.bat",
    ".gitignore", "README.md", "CREDITS.md", "LICENSE", "MODLOG.md",
)


def excluded(path: Path) -> bool:
    relative = path.relative_to(ROOT).as_posix()
    return (
        path.suffix.lower() in {".fmesh", ".pmx", ".obj", ".mtl", ".blend"}
        or "/textures/imported/" in f"/{relative}/"
        or "/assets/firefly_sam/geo/" in f"/{relative}/"
        or "/assets/firefly_sam/walk_data/" in f"/{relative}/"
        or relative.endswith("/animations/sam_rig.animation.json")
    )


def main() -> None:
    DIST.mkdir(exist_ok=True)
    entries = []
    for directory in ("src", "scripts", "gradle"):
        base = ROOT / directory
        if not base.exists():
            continue
        entries.extend(path for path in base.rglob("*") if path.is_file() and "__pycache__" not in path.parts and not excluded(path))
    entries.extend(ROOT / name for name in ROOT_FILES if (ROOT / name).is_file())

    with zipfile.ZipFile(OUTPUT, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for path in sorted(set(entries)):
            archive.write(path, Path("firefly-sam") / path.relative_to(ROOT))

    with zipfile.ZipFile(OUTPUT) as archive:
        forbidden = (".fmesh", ".pmx", ".obj", ".mtl", ".blend", "assets/firefly_sam/geo/",
                     "assets/firefly_sam/walk_data/", "textures/imported/", "sam_rig.animation.json")
        leaked = [name for name in archive.namelist() if any(part in name for part in forbidden)]
        if leaked:
            raise RuntimeError(f"Source archive contains local model-derived assets: {leaked}")

    digest = hashlib.sha256(OUTPUT.read_bytes()).hexdigest()
    (DIST / f"SHA256SUMS-{VERSION}-source.txt").write_text(f"{digest}  {OUTPUT.name}\n", encoding="utf-8")
    print(f"Created code-only source archive: {OUTPUT} ({OUTPUT.stat().st_size} bytes)")


if __name__ == "__main__":
    main()
