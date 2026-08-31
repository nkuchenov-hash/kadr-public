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

## Distribution rule

A Windows installer and Android APK are published here only as a matched pair produced by the private **Integrated Release** workflow. The site Download buttons resolve to the latest public GitHub Release assets:

- `KADR-Setup.exe`
- `KADR-Mobile.apk`
- `SHA256SUMS.txt`

Old public releases remain versioned in GitHub Releases for traceability.

Public site: https://nkuchenov-hash.github.io/kadr-public/
