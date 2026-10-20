import importlib.util
from pathlib import Path
import unittest
ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('universal_guard',ROOT/'scripts/verify-universal-v83.py')
guard=importlib.util.module_from_spec(spec);spec.loader.exec_module(guard)
class GuardTests(unittest.TestCase):
 def test_reviewed_source(self):self.assertGreater(guard.verify(ROOT),100)
 def test_simulated_ui_settings_resolver_workflow_package_mutations_fail(self):
  for target in ['EA-FB/src/main/kotlin/com/eafb/DetailMetaRow.kt','EA-FB/src/main/kotlin/com/eafb/EASettings.kt',
    'EA-FB/src/main/kotlin/com/eafb/DiziBoxAdapter.kt','EA-FB/src/main/kotlin/com/eafb/DiziYouAdapter.kt',
    'EA-FB/src/main/kotlin/com/eafb/EAPlugin.kt','.github/workflows/v83-test.yml','dist-v83-test/EA-FB-V83-TEST.cs3']:
   with self.subTest(path=target),self.assertRaises(ValueError):
    guard.verify(ROOT,lambda p:(ROOT/p).read_bytes()+(b'mutation' if p==target else b''))
