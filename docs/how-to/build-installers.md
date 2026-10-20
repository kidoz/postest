# Build installers

Installers are produced by the Compose Desktop packaging tasks. Build each target on its own operating system: jpackage does not cross-compile.

## macOS

```bash
./gradlew packageDmg
```

Output:

```
build/compose/binaries/main/dmg/Postest-<version>.dmg
```

You can also use `./packaging/macos/build_dmg.sh`.

## Windows

```bash
./gradlew packageMsi
```

Output:

```
build/compose/binaries/main/msi/Postest-<version>.msi
```

## Linux

```bash
./gradlew packageDeb
```

Output:

```
build/compose/binaries/main/deb/postest_<version>_amd64.deb
```

See `packaging/archlinux/` for Arch Linux packaging and `packaging/homebrew/` for the Homebrew cask.

## Local application directory

To build an unpacked application directory without an installer:

```bash
./gradlew createDistributable
```

Output:

```
build/compose/binaries/main/app/Postest.app     # macOS
```

To build the installer for whichever OS you are on:

```bash
./gradlew packageDistributionForCurrentOS
```

## Version

The installer name and bundle version come from `version` and `packageVersion` in `build.gradle.kts`. Both must match the release version.

## Signing and notarization

The current packaging configuration does **not** sign or notarize macOS builds. Gatekeeper may warn users when they open the DMG. Signing and notarization must be added to the build pipeline before distributing broadly.

## Related

- [Build and run from source](build-and-run.md)
- [Release a new version](release.md)
- [macOS packaging notes](../../packaging/macos/README.md)
- [Homebrew packaging notes](../../packaging/homebrew/README.md)
