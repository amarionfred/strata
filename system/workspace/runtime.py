#!/usr/bin/env python3
"""Strata's native Termux workspace. No guest filesystem or privilege emulation.

Only a paired APK can use the loopback protocol. tmux owns the terminals and
service processes, so disconnecting the frontend or restarting this broker does
not destroy them. Android may still kill Termux and its processes.
"""
import base64
import fcntl
import hashlib
import hmac
import json
import os
from pathlib import Path
import re
import secrets
import select
import shlex
import shutil
import signal
import socket
import socketserver
import struct
import subprocess
import sys
import tempfile
import termios
import threading
import time

PROTOCOL = 1
MAX_FRAME = 512 * 1024
MAX_FILE = 256 * 1024
NAME = re.compile(r"[a-zA-Z0-9][a-zA-Z0-9_-]{0,39}\Z")


def send(stream, value):
    stream.write(json.dumps(value, ensure_ascii=False).encode() + b"\n")
    stream.flush()


def receive(stream):
    line = stream.readline(MAX_FRAME + 1)
    if not line or len(line) > MAX_FRAME or not line.endswith(b"\n"):
        raise ValueError("Connection closed or frame too large")
    value = json.loads(line)
    if not isinstance(value, dict):
        raise ValueError("Expected an object")
    return value


def proof(token, value):
    return hmac.new(token.encode(), value.encode(), hashlib.sha256).hexdigest()


def named(value):
    if not isinstance(value, str) or not NAME.fullmatch(value):
        raise ValueError("Use 1–40 letters, numbers, underscores or hyphens")
    return value


def atomic(path, data, mode=0o600):
    fd, temporary = tempfile.mkstemp(prefix=".strata-", dir=path.parent)
    try:
        with os.fdopen(fd, "wb") as output:
            os.fchmod(output.fileno(), mode)
            output.write(data)
            output.flush()
            os.fsync(output.fileno())
        os.replace(temporary, path)
    finally:
        if os.path.exists(temporary):
            os.unlink(temporary)


