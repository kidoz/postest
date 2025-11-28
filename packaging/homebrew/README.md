# Homebrew Cask

## For Users

### Install from Tap (Recommended)

Once published to the tap at `github.com/kidoz/homebrew-postest`:

```bash
brew tap kidoz/postest
brew install --cask postest
```

### Install from Local Cask

```bash
brew install --cask ./postest.rb
```

### Uninstall

```bash
brew uninstall --cask postest

# Remove all data (optional)
brew uninstall --cask --zap postest
```

## For Maintainers

### Building the DMG

```bash
cd /path/to/postest
./gradlew packageDmg
```

The DMG will be at: `build/compose/binaries/main/dmg/Postest-1.0.0.dmg`

### Updating the Cask

1. Build new DMG and upload to GitHub Releases

2. Generate SHA256:
   ```bash
   shasum -a 256 build/compose/binaries/main/dmg/Postest-1.0.0.dmg
   ```

3. Update `postest.rb`:
   - Update `version`
   - Update `sha256`
   - Update download URL if needed

### Creating a Homebrew Tap

1. Create repository named `homebrew-postest`

2. Add cask to `Casks/postest.rb`

3. Users can then install with:
   ```bash
   brew tap kidoz/postest
   brew install --cask postest
   ```

### Submitting to Homebrew Cask (Official)

For wider distribution, submit to [homebrew-cask](https://github.com/Homebrew/homebrew-cask):

1. Fork `homebrew-cask`
2. Add `Casks/p/postest.rb`
3. Run `brew audit --cask postest`
4. Submit Pull Request

Requirements:
- Stable release on GitHub
- Valid SHA256 checksum
- App must be signed (for Gatekeeper)
