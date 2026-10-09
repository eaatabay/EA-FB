"""Distribution contract tests independent from Android/device playback."""
import importlib.util
from pathlib import Path
import sys
import unittest

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'scripts'))
spec = importlib.util.spec_from_file_location('bronze_package', ROOT / 'scripts/package-bronze-nl-land.py')
package = importlib.util.module_from_spec(spec)
spec.loader.exec_module(package)


class DistributionTests(unittest.TestCase):
    def setUp(self):
        self.data = b'independent-final-byte-fixture'
        self.manifest = dict(version=81, internalName=package.ID, name=package.NAME, pluginClassName='com.eafb.EAPlugin')
        self.feed = [package.entry(self.data, 81)]

    def test_matching_identity_and_bytes(self):
        package.verify_metadata(self.data, self.manifest, self.feed)
        self.assertIn('/test/clean-codex-bronze-nl-land-20261009/', self.feed[0]['url'])
        self.assertNotIn('/dist-clean-codex-fix-20261009/', self.feed[0]['url'])

    def test_rejects_changed_bytes_or_size(self):
        with self.assertRaises(ValueError):
            package.verify_metadata(self.data + b'x', self.manifest, self.feed)
        self.feed[0]['fileSize'] += 1
        with self.assertRaises(ValueError):
            package.verify_metadata(self.data, self.manifest, self.feed)

    def test_rejects_v66_identity_and_other_provider(self):
        for key, value in [('internalName', 'EA-FB-CODEX-CLEAN-20261009'), ('pluginClassName', 'com.keyiflerolsun.HDFilmCehennemiPlugin')]:
            manifest = dict(self.manifest, **{key: value})
            with self.assertRaises(ValueError):
                package.verify_metadata(self.data, manifest, self.feed)

    def test_rejects_wrong_update_version_and_url(self):
        for key, value in [('version', 66), ('url', 'https://example.com/old.cs3')]:
            feed = [dict(self.feed[0], **{key: value})]
            with self.assertRaises(ValueError):
                package.verify_metadata(self.data, self.manifest, feed)


if __name__ == '__main__':
    unittest.main()
