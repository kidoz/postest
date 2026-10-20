# Release a new version

This is the maintainer checklist for a release. The example uses version `1.1.0`.

## 1. Update the version

Update every version location:

- `build.gradle.kts` - `version` and `packageVersion`
- `src/main/kotlin/su/kidoz/postest/util/AppVersion.kt` - `CURRENT`
- `README.md` - installer example paths
- `packaging/archlinux/PKGBUILD` and `PKGBUILD.local`
- `packaging/homebrew/postest.rb`
- packaging README examples under `packaging/`

## 2. Update the changelog

Add a new section to `CHANGELOG.md` at the top, using:

```markdown
## [1.1.0] - 2026-10-20
```

Include highlights, downloads, system requirements, changes grouped by area, security notes, and known limitations. Update the comparison link at the bottom.

## 3. Verify

```bash
./gradlew build ktlintCheck detekt test
```

Confirm:

- the About window and update checker use the new version;
- the packaged app reports the new bundle version;
- no stale "Remove from Folder" or other removed UI remains in the artifact.

## 4. Build release artifacts

Build on each target OS:

```bash
./gradlew packageDmg   # macOS
./gradlew packageDeb   # Linux
./gradlew packageMsi   # Windows
```

The CI workflow (`.github/workflows/build.yml`) runs lint and tests, but does not build or upload installers.

## 5. Update the Homebrew cask

Compute the checksum of the exact DMG that will be published:

```bash
shasum -a 256 build/compose/binaries/main/dmg/Postest-1.1.0.dmg
```

Set `version` and `sha256` in `packaging/homebrew/postest.rb`. If the DMG is rebuilt, recompute the checksum.

## 6. Commit, tag, and publish

1. Commit the release changes on `main`.
2. Create an annotated tag:

   ```bash
   git tag -a v1.1.0 -m "v1.1.0"
   ```

3. Push the branch and tag:

   ```bash
   git push origin main
   git push origin v1.1.0
   ```

4. Create a GitHub Release for the tag. Use the matching `CHANGELOG.md` section as the release body.
5. Upload the DMG, DEB, and MSI.
6. Copy the updated cask to the `homebrew-postest` tap.

## 7. Post-release checks

- Download each artifact and confirm it launches.
- Confirm the update checker reports the new release.
- Verify the Homebrew install points at the published DMG and checksum.

## Related

- [Build installers](build-installers.md)
- [Changelog](../../CHANGELOG.md)
- [Packaging notes](../../packaging/macos/README.md)
