"""Installer checks using temporary files; Android intents are not executed."""
import hashlib
from pathlib import Path
import subprocess
import tempfile
import unittest


SCRIPT = Path(__file__).resolve().parents[2] / "install.sh"


class InstallTests(unittest.TestCase):
    def setUp(self):
        self.fixture = tempfile.TemporaryDirectory(prefix="strata-install-")
        self.directory = Path(self.fixture.name)

    def tearDown(self):
        self.fixture.cleanup()

    def shell(self, command, *arguments):
        return subprocess.run(
            ["bash", "-c", 'set -euo pipefail; source "$1"; shift; ' + command,
             "install-test", str(SCRIPT), *map(str, arguments)],
            capture_output=True, text=True, timeout=10)

    def configure(self, directory):
        return self.shell('termux-reload-settings() { :; }; connect_termux "$1"', directory)

    def test_new_config_and_rerun(self):
        first = self.configure(self.directory)
        self.assertEqual(first.returncode, 0, first.stderr)
        config = self.directory / ".termux/termux.properties"
        self.assertEqual(config.read_text(), "allow-external-apps = true\n")
        self.assertEqual(config.stat().st_mode & 0o777, 0o600)
        self.assertEqual(self.configure(self.directory).returncode, 0)
        self.assertEqual(list(config.parent.iterdir()), [config])

    def test_existing_config_preserved_and_backed_up_once(self):
        config = self.directory / ".termux/termux.properties"
        config.parent.mkdir()
        original = "# my config\nfont-size=14\nallow-external-apps: false\nallow-external-apps = false\nextra-keys = [['ESC']]\n"
        config.write_text(original)
        config.chmod(0o640)
        result = self.configure(self.directory)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(config.read_text(), "# my config\nfont-size=14\nallow-external-apps = true\nextra-keys = [['ESC']]\n")
        backup = config.with_name(config.name + ".strata-backup")
        self.assertEqual(backup.read_text(), original)
        self.assertEqual(config.stat().st_mode & 0o777, 0o640)
        self.assertEqual(self.configure(self.directory).returncode, 0)
        self.assertEqual(backup.read_text(), original)
        self.assertEqual(len(list(config.parent.iterdir())), 2)

    def test_linked_config_is_updated_without_replacing_link(self):
        target = self.directory / "my properties"
        target.write_text("allow-external-apps=false\nfont-size=13\n")
        link = self.directory / ".termux/termux.properties"
        link.parent.mkdir()
        link.symlink_to(target)
        result = self.configure(self.directory)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertTrue(link.is_symlink())
        self.assertEqual(target.read_text(), "allow-external-apps = true\nfont-size=13\n")

    def test_valid_apk_checksum_ignores_other_assets(self):
        apk = self.directory / "strata.apk"
        apk.write_bytes(b"APK test fixture")
        manifest = self.directory / "SHA256SUMS"
        manifest.write_text("0" * 64 + "  other.zip\n" + hashlib.sha256(apk.read_bytes()).hexdigest() + "  strata.apk\n")
        result = self.shell('verify_apk "$1" "$2"', manifest, apk)
        self.assertEqual(result.returncode, 0, result.stderr)

    def test_corrupt_missing_and_duplicate_checksums_stop_before_install(self):
        apk = self.directory / "strata.apk"
        apk.write_bytes(b"APK test fixture")
        manifest = self.directory / "SHA256SUMS"
        digest = hashlib.sha256(apk.read_bytes()).hexdigest()
        for contents in ("0" * 64 + "  strata.apk\n", digest + "  ../../strata.apk\n",
                         (digest + "  strata.apk\n") * 2, "not-a-hash  strata.apk\n"):
            with self.subTest(contents=contents):
                manifest.write_text(contents)
                result = self.shell('verify_apk "$1" "$2"; printf SHOULD_NOT_INSTALL', manifest, apk)
                self.assertNotEqual(result.returncode, 0)
                self.assertNotIn("SHOULD_NOT_INSTALL", result.stdout)

    def test_release_resolution_accepts_only_our_tag_url(self):
        for value, accepted in (("https://github.com/amarionfred/strata/releases/tag/strata-v0.5.0-preview", True),
                                ("https://example.com/releases/tag/strata-v0.5.0-preview", False),
                                ("https://github.com/amarionfred/strata/releases/latest", False)):
            with self.subTest(value=value):
                result = self.shell('strata_test_url="$1"; curl() { printf %s "$strata_test_url"; }; release_tag', value)
                self.assertEqual(result.returncode == 0, accepted, result.stderr)

    def test_failed_download_stops_before_setup_or_install(self):
        result = self.shell('curl() { return 28; }; download https://example.com/apk "$1"; printf SHOULD_NOT_INSTALL', self.directory / "apk")
        self.assertNotEqual(result.returncode, 0)
        self.assertNotIn("SHOULD_NOT_INSTALL", result.stdout)
        self.assertIn("Download failed", result.stderr)


if __name__ == "__main__":
    unittest.main()
