# Postest branding

The mark combines a complete white **P**, a periwinkle request arrow pointing
right, and a mint response arrow pointing left on a midnight tile.
The application and GitHub artwork share the source at
[`src/main/resources/icon.svg`](../../src/main/resources/icon.svg).

| Asset | Use |
| --- | --- |
| `../../src/main/resources/icon.svg` | Editable vector master |
| `../../src/main/resources/icon.png` | 1024 × 1024, transparent; application window, Linux, or GitHub avatar |
| `../../src/main/resources/icon.icns` | macOS icon, including Retina representations |
| `../../src/main/resources/icon.ico` | Windows icon, 16–256 px representations |
| `logo-light.svg` / `logo-light.png` | Transparent wordmark for light backgrounds |
| `logo-dark.svg` / `logo-dark.png` | Transparent wordmark for dark backgrounds |
| `github-social-preview.png` | 1280 × 640 image for the repository's social preview |
| `github-social-preview.svg` | Editable vector version of the social preview |

The repository README selects its logo according to the reader's color scheme.
To use the social image on GitHub, upload `github-social-preview.png` in the
repository's **Settings → General → Social preview**. Keeping the file in the
repository does not automatically change that setting.

## Regenerate

With Python 3 and `rsvg-convert` (librsvg) installed, run:

```sh
python3 scripts/regenerate_icon.py
```

The script works from any working directory, uses no network, and regenerates
all desktop icon containers and GitHub artwork. Edit the canonical SVG for mark
changes and the script for wordmark/layout changes. SVG wordmarks use an
Avenir Next/Avenir/Arial/sans-serif font stack; PNG exports preserve the
rendered text. The repository README uses PNG logos for consistent typography.
