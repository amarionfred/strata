"""Real PTY/tmux/process and wire integration checks, using a temporary home."""
import base64
import importlib.util
import json
import os
from pathlib import Path
import secrets
import socket
import subprocess
import sys
import tempfile
import threading
import time
import unittest

SPEC = importlib.util.spec_from_file_location("runtime", Path(__file__).resolve().parents[1] / "runtime.py")
runtime = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(runtime)


class Client:
    def __init__(self, address, token):
        self.socket = socket.create_connection(address, timeout=5)
        self.stream = self.socket.makefile("rwb", buffering=0)
        hello = runtime.receive(self.stream)
        nonce = secrets.token_hex(32)
        message = hello["challenge"] + ":" + nonce
        runtime.send(self.stream, {"nonce": nonce, "auth": runtime.proof(token, "client:" + message)})
        response = runtime.receive(self.stream)
        assert response["authenticated"] == runtime.proof(token, "server:" + message)

    def request(self, op, **args):
        runtime.send(self.stream, {"op": op, **args})
        return runtime.receive(self.stream)

    def close(self):
        self.stream.close()
        self.socket.close()


class WorkspaceTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory(prefix="strata-ws-")
        self.home = Path(self.directory.name)
        self.ws = runtime.Workspace(self.home, self.home / ".state")
        self.token = secrets.token_hex(32)
        self.server = runtime.Server(("127.0.0.1", 0), self.token, self.ws)
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()
        self.clients = []

    def tearDown(self):
        for client in self.clients:
            client.close()
        self.server.shutdown()
        self.server.server_close()
        self.thread.join(5)
        self.ws.tmux("kill-server", check=False)
        self.directory.cleanup()

    def client(self):
        client = Client(self.server.server_address, self.token)
        self.clients.append(client)
        return client

    def call(self, op, **args):
        client = self.client()
        try:
            return client.request(op, **args)
        finally:
            self.clients.remove(client)
            client.close()

    def until(self, predicate):
        deadline = time.monotonic() + 12
        while time.monotonic() < deadline:
            if predicate():
                return
            time.sleep(.05)
        self.fail("Native process did not reach the expected state: " + self.ws.tmux("list-clients", "-F", "#{client_width}x#{client_height}", check=False).stdout
                  + self.ws.tmux("list-panes", "-a", "-F", "#{pane_width}x#{pane_height}", check=False).stdout)

    def output_until(self, client, needle):
        output = b""
        while needle not in output:
            frame = runtime.receive(client.stream)
            if "data" not in frame:
                self.fail(str(frame))
            data = base64.b64decode(frame["data"])
            output += data
            # tmux asks its terminal for capabilities. A real xterm renderer
            # answers these; the wire-only test client must do so as well.
            replies = b""
            for query, reply in ((b"\x1b[c", b"\x1b[?1;2c"), (b"\x1b[>c", b"\x1b[>0;276;0c"),
                                 (b"\x1b[>q", b"\x1bP>|XTerm(276)\x1b\\"), (b"\x1b[6n", b"\x1b[1;1R")):
                if query in data:
                    replies += reply
            if replies:
                runtime.send(client.stream, {"op":"input", "data":base64.b64encode(replies).decode()})
        return output

    def test_unauthenticated_and_replayed_credentials_cannot_execute(self):
        for proof in ("0" * 64, runtime.proof(self.token, "client:old:nonce")):
            with socket.create_connection(self.server.server_address) as sock:
                with sock.makefile("rwb", buffering=0) as stream:
                    runtime.receive(stream)
                    runtime.send(stream, {"auth": proof, "nonce": "a" * 64})
                    self.assertEqual(stream.readline(), b"")
        self.assertEqual(self.ws.sessions(), [])

    def test_native_identity_and_persistent_file_roundtrip_conflict_backup(self):
        self.assertEqual(self.call("status")["uid"], os.getuid())
        text = 'print("native café λ")\n'
        saved = self.call("write", path="hello.py", text=text)
        self.assertTrue(saved["ok"])
        read = self.call("read", path="hello.py")
        self.assertEqual(read["text"], text)
        changed = self.call("write", path="hello.py", text="updated", revision=read["revision"])
        self.assertTrue(changed["ok"])
        conflict = self.call("write", path="hello.py", text="lost update", revision=read["revision"])
        self.assertFalse(conflict["ok"])
        self.assertEqual(self.home.joinpath("hello.py").read_text(), "updated")
        backups = list(self.home.glob("hello.py.strata-backup-*"))
        self.assertEqual(backups[0].read_text(), text)
        other = runtime.Workspace(self.home, self.home / ".state")
        self.assertEqual(other.read(self.home / "hello.py")["text"], "updated")

    def test_file_boundary_blocks_traversal_symlinks_binary_and_oversize(self):
        self.home.joinpath("outside").symlink_to("/etc")
        self.home.joinpath("binary").write_bytes(b"\0data")
        self.home.joinpath("large").write_bytes(b"x" * (runtime.MAX_FILE + 1))
        for path in ("../other", "outside/passwd", "/etc/passwd", "binary", "large"):
            self.assertFalse(self.call("read", path=path)["ok"], path)

    def test_interactive_pty_resize_ctrl_c_and_reconnect_keep_process_and_cwd(self):
        self.assertTrue(self.call("create", name="main")["ok"])
        client = self.client()
        self.assertTrue(client.request("attach", name="main", cols=93, rows=31)["ok"])
        command = "mkdir -p testdir; cd testdir; export STRATA_CHECK=retained; stty size; printf 'PTY_OK\\n'\n"
        runtime.send(client.stream, {"op":"input", "data":base64.b64encode(command.encode()).decode()})
        output = self.output_until(client, b"31 93")
        self.assertIn(b"31 93", output)
        runtime.send(client.stream, {"op":"resize", "cols":71, "rows":19})
        self.until(lambda: self.ws.tmux("list-panes", "-a", "-F", "#{pane_width} #{pane_height}").stdout.strip() == "71 19")
        # tmux debounces the kernel PTY resize after updating its pane layout.
        time.sleep(.4)
        runtime.send(client.stream, {"op":"input", "data":base64.b64encode(b"stty size > resized\n").decode()})
        self.until(lambda: (self.home / "testdir/resized").exists())
        self.assertEqual((self.home / "testdir/resized").read_text().strip(), "19 71")
        runtime.send(client.stream, {"op":"input", "data":base64.b64encode(b"sleep 90\n").decode()})
        time.sleep(.2)
        runtime.send(client.stream, {"op":"input", "data":base64.b64encode(b"\x03").decode()})
        time.sleep(.1)
        runtime.send(client.stream, {"op":"input", "data":base64.b64encode(b"printf 'CTRL_OK' > interrupted\n").decode()})
        self.until(lambda: (self.home / "testdir/interrupted").exists())
        client.close(); self.clients.remove(client)
        client = self.client()
        client.request("attach", name="main", cols=80, rows=24)
        runtime.send(client.stream, {"op":"input", "data":base64.b64encode(b"printf '%s' \"$STRATA_CHECK\" > retained; pwd > cwd\n").decode()})
        self.until(lambda: (self.home / "testdir/cwd").exists())
        self.assertEqual((self.home / "testdir/retained").read_text(), "retained")
        self.assertEqual((self.home / "testdir/cwd").read_text().strip(), str(self.home / "testdir"))
        self.assertEqual(len(self.ws.sessions()), 1)

    def test_actual_interpreter_executes_saved_file_and_preserves_exit_code(self):
        self.call("write", path="script.py", text="from pathlib import Path\nPath('result').write_text('executed')\nraise SystemExit(7)\n")
        self.assertTrue(self.call("create", name="python", command="python3 script.py")["ok"])
        self.until(lambda: self.home.joinpath("result").exists())
        self.until(lambda: not self.ws.sessions()[0]["running"])
        self.assertEqual(self.ws.sessions()[0]["exit"], 7)
        self.assertEqual(self.home.joinpath("result").read_text(), "executed")

    def test_service_start_stop_log_and_broker_restart_preserve_native_pid(self):
        script = 'echo "service started"; echo $$ > service.pid; exec sleep 90'
        self.assertTrue(self.call("service-save", name="demo", script=script)["ok"])
        self.assertTrue(self.call("service-start", name="demo")["ok"])
        self.until(lambda: self.home.joinpath("service.pid").exists())
        pid = int(self.home.joinpath("service.pid").read_text())
        os.kill(pid, 0)
        self.assertIn("service started", self.call("service-log", name="demo")["text"])
        other = runtime.Workspace(self.home, self.home / ".state")
        other.restore_services()
        self.assertEqual(int(self.home.joinpath("service.pid").read_text()), pid)
        self.assertTrue(other.service_list()[0]["running"])
        self.assertTrue(self.call("service-stop", name="demo")["ok"])
        def stopped():
            try: os.kill(pid, 0); return False
            except ProcessLookupError: return True
        self.until(stopped)
        self.assertFalse(other.service_list()[0]["enabled"])

    def test_no_shell_interpolation_in_names_and_invalid_service_keeps_previous(self):
        marker = self.home / "injected"
        self.assertFalse(self.call("create", name="a;touch " + str(marker))["ok"])
        self.assertFalse(marker.exists())
        self.call("service-save", name="good", script="echo good")
        self.assertFalse(self.call("service-save", name="good", script="if then")["ok"])
        self.assertEqual((self.ws.services / "good.sh").read_text(), "echo good")

    def test_stop_ends_workspace_processes_keeps_files_and_disables_service_restart(self):
        self.call("write", path="keep.txt", text="persistent")
        self.call("create", name="main")
        self.call("service-save", name="demo", script="exec sleep 90")
        self.call("service-start", name="demo")
        self.assertTrue(self.call("stop")["stopped"])
        self.assertEqual(self.ws.sessions(), [])
        self.assertFalse(self.ws.service_list()[0]["enabled"])
        self.assertEqual((self.home / "keep.txt").read_text(), "persistent")

    def test_broker_process_restart_keeps_existing_terminal_and_service_pids(self):
        with socket.socket() as reservation:
            reservation.bind(("127.0.0.1", 0))
            port = reservation.getsockname()[1]
        token = secrets.token_hex(32)
        state = self.home / ".local/state/strata"
        source = Path(runtime.__file__).read_text()
        # Same stdin/code-loading contract used by the APK's Termux task.
        loader = "import sys,json,io; c=json.load(sys.stdin); s=c.pop('source'); sys.stdin=io.StringIO(json.dumps(c)); exec(compile(s,'strata-runtime','exec'))"
        process = None
        def start():
            process = subprocess.Popen([sys.executable, "-u", "-c", loader], stdin=subprocess.PIPE,
                                       stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True,
                                       env={**os.environ, "HOME": str(self.home)})
            process.stdin.write(json.dumps({"source": source, "port": port, "token": token}))
            process.stdin.close()
            self.assertIn("workspace ready", process.stdout.readline())
            return process
        def call(op, **args):
            client = Client(("127.0.0.1", port), token)
            try: return client.request(op, **args)
            finally: client.close()
        try:
            process = start()
            self.assertTrue(call("create", name="main")["ok"])
            call("service-save", name="keeper", script="echo $$ > keeper.pid; exec sleep 90")
            call("service-start", name="keeper")
            self.until(lambda: (self.home / "keeper.pid").exists())
            service_pid = int((self.home / "keeper.pid").read_text())
            before = subprocess.check_output(["tmux", "-S", str(state / "tmux.sock"), "list-panes", "-a", "-F", "#{pane_pid}"], text=True)
            process.terminate(); process.wait(5)
            process.stdout.close(); process.stderr.close()
            process = start()
            self.assertTrue(call("status")["sessions"][0]["running"])
            self.assertTrue(call("services")["services"][0]["running"])
            os.kill(service_pid, 0)
            after = subprocess.check_output(["tmux", "-S", str(state / "tmux.sock"), "list-panes", "-a", "-F", "#{pane_pid}"], text=True)
            self.assertEqual(before, after)
            call("stop"); self.assertEqual(process.wait(5), 0)
        finally:
            if process is not None:
                if process.poll() is None: process.terminate(); process.wait(5)
                process.stdout.close(); process.stderr.close()
            subprocess.run(["tmux", "-S", str(state / "tmux.sock"), "kill-server"], capture_output=True)


if __name__ == "__main__":
    unittest.main()
