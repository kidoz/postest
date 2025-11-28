# macOS DMG Build

This project ships a native DMG via Compose Desktop. Build on macOS so codesigning/notarization can be added later if needed. Release artifacts live at https://github.com/kidoz/postest/releases when published.

## Build

```bash
./packaging/macos/build_dmg.sh
```

This runs `./gradlew packageDmg` and leaves the artifact at:

- `build/compose/binaries/main/dmg/Postest-1.0.0.dmg`

## Notes

- Run on macOS; the Gradle task targets the host OS.
- For distribution, add signing/notarization as needed (not included here).