class Workspace:
    def __init__(self, home, state):
        self.home = Path(home).resolve()
        self.state = Path(state).resolve()
        self.state.mkdir(parents=True, exist_ok=True, mode=0o700)
        self.services = self.state / "services"
        self.services.mkdir(exist_ok=True, mode=0o700)
        self.socket = str(self.state / "tmux.sock")
        self.lock = threading.RLock()
        # A dedicated tmux server never changes the user's own tmux configuration.
        self.tmux_config = self.state / "tmux.conf"
        atomic(self.tmux_config, b"set -g status off\nset -g history-limit 5000\nset -g mouse on\nset -g default-terminal screen-256color\nset -g set-clipboard off\nset -g exit-empty on\nset -g remain-on-exit on\n")
        self.rc = self.state / "bashrc"
        atomic(self.rc, b'[[ ! -r "$HOME/.bashrc" ]] || source "$HOME/.bashrc"\n'
               b'[[ ! -r "$HOME/.config/oma/omarchy.bash" ]] || source "$HOME/.config/oma/omarchy.bash"\n'
               b'[[ ! -r "$HOME/.config/oma/session.sh" ]] || source "$HOME/.config/oma/session.sh"\n')

    def tmux(self, *args, check=True):
        result = subprocess.run(["tmux", "-S", self.socket, "-f", str(self.tmux_config), *args],
                                capture_output=True, text=True, timeout=10,
                                env={**os.environ, "HOME": str(self.home)})
        if check and result.returncode:
            raise RuntimeError(result.stderr.strip() or "tmux command failed")
        return result

    def sessions(self, prefix="term-"):
        result = self.tmux("list-panes", "-a", "-F", "#{session_name}\t#{pane_dead}\t#{pane_dead_status}\t#{pane_current_command}", check=False)
        if result.returncode:
            return []
        items = []
        for line in result.stdout.splitlines():
            fields = line.split("\t")
            if len(fields) == 4 and fields[0].startswith(prefix):
                items.append({"name": fields[0][len(prefix):], "running": fields[1] != "1",
                              "exit": int(fields[2]) if fields[2].isdigit() else None, "command": fields[3]})
        return items

    def create_terminal(self, name, command=None):
        named(name)
        with self.lock:
            if any(item["name"] == name for item in self.sessions()):
                raise ValueError("Session already exists")
            if len(self.sessions()) >= 12:
                raise ValueError("Close a session first (12 maximum)")
            args = ["bash", "--rcfile", str(self.rc), "-i"]
            if command:
                if not isinstance(command, str) or len(command) > 16000:
                    raise ValueError("Command too large")
                # The explicit command runs in its own terminal, with real prompts/exit.
                args = ["bash", "--rcfile", str(self.rc), "-ic", command]
            self.tmux("new-session", "-d", "-s", "term-" + name, "-c", str(self.home), *args)
        return {"name": name}

    def path(self, value):
        if not isinstance(value, str) or "\0" in value or Path(value).is_absolute():
            raise ValueError("Use a path relative to your native home")
        path = (self.home / value).resolve()
        if not path.is_relative_to(self.home):
            raise ValueError("This link is outside the workspace home; use Android's file picker")
        return path

    def read(self, path):
        if not path.is_file() or path.stat().st_size > MAX_FILE:
            raise ValueError("Choose a text file smaller than 256 KiB")
        data = path.read_bytes()
        if len(data) > MAX_FILE or b"\0" in data:
            raise ValueError("Choose a text file smaller than 256 KiB")
        return {"text": data.decode("utf-8"), "revision": hashlib.sha256(data).hexdigest()}

    def file_action(self, request):
        path = self.path(request.get("path", ""))
        action = request["op"]
        with self.lock:
            if action == "files":
                entries = []
                for item in sorted(path.iterdir(), key=lambda p: (not p.is_dir(), p.name.lower())):
                    entries.append({"name": item.name, "directory": item.is_dir(), "link": item.is_symlink()})
                    if len(entries) >= 2000:
                        break
                return {"entries": entries, "path": str(path.relative_to(self.home))}
            if action == "read":
                return self.read(path)
            if action == "mkdir":
                path.mkdir()
                return {"path": request["path"]}
            if action == "write":
                data = request["text"].encode("utf-8")
                if len(data) > MAX_FILE or b"\0" in data:
                    raise ValueError("Text must be smaller than 256 KiB")
                if path.exists():
                    current = self.read(path)
                    if current["revision"] != request.get("revision"):
                        raise ValueError("File changed since it was opened. Reopen it before saving.")
                    backup = path.with_name(path.name + ".strata-backup-" + secrets.token_hex(4))
                    atomic(backup, path.read_bytes(), path.stat().st_mode & 0o777)
                elif request.get("revision") is not None:
                    raise ValueError("File was removed since it was opened")
                atomic(path, data, path.stat().st_mode & 0o777 if path.exists() else 0o600)
                return {"revision": hashlib.sha256(data).hexdigest()}
        raise ValueError("Unknown file action")

    def service_action(self, request, internal=False):
        with self.lock:
            name = named(request.get("name", ""))
            config = self.services / (name + ".json")
            script = self.services / (name + ".sh")
            log = self.services / (name + ".log")
            op = request["op"]
            if name == "strata-desktop" and op == "service-save" and not internal:
                raise ValueError("Use the Desktop controls for this managed service")
            if op == "service-save":
                body = request["script"]
                if not isinstance(body, str) or not body.strip() or len(body.encode()) > 16000:
                    raise ValueError("Enter a foreground shell script (up to 16 KB)")
                check = subprocess.run(["bash", "-n"], input=body, text=True, capture_output=True)
                if check.returncode:
                    raise ValueError(check.stderr.strip())
                if script.exists():
                    atomic(script.with_suffix(".previous"), script.read_bytes())
                atomic(script, body.encode())
                if not config.exists():
                    atomic(config, b'{"enabled": false}')
            elif op == "service-start":
                if not script.is_file():
                    raise ValueError("Save the service first")
                if any(x["name"] == name and x["running"] for x in self.sessions("svc-")):
                    return {"running": True}
                self.tmux("kill-session", "-t", "=svc-" + name, check=False)
                # Rotate between starts. The log viewer only reads its last 64 KiB.
                if log.exists() and log.stat().st_size > 1024 * 1024:
                    os.replace(log, log.with_suffix(".log.previous"))
                self.tmux("new-session", "-d", "-s", "svc-" + name, "-c", str(self.home),
                          "bash", "-c", 'exec bash "$1" >> "$2" 2>&1', "strata-service", str(script), str(log))
                atomic(config, b'{"enabled": true}')
            elif op == "service-stop":
                self.tmux("kill-session", "-t", "=svc-" + name, check=False)
                if config.exists():
                    atomic(config, b'{"enabled": false}')
            elif op == "service-log":
                if not log.exists():
                    return {"text": "No output yet"}
                with log.open("rb") as stream:
                    stream.seek(max(0, log.stat().st_size - 65536))
                    return {"text": stream.read(65536).decode("utf-8", errors="replace")}
            elif op == "service-read":
                return {"script": script.read_text()}
            else:
                raise ValueError("Unknown service action")
            return {"saved": True}

    def restore_services(self):
        for config in self.services.glob("*.json"):
            try:
                if config.stem == "strata-desktop":
                    continue  # Graphics starts explicitly from the Desktop view.
                if json.loads(config.read_text()).get("enabled"):
                    self.service_action({"op": "service-start", "name": config.stem})
            except (OSError, ValueError, RuntimeError) as error:
                print("Service unavailable:", config.stem, str(error), file=sys.stderr, flush=True)

    def request(self, request):
        op = request.get("op")
        if op == "status":
            return {"protocol": PROTOCOL, "uid": os.getuid(), "home": str(self.home),
                    "kernel": os.uname().release, "sessions": self.sessions(), "services": self.service_list()}
        if op == "create":
            return self.create_terminal(request.get("name"), request.get("command"))
        if op == "close":
            self.tmux("kill-session", "-t", "=term-" + named(request.get("name")))
            return {"closed": True}
        if op in ("files", "read", "write", "mkdir"):
            return self.file_action(request)
        if op and op.startswith("service-"):
            return self.service_action(request)
        if op == "services":
            return {"services": self.service_list()}
        if op == "desktop-start":
            return self.desktop_start(request.get("theme", {}))
        if op == "desktop-stop":
            return self.service_action({"op": "service-stop", "name": "strata-desktop"})
        if op == "desktop-run":
            state = self.desktop_start()
            command = request.get("command", "")
            if not isinstance(command, str) or not command.strip() or len(command) > 16000:
                raise ValueError("Enter a graphical program command")
            name = "gui-" + secrets.token_hex(4)
            return self.create_terminal(name, "export DISPLAY=" + shlex.quote(state["display"])
                                        + " XAUTHORITY=" + shlex.quote(str(self.state / "desktop/Xauthority"))
                                        + "; exec bash -c " + shlex.quote(command))
        if op == "stop":
            with self.lock:
                for config in self.services.glob("*.json"):
                    atomic(config, b'{"enabled": false}')
                self.tmux("kill-server", check=False)
            return {"stopped": True}
        raise ValueError("Unsupported workspace operation")

    def desktop_start(self, theme=None):
        with self.lock:
            folder = self.state / "desktop"
            folder.mkdir(exist_ok=True, mode=0o700)
            config = folder / "session.json"
            if any(x["name"] == "strata-desktop" and x["running"] for x in self.sessions("svc-")):
                return json.loads(config.read_text())
            for program in ("Xvnc", "xauth"):
                if not shutil.which(program):
                    raise ValueError("Install graphical tools in Desktop first (missing " + program + ")")
            tmp = Path(tempfile.gettempdir())
            number = next((n for n in range(20, 100) if not (tmp / (".X%d-lock" % n)).exists()
                           and not (tmp / ".X11-unix" / ("X%d" % n)).exists()), None)
            if number is None:
                raise ValueError("No free local X display")
            display = ":" + str(number)
            auth = folder / "Xauthority"
            atomic(auth, b"")
            subprocess.run(["xauth", "-f", str(auth), "add", display, "MIT-MAGIC-COOKIE-1", secrets.token_hex(16)],
                           check=True, capture_output=True)
            sock = folder / "display.sock"
            sock.unlink(missing_ok=True)
            theme = theme or {}
            colors = {key: theme.get(key, fallback) for key, fallback in
                      (("background", "#1a1b26"), ("foreground", "#c0caf5"), ("accent", "#7aa2f7"))}
            if any(not isinstance(value, str) or not re.fullmatch(r"#[a-fA-F0-9]{6}", value) for value in colors.values()):
                raise ValueError("Invalid desktop palette")
            theme_dir = folder / "data/themes/Strata/openbox-3"
            theme_dir.mkdir(parents=True, exist_ok=True)
            native_theme = "border.width: 1\nwindow.handle.width: 2\npadding.width: 5\n"
            for state in ("active", "inactive"):
                native_theme += f"window.{state}.title.bg: Flat Solid\nwindow.{state}.title.bg.color: {colors['background']}\n"
                native_theme += f"window.{state}.label.bg: Parentrelative\nwindow.{state}.label.text.color: {colors['foreground']}\n"
                native_theme += f"window.{state}.button.unpressed.bg: Parentrelative\nwindow.{state}.button.unpressed.image.color: {colors['accent']}\n"
            native_theme += f"window.active.border.color: {colors['accent']}\nwindow.inactive.border.color: {colors['background']}\n"
            atomic(theme_dir / "themerc", native_theme.encode())
            atomic(folder / "openbox.xml", b'''<openbox_config xmlns="http://openbox.org/3.4/rc">
<theme><name>Strata</name><titleLayout>NLIMC</titleLayout></theme>
<focus><focusNew>yes</focusNew></focus><mouse>
<context name="Titlebar"><mousebind button="Left" action="Press"><action name="Focus"/><action name="Raise"/></mousebind><mousebind button="Left" action="Drag"><action name="Move"/></mousebind><mousebind button="Left" action="DoubleClick"><action name="ToggleMaximize"/></mousebind></context>
<context name="Close"><mousebind button="Left" action="Click"><action name="Close"/></mousebind></context>
<context name="Maximize"><mousebind button="Left" action="Click"><action name="ToggleMaximize"/></mousebind></context>
<context name="Iconify"><mousebind button="Left" action="Click"><action name="Iconify"/></mousebind></context>
<context name="Frame"><mousebind button="A-Left" action="Drag"><action name="Move"/></mousebind><mousebind button="A-Right" action="Drag"><action name="Resize"/></mousebind></context>
<context name="Bottom Right Left BLCorner BRCorner"><mousebind button="Left" action="Drag"><action name="Resize"/></mousebind></context>
<context name="Client"><mousebind button="Left" action="Press"><action name="Focus"/><action name="Raise"/></mousebind></context>
</mouse><keyboard><keybind key="A-Tab"><action name="NextWindow"/></keybind><keybind key="A-F4"><action name="Close"/></keybind></keyboard>
</openbox_config>''')
            # RFB has no TCP listener. Its Unix socket is private to Termux; the
            # authenticated broker is the only cross-app entry. X11 uses a cookie.
            script = "export DISPLAY=" + shlex.quote(display) + " XAUTHORITY=" + shlex.quote(str(auth)) + "\n"
            script += "strata_x_pid= strata_wm_pid= strata_term_pid=\n"
            script += "trap 'trap - EXIT HUP INT TERM; for p in $strata_term_pid $strata_wm_pid $strata_x_pid; do kill \"$p\" 2>/dev/null || :; done; wait; exit' EXIT HUP INT TERM\n"
            script += "Xvnc \"$DISPLAY\" -auth \"$XAUTHORITY\" -geometry 1024x720 -depth 24 -nolisten tcp -rfbport -1 -rfbunixpath " + shlex.quote(str(sock)) + " -rfbunixmode 0600 -SecurityTypes None -AlwaysShared &\nstrata_x_pid=$!\n"
            script += "for ((i=0;i<100;i++)); do [[ -S " + shlex.quote(str(sock)) + " ]] && break; kill -0 \"$strata_x_pid\" 2>/dev/null || exit 1; sleep 0.1; done\n"
            script += "if command -v openbox >/dev/null; then XDG_DATA_HOME=" + shlex.quote(str(folder / "data")) + " openbox --config-file " + shlex.quote(str(folder / "openbox.xml")) + " & strata_wm_pid=$!; fi\n"
            script += "if command -v aterm >/dev/null; then aterm -title Strata -bg " + shlex.quote(colors["background"]) + " -fg " + shlex.quote(colors["foreground"]) + " -e bash --rcfile " + shlex.quote(str(self.rc)) + " -i & strata_term_pid=$!; fi\nwait \"$strata_x_pid\"\n"
            state = {"display": display}
            atomic(config, json.dumps(state).encode())
            self.service_action({"op": "service-save", "name": "strata-desktop", "script": script}, internal=True)
            self.service_action({"op": "service-start", "name": "strata-desktop"})
            for _ in range(100):
                if sock.is_socket():
                    return state
                time.sleep(.1)
            raise RuntimeError("Display did not start. Read strata-desktop's service log.")

    def service_list(self):
        live = {x["name"]: x for x in self.sessions("svc-")}
        return [dict(name=path.stem, enabled=json.loads(path.read_text()).get("enabled", False),
                     running=live.get(path.stem, {}).get("running", False),
                     exit=live.get(path.stem, {}).get("exit")) for path in sorted(self.services.glob("*.json"))]


