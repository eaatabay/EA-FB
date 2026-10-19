import importlib.util
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("series_guard", ROOT / "scripts/verify-series-v83-baseline.py")
guard = importlib.util.module_from_spec(spec)
spec.loader.exec_module(guard)

class V83BaselineProtectionTests(unittest.TestCase):
    def test_actual_baseline_is_unchanged(self):
        self.assertGreater(guard.verify(ROOT), 100)

    def test_source_mutations_are_rejected_without_editing_real_files(self):
        for target in [
            "EA-FB/src/main/kotlin/com/eafb/EAProvider.kt",
            "EA-FB/src/main/kotlin/com/eafb/DetailMetaRow.kt",
            "EA-FB/src/main/kotlin/com/eafb/EASettings.kt",
            "EA-FB/src/main/kotlin/com/eafb/DiziBoxAdapter.kt",
            "EA-FB/src/main/kotlin/com/eafb/DiziYouAdapter.kt",
            "EA-FB/src/main/kotlin/com/eafb/BronzeApiAdapter.kt",
            ".github/workflows/v83-test.yml",
            "dist-v83-test/plugins.json",
            guard.PACKAGE,
        ]:
            with self.subTest(path=target):
                def read(path):
                    data = (ROOT / path).read_bytes()
                    return data + b"mutation" if path == target else data
                with self.assertRaisesRegex(ValueError, "Protected V83 tracked file changed"):
                    guard.verify(ROOT, read_file=read)

if __name__ == "__main__":
    unittest.main()
