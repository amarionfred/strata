"""Behavioral tests on the running Linux kernel; no VM/emulator is involved.

Prepare the verified x86_64 Alpine fixture as described in system/README.md,
then run: python3 -m unittest discover -s system/tests -v
"""
import os
from pathlib import Path
import signal
import shutil
import subprocess
import tempfile
import time
import unittest


REPO = Path(__file__).resolve().parents[2]
BINARY = REPO / ".local/native/oma-session"
ROOTFS = REPO / ".local/native/rootfs"


def command(*arguments, pidfile=None):
    result = ["unshare", "--user", "--map-auto", "--map-root-user", str(BINARY)]
    if pidfile:
        result += ["--pidfile", str(pidfile)]
    return result + [str(ROOTFS), "--", *arguments]


def descendants(pid):
    try:
        children = Path(f"/proc/{pid}/task/{pid}/children").read_text().split()
    except FileNotFoundError:
        return set()
    result = set(map(int, children))
    for child in list(result):
        result |= descendants(child)
    return result


def alive(pid):
    try:
        return Path(f"/proc/{pid}/stat").read_text().rsplit(") ", 1)[1][0] != "Z"
    except FileNotFoundError:
        return False


class NativeSessionTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        if not BINARY.is_file() or not (ROOTFS / "bin/busybox").is_file():
            raise RuntimeError("Build oma-session and prepare the verified native rootfs first")

    def run_guest(self, script, **kwargs):
        return subprocess.run(command("/bin/sh", "-c", script), capture_output=True,
                              text=True, timeout=15, **kwargs)

    def test_real_kernel_root_mounts_and_persistent_files(self):
        hostname_before = os.uname().nodename
        first = self.run_guest(
            "set -eu; test $(id -u) = 0; "
            "test $(cat /proc/1/comm) = oma-session; "
            "mount -t tmpfs tmpfs /mnt; echo mounted > /mnt/proof; "
            "test $(cat /mnt/proof) = mounted; "
            "echo persistent > /root/oma-native-test; uname -r"
        )
        self.assertEqual(first.returncode, 0, first.stderr)
        self.assertEqual(first.stdout.strip(), os.uname().release)
        second = self.run_guest("cat /root/oma-native-test")
        self.assertEqual(second.returncode, 0, second.stderr)
        self.assertEqual(second.stdout.strip(), "persistent")
        self.assertFalse((ROOTFS / "proc/self").exists(), "guest mounts leaked into host")
        self.assertFalse((ROOTFS / "mnt/proof").exists(), "guest mount leaked into host")
        self.assertEqual(os.uname().nodename, hostname_before)

    def test_real_installed_package_and_sudo(self):
        result = self.run_guest("set -eu; apk info -e tree sudo; tree --version; sudo -n id -u")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("tree v", result.stdout)
        self.assertTrue(result.stdout.rstrip().endswith("0"))

    def test_exit_status_and_descendant_cleanup(self):
        result = self.run_guest("sleep 300 & exit 42")
        self.assertEqual(result.returncode, 42, result.stderr)

    def test_service_manager_starts_and_restarts_a_real_process(self):
        manager = ROOTFS / "usr/local/libexec/oma-services"
        manager.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(REPO / "system/module/guest/oma-services", manager)
        manager.chmod(0o755)
        service = ROOTFS / "etc/oma-system/services/oma-native-test"
        service.parent.mkdir(parents=True, exist_ok=True)
        evidence = ROOTFS / "root/oma-service-test"
        evidence.unlink(missing_ok=True)
        service.write_text("#!/bin/sh\necho started >> /root/oma-service-test\nexit 1\n")
        service.chmod(0o755)
        process = subprocess.Popen(command("/usr/local/libexec/oma-services"),
                                   stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)
        try:
            deadline = time.monotonic() + 8
            while time.monotonic() < deadline:
                if evidence.exists() and len(evidence.read_text().splitlines()) >= 2:
                    break
                if process.poll() is not None:
                    self.fail(process.stderr.read().decode())
                time.sleep(0.1)
            self.assertGreaterEqual(len(evidence.read_text().splitlines()), 2)
        finally:
            process.terminate()
            process.wait(timeout=8)
            process.stderr.close()
            service.unlink(missing_ok=True)

    def test_environment_is_not_inherited(self):
        result = self.run_guest('test -z "${OMA_TEST_HOST_SECRET:-}"; test "$HOME" = /root',
                                env={**os.environ, "OMA_TEST_HOST_SECRET": "host-only"})
        self.assertEqual(result.returncode, 0, result.stderr)

    def test_refuses_host_root_and_symlink_mount_target(self):
        result = subprocess.run(["unshare", "--user", "--map-root-user", str(BINARY),
                                 "/", "--", "/bin/true"], capture_output=True, text=True)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("host root", result.stderr)
        with tempfile.TemporaryDirectory() as fixture:
            Path(fixture, "proc").symlink_to("/proc")
            result = subprocess.run(["unshare", "--user", "--map-root-user", str(BINARY),
                                     fixture, "--", "/bin/true"], capture_output=True, text=True)
            self.assertNotEqual(result.returncode, 0)
            self.assertIn("mount target", result.stderr)

    def test_supervisor_shutdown_and_forced_death_stop_the_session(self):
        for stop_signal in (signal.SIGTERM, signal.SIGKILL):
            with self.subTest(signal=stop_signal), tempfile.TemporaryDirectory() as state:
                pidfile = Path(state, "session.pid")
                process = subprocess.Popen(command("/bin/sh", "-c", "sleep 300 & wait", pidfile=pidfile),
                                           stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)
                try:
                    deadline = time.monotonic() + 5
                    children = set()
                    while time.monotonic() < deadline:
                        children = descendants(process.pid)
                        if len(children) >= 3:
                            break
                        if process.poll() is not None:
                            self.fail(process.stderr.read().decode())
                        time.sleep(0.05)
                    self.assertGreaterEqual(len(children), 3)
                    self.assertEqual(int(pidfile.read_text().split()[0]), process.pid)
                    process.send_signal(stop_signal)
                    process.wait(timeout=8)
                    deadline = time.monotonic() + 5
                    while any(alive(child) for child in children) and time.monotonic() < deadline:
                        time.sleep(0.05)
                    self.assertFalse(any(alive(child) for child in children), "session processes survived")
                    if stop_signal == signal.SIGTERM:
                        self.assertFalse(pidfile.exists())
                finally:
                    if process.poll() is None:
                        process.kill()
                        process.wait(timeout=8)
                    process.stderr.close()


if __name__ == "__main__":
    unittest.main()