class Handler(socketserver.StreamRequestHandler):
    def handle(self):
        self.connection.settimeout(5)
        try:
            nonce = secrets.token_hex(32)
            send(self.wfile, {"challenge": nonce, "protocol": PROTOCOL})
            auth = receive(self.rfile)
            client_nonce = auth.get("nonce", "")
            if not re.fullmatch(r"[a-f0-9]{64}", client_nonce):
                return
            message = nonce + ":" + client_nonce
            if not hmac.compare_digest(auth.get("auth", ""), proof(self.server.token, "client:" + message)):
                return
            send(self.wfile, {"authenticated": proof(self.server.token, "server:" + message)})
            self.connection.settimeout(30)
            request = receive(self.rfile)
            if request.get("op") == "attach":
                self.attach(request)
            elif request.get("op") == "display":
                self.display()
            else:
                send(self.wfile, {"ok": True, **self.server.workspace.request(request)})
                if request.get("op") == "stop":
                    self.server.shutdown()
        except (OSError, ValueError, KeyError, TypeError, RuntimeError, subprocess.SubprocessError) as error:
            try:
                send(self.wfile, {"ok": False, "error": str(error)[:1000]})
            except OSError:
                pass

    def attach(self, request):
        name = named(request.get("name"))
        ws = self.server.workspace
        if not any(item["name"] == name for item in ws.sessions()):
            raise ValueError("Session does not exist")
        master, slave = os.openpty()
        process = None
        try:
            self.resize(master, request)
            environment = dict(os.environ, TERM="xterm-256color", COLORTERM="truecolor")
            # Acquire a controlling tty in a fresh interpreter, not a preexec_fn
            # callback in this threaded process. This delivers SIGWINCH and job
            # control correctly. Closing this client still only detaches tmux.
            child = "import os,fcntl,termios,sys; fcntl.ioctl(0,termios.TIOCSCTTY,0); os.execvp(sys.argv[1],sys.argv[1:])"
            process = subprocess.Popen([sys.executable, "-c", child, "tmux", "-S", ws.socket, "attach-session", "-t", "=term-" + name],
                                       stdin=slave, stdout=slave, stderr=slave, env=environment,
                                       start_new_session=True, close_fds=True)
            os.close(slave)
            slave = -1
            send(self.wfile, {"ok": True, "attached": name})
            self.connection.settimeout(15)
            pending = b""
            while True:
                ready, _, _ = select.select([master, self.connection], [], [], 1)
                if self.connection in ready:
                    data = self.connection.recv(65536)
                    if not data:
                        break
                    pending += data
                    if len(pending) > MAX_FRAME:
                        raise ValueError("Input frame too large")
                    while b"\n" in pending:
                        line, pending = pending.split(b"\n", 1)
                        event = json.loads(line)
                        if event.get("op") == "input":
                            data = base64.b64decode(event["data"], validate=True)
                            if len(data) > 32768:
                                raise ValueError("Input too large")
                            # Partial writes are possible with large clipboard pastes.
                            while data:
                                _, writable, _ = select.select([], [master], [], 5)
                                if not writable:
                                    raise ValueError("Terminal input stalled")
                                data = data[os.write(master, data[:4096]):]
                        elif event.get("op") == "resize":
                            self.resize(master, event)
                        elif event.get("op") == "detach":
                            return
                if master in ready:
                    try:
                        data = os.read(master, 16384)
                    except OSError:
                        break
                    if not data:
                        break
                    send(self.wfile, {"data": base64.b64encode(data).decode()})
                if process.poll() is not None:
                    break
            send(self.wfile, {"detached": True})
        finally:
            os.close(master)
            if slave >= 0:
                os.close(slave)
            if process is not None:
                if process.poll() is None:
                    process.terminate()
                try:
                    process.wait(timeout=3)
                except subprocess.TimeoutExpired:
                    process.kill()
                    process.wait()

    @staticmethod
    def resize(master, request):
        cols = max(2, min(300, int(request.get("cols", 80))))
        rows = max(2, min(150, int(request.get("rows", 24))))
        fcntl.ioctl(master, termios.TIOCSWINSZ, struct.pack("HHHH", rows, cols, 0, 0))

    def display(self):
        with socket.socket(socket.AF_UNIX, socket.SOCK_STREAM) as display:
            display.settimeout(10)
            display.connect(str(self.server.workspace.state / "desktop/display.sock"))
            send(self.wfile, {"ok": True, "display": True})
            pending = b""
            while True:
                ready, _, _ = select.select([display, self.connection], [], [], 30)
                if self.connection in ready:
                    data = self.connection.recv(65536)
                    if not data:
                        return
                    pending += data
                    if len(pending) > MAX_FRAME:
                        raise ValueError("Display input frame too large")
                    while b"\n" in pending:
                        line, pending = pending.split(b"\n", 1)
                        event = json.loads(line)
                        if event.get("op") == "input":
                            data = base64.b64decode(event["data"], validate=True)
                            if len(data) > 32768:
                                raise ValueError("Display input too large")
                            display.sendall(data)
                        elif event.get("op") == "detach":
                            return
                if display in ready:
                    data = display.recv(32768)
                    if not data:
                        return
                    send(self.wfile, {"displayData": base64.b64encode(data).decode()})


