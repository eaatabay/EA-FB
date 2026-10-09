# LAND FastPlay – isolated experimental V65

Base release: exact V64 CLEAN commit 4730b9717a76c5810607e59b28600e274fb7b875.
Frozen full 1:1 snapshot: archive/frozen-v64-complete-20261009.
Frozen branch contains all 248 tracked files, including CS3, manifests, assets and workflows.

V65 is an isolated test source; V64 CLEAN, Blue V5, RED production and Cloudflare are untouched.
Bridge registers only DiziYou, DiziBox and **optional LAND**; NL is not registered.
LAND's V80 AJAX path now attempts SPG/XOR/HTTPS direct HLS from the supplied research report.
No loopback/LocalHlsServer and no HTTPS gate policy exception.
Dublaj/subtitle variant quality and full segment play require Mi Box confirmation.
Reference X-Sp formula is a hypothesis, not verified live. Never log nonce/sp/X-Sp/URLs.

Offline unit tests: bash scripts/test-land-codec.sh
Build: gradle :EA-FB:make
Isolated distribution: dist-v65-land-test/repo.json
No freezing of V65 before confirmed physical Mi Box playback.
