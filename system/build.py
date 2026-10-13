#!/usr/bin/env python3
"""Build a native, architecture-specific Magisk runtime preview. No device writes."""
import argparse
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import subprocess
import tarfile
import zipfile

SYSTEM = Path(__file__).resolve().parent
REPO = SYSTEM.parent


def archive_inventory(archive):
    """Reject extraction paths that could escape a fresh, root-owned staging dir."""
    with tarfile.open(archive) as stream:
        members = stream.getmembers()
    names = {}
    for entry in members:
        name = PurePosixPath(entry.name)
        if name.is_absolute() or ".." in name.parts:
            raise ValueError(f"Unsafe archive path: {entry.name}")
        if str(name) == ".":
            if not entry.isdir():
                raise ValueError("Archive root must be a directory")
            continue
        if name in names:
            raise ValueError(f"Duplicate archive path: {entry.name}")
        if not (entry.isfile() or entry.isdir() or entry.issym() or entry.islnk()):
            raise ValueError(f"Unsupported archive entry: {entry.name}")
        names[name] = entry
    for name, entry in names.items():
        for parent in name.parents:
            if parent in names and not names[parent].isdir():
                raise ValueError(f"Archive writes through a link/non-directory: {name}")
        if entry.islnk():
            target = PurePosixPath(entry.linkname)
            if target.is_absolute() or ".." in target.parts or target not in names or not names[target].isfile():
                raise ValueError(f"Unsafe archive hard link: {name}")
    return sum(entry.size for entry in members if entry.isfile())


def write_entry(output, name, data, executable=False):
    info = zipfile.ZipInfo(name, date_time=(2026, 1, 1, 0, 0, 0))
    info.create_system = 3
    info.external_attr = (0o100755 if executable else 0o100644) << 16
    info.compress_type = zipfile.ZIP_DEFLATED
    output.writestr(info, data)


def build(architecture, zig):
    lock = json.loads((SYSTEM / "images.lock.json").read_text())
    image = lock["images"][architecture]
    cache = REPO / ".local/native"
    cache.mkdir(parents=True, exist_ok=True)
    filename = f"alpine-minirootfs-{lock['release']}-{image['alpine_arch']}.tar.gz"
    archive = cache / filename
    url = f"https://dl-cdn.alpinelinux.org/alpine/v3.24/releases/{image['alpine_arch']}/{filename}"
    if not archive.is_file():
        raise SystemExit(f"Download {url}\nto {archive}; its checksum will be verified before packaging.")
    digest = hashlib.file_digest(archive.open("rb"), "sha256").hexdigest()
    if digest != image["sha256"]:
        raise SystemExit(f"Rootfs checksum mismatch: {archive}")
    unpacked = archive_inventory(archive)
    binary = cache / f"oma-session-{architecture}"
    compiler = [str(Path(zig).resolve()), "cc", "-target", image["target"], "-static",
                "-Os", "-s", "-Wall", "-Wextra", "-Werror"]
    if image.get("cpu"):
        compiler += [f"-mcpu={image['cpu']}"]
    compiler += [str(SYSTEM / "native/session.c"), "-o", str(binary)]
    subprocess.run(compiler, check=True,
                   env={**os.environ, "ZIG_GLOBAL_CACHE_DIR": str(REPO / ".local/toolchains/zig-cache")})
    properties = dict(line.split("=", 1) for line in (SYSTEM / "module/module.prop").read_text().splitlines() if "=" in line)
    destination = cache / "dist" / f"strata-runtime-{properties['version']}-{architecture}.zip"
    destination.parent.mkdir(parents=True, exist_ok=True)
    temporary = destination.with_suffix(".zip.tmp")
    with zipfile.ZipFile(temporary, "w") as output:
        for source in sorted((SYSTEM / "module").rglob("*")):
            if source.is_file():
                name = source.relative_to(SYSTEM / "module").as_posix()
                write_entry(output, name, source.read_bytes(), source.suffix == ".sh" or name in ("omactl", "guest/oma-services"))
        write_entry(output, "bin/oma-session", binary.read_bytes(), True)
        write_entry(output, "payload/rootfs.tar.gz", archive.read_bytes())
        write_entry(output, "payload/rootfs.sha256", f"{digest}  rootfs.tar.gz\n")
        write_entry(output, "payload/unpacked-bytes", f"{unpacked}\n")
        write_entry(output, "payload/arch", architecture + "\n")
        write_entry(output, "native/session.c", (SYSTEM / "native/session.c").read_bytes())
        write_entry(output, "LICENSE", (REPO / "LICENSE").read_bytes())
        write_entry(output, "README.md", (SYSTEM / "README.md").read_bytes())
        write_entry(output, "IMAGE_SOURCE.txt", f"Unmodified Alpine base image:\n{url}\nSHA256 {digest}\n"
                    "Image packages retain their upstream licenses. Package metadata is in /lib/apk/db/installed.\n"
                    "Alpine package build recipes: https://gitlab.alpinelinux.org/alpine/aports/-/tree/3.24-stable\n"
                    "Source archives: https://distfiles.alpinelinux.org/distfiles/v3.24/\n"
                    "Native helper libc: musl (MIT), https://musl.libc.org/\n")
    temporary.replace(destination)
    with destination.open("rb") as file:
        checksum = hashlib.file_digest(file, "sha256").hexdigest()
    destination.with_suffix(".zip.sha256").write_text(f"{checksum}  {destination.name}\n")
    print(destination)
    print(f"sha256={checksum}; size={destination.stat().st_size}; rootfs_bytes={unpacked}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--arch", choices=("arm64", "arm", "x64"), required=True)
    parser.add_argument("--zig", required=True, help="Path to the Zig compiler (tested with 0.17.0)")
    options = parser.parse_args()
    build(options.arch, options.zig)
