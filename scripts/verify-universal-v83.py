#!/usr/bin/env python3
"""Offline source/package regression guard, never a device loading requirement."""
from pathlib import Path
import hashlib
import subprocess
BASE = "16396702470eb1f81cc6dac4360e6b0ff84927d1"
APPROVED = {'EA-FB/build.gradle.kts': '9c224f0743ecad5b03697fbf3663d24b38b3647d862651f33ed8b9bc119a0916', 'EA-FB/src/main/kotlin/com/eafb/BronzeApiAdapter.kt': 'e9c5b36d3194cb799f0ee479d3e6c1c4d736faf6a8b2f126e57bc7eca42b75b8', 'EA-FB/src/main/kotlin/com/eafb/CleanTestIdentity.kt': 'a6edbedef61b53db5ad69a54bbc7708e66cca843f715f7c22f2bf92abcbd5821', 'EA-FB/src/main/kotlin/com/eafb/DiziBoxAdapter.kt': 'c7874265296463783d1947f358dd2dec3241f01a2e3c7e85127802b60ff02c20', 'EA-FB/src/main/kotlin/com/eafb/DiziYouAdapter.kt': 'bbf48ba25ed3168f2f9cc20f21acfc61c4292234415c9f6ed7f9b9e3d41eb08e', 'EA-FB/src/main/kotlin/com/eafb/EAProvider.kt': '15b37606a6baf8852f5b8ac43342f15fe8169979bb3b2535aecb294523d541e3', 'EA-FB/src/main/kotlin/com/eafb/PlaybackLinkBridge.kt': '73374a3c6853322c6d084d9dc193da7019ce5f8198a5f5eb3e25a6a602be328c', 'EA-FB/src/main/kotlin/com/eafb/PlaybackQuery.kt': 'da6c76563c2c810de75c824d4aac6938ebce7b0cf3b7a60c3fab272bf9eb9f9e', 'EA-FB/src/main/kotlin/com/eafb/SourceEngine.kt': '55da10bf934e5a678013f260540b7d4dd79783264e13aae8658c8636cdfa6d0a', 'EA-FB/src/main/kotlin/com/eafb/SourceSearchTitles.kt': 'd055f01dea326382c1a1db26532008dbab6d5cc9cb37135b32b701de7eeb3eab', 'core-tests/clean/BronzeBridgeTest.kt': '30b21ad2969f4d90e294565c2e57632bdc855d64f52dac7e12fee34a831452c8', 'core-tests/clean/BronzeFilmSearchTest.kt': 'e601a89b3a6c1987405a5a1798a67437643582a59c8bb96c6fd2bc4c5695f000', 'core-tests/clean/CleanAdaptersTest.kt': '75285c9969edbbd7d55dc9955c6f04d23838187c05f92671348a0fdf0b5ac8dd', 'scripts/test-bronze-nl-land.sh': '419f7381b274ef7eeb66ed6d76aae4cbf78aebc81631edd7a7719fd2342d7f30'}
NEW_RUNTIME = {'EA-FB/src/main/kotlin/com/eafb/SourceTitleMatcher.kt': 'd72cee1d59e6227e9cd8af71250ebf32ef710043b830588b610c51ead7243c33', 'EA-FB/src/main/kotlin/com/eafb/SourcePageIdentity.kt': '596c62ff835e06d82a0be1a70271782e31c5d8a2f4102b776c5cb425581e0ef5', 'EA-FB/src/main/kotlin/com/eafb/CatalogMatchMetadata.kt': 'a631ef38e28a38f6cd7fd517435a0a02e1c4f2cf65c82a82da7cf8a2acb7b00e'}
ALLOWED_NEW = {'core-tests/clean/UniversalAdaptersTest.kt', 'tests/test_universal_guard.py', 'scripts/verify-universal-v83.py', 'scripts/test-universal-matcher.sh', 'scripts/package-universal.py', 'dist-universal-8601-test/SOURCE-LICENSE', '.github/workflows/universal-matcher.yml', 'tests/test_universal_package.py', 'dist-universal-8601-test/repo.json', 'core-tests/clean/UniversalLiveReplayTest.kt', 'dist-universal-8601-test/SOURCE-NOTICE.txt', 'core-tests/clean/UniversalMatcherTest.kt', 'dist-universal-8601-test/build-info.json', 'EA-FB/src/main/kotlin/com/eafb/SourceTitleMatcher.kt', 'dist-universal-8601-test/EA-FB-UNIVERSAL-8601-TEST.cs3', 'EA-FB/src/main/kotlin/com/eafb/CatalogMatchMetadata.kt', 'scripts/compare-universal-v83.sh', 'dist-universal-8601-test/plugins.json', 'EA-FB/src/main/kotlin/com/eafb/SourcePageIdentity.kt', 'tests/test_universal_generality.py'}
REGIONS = {
 'EA-FB/src/main/kotlin/com/eafb/BronzeApiAdapter.kt': ('    override suspend fun search(query:', '    override suspend fun resolveIncrementally('),
 'EA-FB/src/main/kotlin/com/eafb/DiziBoxAdapter.kt': ('    override suspend fun search(query:', '    private suspend fun searchHtml('),
 'EA-FB/src/main/kotlin/com/eafb/DiziYouAdapter.kt': ('    override suspend fun search(query:', '    private suspend fun playlistInfo('),
 'EA-FB/src/main/kotlin/com/eafb/EAProvider.kt': ('            val metadata = getJson("/$kind/$id")', '            var emittedLinks = 0'),
}
def outside(data, markers):
 start,end=[m.encode() for m in markers];a=data.index(start);b=data.index(end,a)
 return data[:a]+data[b:]
def verify(root, read_file=None):
 root=Path(root);read_file=read_file or (lambda p:(root/p).read_bytes())
 def git(*a):return subprocess.check_output(['git',*a],cwd=root)
 paths=git('ls-tree','-r','--name-only',BASE).decode().splitlines()
 for p in paths:
  actual=read_file(p);old=git('show',BASE+':'+p)
  if p in APPROVED:
   if hashlib.sha256(actual).hexdigest()!=APPROVED[p]:raise ValueError('Protected V83 file changed: '+p)
   if p in REGIONS and outside(actual,REGIONS[p])!=outside(old,REGIONS[p]):raise ValueError('Outside approved matching/metadata region: '+p)
  elif actual!=old:raise ValueError('Protected V83 file changed: '+p)
 for p,digest in NEW_RUNTIME.items():
  if hashlib.sha256(read_file(p)).hexdigest()!=digest:raise ValueError('Unreviewed matcher source: '+p)
 changed=set(git('diff','--name-only',BASE).decode().splitlines()) | set(git('ls-files','--others','--exclude-standard').decode().splitlines())
 unknown={p for p in changed if p not in APPROVED and p not in ALLOWED_NEW and not p.startswith('docs/universal-matcher/')}
 if unknown:raise ValueError('Unexpected path: '+str(sorted(unknown)))
 v83=hashlib.sha256(read_file('dist-v83-test/EA-FB-V83-TEST.cs3')).hexdigest()
 if v83!='40fd24acb41dc69fa869ceb69ee6661e5136c1dff39434eb8bd3227f955f3b66':raise ValueError('V83 package changed')
 print(f'PASS: {len(paths)-len(APPROVED)} V83 files byte-identical; {len(APPROVED)} exact reviewed hashes; resolver/UI source regions preserved; V83 SHA256 {v83}')
 return len(paths)
if __name__=='__main__':verify(Path(__file__).resolve().parents[1])