class Server(socketserver.ThreadingTCPServer):
    allow_reuse_address = True
    daemon_threads = True

    def __init__(self, address, token, workspace):
        self.token = token
        self.workspace = workspace
        self.slots = threading.BoundedSemaphore(16)
        super().__init__(address, Handler)

    def process_request(self, request, address):
        if not self.slots.acquire(blocking=False):
            self.shutdown_request(request)
            return
        try:
            super().process_request(request, address)
        except Exception:
            self.slots.release()
            raise

    def process_request_thread(self, request, address):
        try:
            super().process_request_thread(request, address)
        finally:
            self.slots.release()


def main():
    os.umask(0o077)
    config = json.load(sys.stdin)
    if not re.fullmatch(r"[a-f0-9]{64}", config["token"]):
        raise ValueError("Invalid pairing secret")
    home = Path.home()
    state = home / ".local/state/strata"
    ws = Workspace(home, state)
    # Duplicate launches cannot replace an active listener or spawn extra services.
    with (state / "runtime.lock").open("w") as lock:
        try:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            raise SystemExit("Workspace already running. Reconnect from Strata.")
        with Server(("127.0.0.1", int(config["port"])), config["token"], ws) as server:
            ws.restore_services()
            print("Strata native workspace ready; UID", os.getuid(), flush=True)
            server.serve_forever(poll_interval=0.5)


if __name__ == "__main__":
    main()
