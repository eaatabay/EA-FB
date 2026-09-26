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
            {"internalName": "EA-FB", "version": 6}
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
        repo_meta = json.loads((dist / "repo.json").read_text())
        self.assertEqual(metadata[0]["iconUrl"], module.ICON)
        self.assertEqual(repo_meta["iconUrl"], module.ICON)

    def test_reject_stale_plugin_version(self):
        self.make_archive()
        for version in (4, 5, 7, None, 6.0, "6", True):
            (self.root / "build" / "plugins.json").write_text(json.dumps([
                {"internalName": "EA-FB", "version": version}
            ]))
            with self.assertRaisesRegex(ValueError, "Expected EA-FB v6"):
                module.stage(self.root)

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

    def test_reject_invalid_compiled_manifest(self):
        path = self.make_archive()
        with zipfile.ZipFile(path, "w") as z:
            z.writestr("classes.dex", b"dex-test-placeholder" * 20)
            z.writestr("manifest.json", "{not-json")
        with self.assertRaisesRegex(ValueError, "Invalid .cs3 manifest JSON"):
            module.stage(self.root)

    def test_reject_other_plugin_manifest_identity(self):
        path = self.make_archive()
        with zipfile.ZipFile(path, "w") as z:
            z.writestr("classes.dex", b"dex-test-placeholder" * 20)
            z.writestr("manifest.json", '{"name":"OTHER"}')
        with self.assertRaisesRegex(ValueError, "Unexpected .cs3 manifest identity"):
            module.stage(self.root)

    def test_reject_duplicate_critical_zip_members(self):
        path = self.make_archive()
        with zipfile.ZipFile(path, "a") as z:
            import warnings
            with warnings.catch_warnings():
                warnings.simplefilter("ignore", UserWarning)
                z.writestr("classes.dex", b"duplicate")
        with self.assertRaisesRegex(ValueError, "Duplicate"):
            module.stage(self.root)

    def test_reject_zip_path_traversal(self):
        path = self.make_archive()
        with zipfile.ZipFile(path, "a") as z:
            z.writestr("../unexpected.txt", "unsafe")
        with self.assertRaisesRegex(ValueError, "Unsafe"):
            module.stage(self.root)

    def test_reject_symlinked_binary(self):
        path = self.make_archive()
        external = self.root / "external.cs3"
        path.rename(external)
        path.symlink_to(external)
        with self.assertRaisesRegex(ValueError, "symlinked"):
            module.stage(self.root)

    def test_reject_oversized_zip_member_before_decompression(self):
        path = self.make_archive()
        with zipfile.ZipFile(path, "a") as z:
            z.writestr("oversized.bin", b"x" * (64 * 1024 * 1024 + 1),
                       compress_type=zipfile.ZIP_DEFLATED)
        with self.assertRaisesRegex(ValueError, "Oversized"):
            module.stage(self.root)

    def test_reject_traversal_directory_zip_member(self):
        path = self.make_archive()
        with zipfile.ZipFile(path, "a") as z:
            z.writestr("../escaped/", "")
        with self.assertRaisesRegex(ValueError, "Unsafe"):
            module.stage(self.root)

    def test_reject_embedded_zip_symlink(self):
        path = self.make_archive()
        info = zipfile.ZipInfo("linked-file")
        info.create_system = 3
        info.external_attr = (0o120777 << 16)
        with zipfile.ZipFile(path, "a") as z:
            z.writestr(info, "../../outside")
        with self.assertRaisesRegex(ValueError, "Symlink member"):
            module.stage(self.root)

    def test_reject_symlinked_dist_directory(self):
        self.make_archive()
        external = self.root / "external-dist"
        external.mkdir()
        (self.root / "dist").symlink_to(external, target_is_directory=True)
        with self.assertRaisesRegex(ValueError, "symlinked dist"):
            module.stage(self.root)
        self.assertEqual(list(external.iterdir()), [])

    def test_reject_symlinked_release_output(self):
        self.make_archive()
        dist = self.root / "dist"
        dist.mkdir()
        outside = self.root / "outside.cs3"
        outside.write_bytes(b"untouched")
        (dist / "EA-FB.cs3").symlink_to(outside)
        with self.assertRaisesRegex(ValueError, "symlinked release outputs"):
            module.stage(self.root)
        self.assertEqual(outside.read_bytes(), b"untouched")

    def test_reject_symlinked_release_manifest(self):
        self.make_archive()
        dist = self.root / "dist"
        dist.mkdir()
        outside = self.root / "outside.json"
        outside.write_text("untouched")
        (dist / "plugins.json").symlink_to(outside)
        with self.assertRaisesRegex(ValueError, "symlinked release outputs"):
            module.stage(self.root)
        self.assertEqual(outside.read_text(), "untouched")

    def test_reject_multiple_extensions(self):
        self.make_archive()
        (self.root / "build" / "plugins.json").write_text(json.dumps([
            {"internalName": "EA-FB", "version": 6},
            {"internalName": "Other", "version": 1}
        ]))
        with self.assertRaisesRegex(ValueError, "exactly one"):
            module.stage(self.root)


if __name__ == "__main__":
    unittest.main()
