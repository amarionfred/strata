"""Host browser QA fixture; never starts a VM or contacts a phone."""
import json
import os
from pathlib import Path
import secrets
import sys
import tempfile
from test_workspace import runtime

if os.environ.get("STRATA_XVNC_DIRECTORY"):
    os.environ["PATH"] = os.environ["STRATA_XVNC_DIRECTORY"] + os.pathsep + os.environ["PATH"]

with tempfile.TemporaryDirectory(prefix="strata-browser-") as directory:
    home = Path(directory)
    ws = runtime.Workspace(home, home / ".state")
    token = secrets.token_hex(32)
    with runtime.Server(("127.0.0.1", 0), token, ws) as server:
        print(json.dumps({"port": server.server_address[1], "token": token, "home": directory}), flush=True)
        try:
            server.serve_forever()
        except KeyboardInterrupt:
            pass
        finally:
            ws.tmux("kill-server", check=False)
