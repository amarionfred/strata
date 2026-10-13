#!/usr/bin/env python3
"""Vendor selected text configs at the same immutable Omarchy commit as the launcher."""
import concurrent.futures
import hashlib
import json
import pathlib
import re
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parents[3]
ASSETS = ROOT / "system/companion/src/main/assets/omarchy"
REF = re.search(r'OMARCHY_REF = "([0-9a-f]{40})"', (ROOT / "app/src/main/kotlin/dev/omalauncher/theme/ThemeStore.kt").read_text()).group(1)
PATHS = [
    "LICENSE", "config/starship.toml", "config/tmux/tmux.conf", "config/btop/btop.conf",
    "config/lazygit/config.yml", "etc/fastfetch/config.jsonc", "default/themed/btop.theme.tpl",
    "default/bash/aliases", "default/bash/rc", "default/bash/init", "default/bash/shell",
    "default/bash/inputrc", "default/bash/envs",
]

def get(url):
    request = urllib.request.Request(url, headers={"User-Agent": "OmaSystemPreview-config-vendor"})
    with urllib.request.urlopen(request, timeout=30) as response:
        data = response.read(2_000_001)
        if len(data) > 2_000_000:
            raise ValueError("Unexpectedly large config response")
        return data

def main():
    tree = json.loads(get(f"https://api.github.com/repos/omacom/omarchy/git/trees/{REF}?recursive=1"))
    assert not tree.get("truncated"), "Incomplete source tree"
    blobs = {item["path"]: item for item in tree["tree"] if item["type"] == "blob"}
    paths = PATHS + [path for path in blobs if re.fullmatch(r"themes/[a-z0-9-]+/btop\.theme", path)]
    def fetch(path):
        data = get(f"https://raw.githubusercontent.com/omacom/omarchy/{REF}/{path}")
        actual = hashlib.sha1(b"blob " + str(len(data)).encode() + b"\0" + data).hexdigest()
        assert actual == blobs[path]["sha"], f"Git blob mismatch: {path}"
        data.decode("utf-8")
        if len(data) > 20000:
            raise ValueError(f"Unexpected config size: {path}")
        return path, data
    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
        files = dict(pool.map(fetch, paths))
    for path, data in files.items():
        destination = ASSETS / path
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_bytes(data)
    manifest = {"repository": "https://github.com/omacom/omarchy", "commit": REF,
                "files": {path: hashlib.sha256(data).hexdigest() for path, data in sorted(files.items())}}
    (ASSETS / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
    print(f"Verified {len(files)} files at {REF} ({sum(map(len, files.values()))} bytes)")

if __name__ == "__main__":
    main()
