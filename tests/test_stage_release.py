import json
import tempfile
import unittest
import zipfile
from pathlib import Path
from importlib.util import module_from_spec, spec_from_file_location

script = Path(__file__).resolve().parents[1] / "scripts" / "stage-release.py"
spec = spec_from_file_location("stage_release", script)
module = module_from_spec(spec)
spec.loader.exec_module(module)


class StageReleaseTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        (self.root / "build").mkdir()
        (self.root / "EA-FB" / "build").mkdir(parents=True)
        (self.root / "build" / "plugins.json").write_text(json.dumps([
            {"internalName": "EA-FB", "version": 1}
        ]))

    def make_archive(self):
        path = self.root / "EA-FB" / "build" / "EA-FB.cs3"
        with zipfile.ZipFile(path, "w") as z:
            z.writestr("classes.dex", b"dex-test-placeholder" * 20)
            z.writestr("manifest.json", '{"name":"EA-FB"}')
        return path

    def test_valid_staging(self):
        self.make_archive()
        dist = module.stage(self.root)
        metadata = json.loads((dist / "plugins.json").read_text())
        self.assertEqual(len(metadata), 1)
        self.assertEqual(metadata[0]["fileSize"], (dist / "EA-FB.cs3").stat().st_size)
        self.assertEqual(json.loads((dist / "repo.json").read_text())["manifestVersion"], 1)

    def test_reject_missing_binary(self):
        with self.assertRaisesRegex(ValueError, "Expected exactly one"):
            module.stage(self.root)

    def test_reject_invalid_binary(self):
        (self.root / "EA-FB" / "build" / "EA-FB.cs3").write_bytes(b"garbage" * 100)
        with self.assertRaisesRegex(ValueError, "Invalid"):
            module.stage(self.root)

    def test_reject_missing_dex(self):
        path = self.make_archive()
        with zipfile.ZipFile(path, "w") as z:
            z.writestr("other.txt", "nothing")
        with self.assertRaisesRegex(ValueError, "missing required"):
            module.stage(self.root)

    def test_reject_multiple_extensions(self):
        self.make_archive()
        (self.root / "build" / "plugins.json").write_text(json.dumps([
            {"internalName": "EA-FB", "version": 1},
            {"internalName": "Other", "version": 1}
        ]))
        with self.assertRaisesRegex(ValueError, "exactly one"):
            module.stage(self.root)


if __name__ == "__main__":
    unittest.main()
