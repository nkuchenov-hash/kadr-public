# KADR public site — release/docs parity (2026-10-17)

**Reviewed:** `main` at `37c3e89745`, `README.md`, `index.html`, `assets/site.js`, `RELEASE_STYLE.md` and `.github/workflows/pages.yml`. No live release file, checksum, signing, website HTTP link or GitHub Actions execution was checked.

## Confirmed actual public behavior
- `index.html` links Windows to release tag `windows-latest/KADR-Setup.exe` and Android to `android-latest/KADR-Mobile.apk`: **two separate latest tags**, not intrinsically a matched release pair.
- `assets/site.js` inserts a separate optional macOS preview download `macos-preview/KADR-macOS.dmg`, depending on UI availability, and handles localization.
- `README.md` currently claims Windows and Android are published *only as a matched pair* from one private Integrated Release. That guarantee is **not proven by the currently separate public download URLs**; it may depend on external private workflow discipline. Verify actual source commit/protocol pair metadata before continuing to claim that every latest tag is compatible.
- `RELEASE_STYLE.md` requires user-focused bilingual release stories; preserve it.
- `.github/workflows/pages.yml` deploys the public site on `main`; source code, signing material and private infrastructure remain in the private KADR repo.

## Safe action
Reconcile README claims against current workflow/release metadata and actual downloadable assets. Keep the public site and historic releases intact. Do not modify download targets, hashes, signing or `main` in a documentation-only pass. If a synchronized pair cannot be independently established, describe the release tag relationship accurately and gate 'matched pair' claims on build provenance.

No release was downloaded, changed or published.
