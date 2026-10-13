"""Check extraction-boundary cases for privileged module installation."""
import importlib.util
import io
from pathlib import Path
import tarfile
import tempfile
import unittest

spec = importlib.util.spec_from_file_location("oma_build", Path(__file__).resolve().parents[1] / "build.py")
builder = importlib.util.module_from_spec(spec)
spec.loader.exec_module(builder)


class ArchiveTests(unittest.TestCase):
    def inventory(self, entries):
        with tempfile.NamedTemporaryFile(suffix=".tar.gz") as file:
            with tarfile.open(file.name, "w:gz") as archive:
                for name, kind, target in entries:
                    item = tarfile.TarInfo(name)
                    item.type = kind
                    item.linkname = target
                    archive.addfile(item, io.BytesIO())
            return builder.archive_inventory(file.name)

    def test_rejects_path_traversal_and_absolute_paths(self):
        for path in ("../../data/host", "/data/host"):
            with self.subTest(path=path), self.assertRaises(ValueError):
                self.inventory([(path, tarfile.REGTYPE, "")])

    def test_rejects_writes_through_symlinks_regardless_of_entry_order(self):
        entries = [("escape", tarfile.SYMTYPE, "/data/host"), ("escape/file", tarfile.REGTYPE, "")]
        for order in (entries, entries[::-1]):
            with self.assertRaises(ValueError):
                self.inventory(order)

    def test_rejects_external_hardlinks(self):
        with self.assertRaises(ValueError):
            self.inventory([("escape", tarfile.LNKTYPE, "/data/host")])

    def test_accepts_guest_absolute_symlinks_without_extraction_through_them(self):
        self.assertEqual(self.inventory([("bin", tarfile.DIRTYPE, ""),
                                         ("bin/tool", tarfile.REGTYPE, ""),
                                         ("tool", tarfile.SYMTYPE, "/bin/tool")]), 0)
