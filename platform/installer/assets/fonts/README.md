# Installer font

Unmodified **Noto Sans CJK SC Regular 2.004**, licensed under the SIL Open Font License 1.1. The copyright notice and license are in [OFL.txt](OFL.txt), copied into every installer JAR alongside the font.

- [Upstream release](https://github.com/notofonts/noto-cjk/releases/tag/Sans2.004)
- [Font source](https://github.com/notofonts/noto-cjk/blob/Sans2.004/Sans/OTF/SimplifiedChinese/NotoSansCJKsc-Regular.otf)
- SHA-256: `2c76254f6fc379fddfce0a7e84fb5385bb135d3e399294f6eeb6680d0365b74b`

The wizard loads this font directly, including Swing default controls. No font installation, network access or system language pack is required. Keep the digest in `InstallerFonts` and `build.mjs` synchronized when intentionally updating the upstream font.
