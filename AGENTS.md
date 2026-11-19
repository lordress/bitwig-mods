# Ressa's Bitwig Mods

This repository keeps application mods for Bitwig Studio. Each mod is distributed
as a ZIP that [Patchbay Mod Loader](https://github.com/shaboogen-dev/Patchbay)
installs and loads.

| Task | Start here |
| --- | --- |
| Develop or change a mod | [Patchbay SDK guide](https://github.com/shaboogen-dev/Patchbay/blob/main/docs/sdk/README.md) for the mod kit and frameworks. Use [shaboogen-dev/Mods](https://github.com/shaboogen-dev/Mods) for working examples and package layout. |
| Work on an existing mod | [RESS-SQ](ress-sq/README.md). The mod directory holds its own `mod.json`, `build.json` and `src/`. |
| Build a release ZIP | [Release guide](docs/releasing.md) and [local build script](tools/build_releases.py). |
| Publish a mod or add a fork as a launcher source | Follow the [shared packaging manifest](docs/patchbay-launcher.md#mods). |
| Install native devices or modulators | [Ressa's Bitwig Devices](https://github.com/lordress/bitwig-devices). Patchbay registers these through **Framework: Nitro Devices** (`custom-device-registrar`). Devices do not live in this repository. |

`docs/patchbay-launcher.md` is the shared manifest, copied from
[polarity/bitwig-mods](https://github.com/polarity/bitwig-mods); keep it in step
with upstream rather than editing the rules here.
