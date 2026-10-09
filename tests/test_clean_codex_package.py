import importlib.util
import io
import json
from pathlib import Path
import tempfile
import unittest
import zipfile

spec = importlib.util.spec_from_file_location("clean_package", Path(__file__).parents[1] / "scripts/package-clean-codex.py")
package = importlib.util.module_from_spec(spec)
spec.loader.exec_module(package)


class DistributionTests(unittest.TestCase):
    def test_metadata_is_from_final_bytes_and_tampering_is_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            out = Path(tmp)
            buf = io.BytesIO()
            with zipfile.ZipFile(buf, "w") as z:
                z.writestr("manifest.json", json.dumps(dict(name=package.NAME, internalName=package.ID, version=66, pluginClassName="com.eafb.EAPlugin")))
            data = buf.getvalue()
            (out / package.FILE).write_bytes(data)
            (out / "plugins.json").write_text(json.dumps([package.entry(data, 66)]))
            manifest = json.loads(zipfile.ZipFile(io.BytesIO(data)).read("manifest.json"))
            package.verify_metadata(data, manifest, json.loads((out / "plugins.json").read_text()))
            modified = package.entry(data, 66)
            modified["fileSize"] -= 42
            (out / "plugins.json").write_text(json.dumps([modified]))
            with self.assertRaises(ValueError):
                package.verify_metadata(data, manifest, json.loads((out / "plugins.json").read_text()))

    def test_existing_providers_are_not_targeted(self):
        e = package.entry(b"test", 66)
        self.assertEqual(e["internalName"], "EA-FB-CODEX-CLEAN-20261009")
        self.assertIn("test/clean-codex-fix-20261009/dist-clean-codex-fix-20261009/", e["url"])
        self.assertNotIn("dist-v63-clean/", e["url"])

    def test_descriptor_text_is_not_enough_to_claim_compiled_classes(self):
        with self.assertRaises(ValueError):
            package.validate_compiled_scope(b"dex\nLcom/eafb/DiziBoxAdapter;")
