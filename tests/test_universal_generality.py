"""Work names are fixture data only; runtime matching must not contain work/ID exceptions."""
import re
from pathlib import Path
import unittest
ROOT=Path(__file__).resolve().parents[1]
class GeneralityTests(unittest.TestCase):
    def test_no_work_literals_or_fixed_tmdb_conditions(self):
        paths=[ROOT/'EA-FB/src/main/kotlin/com/eafb'/n for n in [
            'SourceTitleMatcher.kt','SourcePageIdentity.kt','CatalogMatchMetadata.kt','SourceSearchTitles.kt',
            'SourceEngine.kt','BronzeApiAdapter.kt','DiziBoxAdapter.kt','DiziYouAdapter.kt']]
        for p in paths:
            text=p.read_text()
            literals=re.findall(r'"(?:[^"\\]|\\.)*"',text)
            for name in ['Organize İşler','Karun Hazinesi','Ted Lasso','Sazan Sarmalı','Spider-Man','Unknown Observatory']:
                self.assertFalse(any(name.lower() in s.lower() for s in literals),(p.name,name))
            self.assertNotRegex(text,r'\b(?:320754|97546|315635|30634|577990)\b')
            self.assertNotRegex(text,r'\.tmdbId\s*(?:==|!=|in)\s*\d')
            self.assertNotIn('ConfirmedSeriesAlias',text)
    def test_base_and_branch_are_from_v83(self):
        import subprocess
        base='16396702470eb1f81cc6dac4360e6b0ff84927d1'
        subprocess.run(['git','merge-base','--is-ancestor',base,'HEAD'],cwd=ROOT,check=True)
        # The prior special-case implementation branch must not be an ancestor.
        result=subprocess.run(['git','merge-base','--is-ancestor','8251abf3fa3994f6cefb908ea7b27e5b86d7e785','HEAD'],cwd=ROOT)
        self.assertEqual(result.returncode,1)
