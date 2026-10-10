"""Distribution contract tests independent from Android/device playback."""
import importlib.util
from pathlib import Path
import sys
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'scripts'))
spec = importlib.util.spec_from_file_location('v83_package', ROOT / 'scripts/package-v83.py')
package = importlib.util.module_from_spec(spec)
spec.loader.exec_module(package)


class DistributionTests(unittest.TestCase):
    def setUp(self):
        self.data = b'independent-final-byte-fixture'
        self.manifest = dict(version=83, internalName=package.ID, name=package.NAME, pluginClassName='com.eafb.EAPlugin')
        self.feed = [package.entry(self.data, 83)]

    def test_matching_identity_and_bytes(self):
        package.verify_metadata(self.data, self.manifest, self.feed)
        self.assertIn('/test/ea-fb-v83-bronze-search-fix/', self.feed[0]['url'])
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

    def test_rejects_consistent_but_wrong_manifest_version(self):
        manifest = dict(self.manifest, version=82)
        with self.assertRaises(ValueError):
            package.verify_metadata(self.data, manifest, [package.entry(self.data, 82)])

    def test_protected_sources_and_existing_distributions(self):
        package.verify_scope(ROOT)

    def test_scope_rejects_dizibox_or_diziyou_change_without_modifying_files(self):
        original_read = Path.read_bytes
        for name in ['DiziBoxAdapter.kt', 'DiziYouAdapter.kt']:
            def changed(path, target=name):
                data = original_read(path)
                return data + b'\n// forbidden alteration\n' if path.name == target else data
            with patch.object(Path, 'read_bytes', changed):
                with self.assertRaisesRegex(ValueError, 'Protected source/runtime changed'):
                    package.verify_scope(ROOT)


if __name__ == '__main__':
    unittest.main()
