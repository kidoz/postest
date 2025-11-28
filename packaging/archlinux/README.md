# Arch Linux Package Build

## Prerequisites

Install build dependencies:

```bash
sudo pacman -S base-devel jdk21-openjdk
```

## Building from Local Source

1. Navigate to this directory:
   ```bash
   cd packaging/archlinux
   ```

2. Use the local PKGBUILD:
   ```bash
   cp PKGBUILD.local PKGBUILD
   ```

3. Build the package:
   ```bash
   makepkg -si
   ```

   Options:
   - `-s` - Install missing dependencies
   - `-i` - Install package after build
   - `-f` - Force rebuild if package exists

## Building from Release

1. Update `PKGBUILD`:
   - Set correct `url` and `source` URL
   - Generate checksum: `makepkg -g >> PKGBUILD`

2. Build:
   ```bash
   makepkg -si
   ```

## Package Contents

After installation:
- `/opt/postest/` - Application files
- `/usr/bin/postest` - Launcher script
- `/usr/share/applications/postest.desktop` - Desktop entry
- `/usr/share/icons/hicolor/512x512/apps/postest.png` - Application icon

## Uninstall

```bash
sudo pacman -R postest
```

## Validation

Check package for issues:
```bash
namcap PKGBUILD
namcap postest-1.0.0-1-x86_64.pkg.tar.zst
```
