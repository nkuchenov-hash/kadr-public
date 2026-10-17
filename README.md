# KADR Public

This repository is the canonical public distribution layer for KADR / КАДР.

## What belongs here

- Public website and documentation
- Public release history
- Versioned Windows installer release assets
- Versioned Android Companion APK release assets
- Public checksums / build metadata

## What does not belong here

The private KADR application source, signing material, internal architecture, private CI implementation, credentials, and development-only files remain in the private `nkuchenov-hash/KADR` repository.

## Current link-provenance caveat (2026-10-17)

The live source `index.html` currently references **separate** `windows-latest` and `android-latest` release tags. This does not by itself verify that the installers are a compatible matched pair. The matched-pair release policy below remains the *intended requirement* and must be proven from release metadata and the private integrated workflow before compatibility is promised. The site also exposes a separate `macos-preview` download through `assets/site.js`. See `docs/DOCUMENTATION_PARITY_2026-10-17.md`.

## Distribution rule

A Windows installer and Android APK are published here only as a matched pair produced by the private **Integrated Release** workflow. The site Download buttons resolve to the latest public GitHub Release assets:

- `KADR-Setup.exe`
- `KADR-Mobile.apk`
- `SHA256SUMS.txt`

Old public releases remain versioned in GitHub Releases for traceability.

Public site: https://nkuchenov-hash.github.io/kadr-public/
