# Ressa's Bitwig Mods

## Getting started

- **Install manually.** Download a mod ZIP from [Releases](https://github.com/lordress/bitwig-mods/releases).
  In Patchbay, use **Mods → Add Mod…**, pick the ZIP, enable the mod and click
  **Load**. Do not unpack the ZIP: Patchbay reads the manifest inside it.
  [Download Patchbay](https://github.com/shaboogen-dev/Patchbay/releases).
- **Use the launcher.** Add this repository as a source in the
  [Patchbay Launcher](https://github.com/arius77R/patchbay-launcher/releases):
  **Sources → Mods → + Add → `lordress/bitwig-mods` → Check**. The launcher then
  installs and updates the mod ZIPs for you.

## What's in this repo

| Mod | What it adds |
| --- | --- |
| [Piano Roll: RESS-SQ](ress-sq/README.md) | A sixteenth-note step grid with a playhead, page following, randomise and clear tools, and per-step velocity, offset, length, repeat, pitch and chance. |

## Install

RESS-SQ needs **Framework: Piano Roll**, which ships with Patchbay, and Bitwig
Studio **6.1.7 on macOS**. Every Bitwig name inside it is 6.1.7's, so it is used
as written instead of being translated by a map pack; it will not load on another
Bitwig version, and no cross-platform ZIP is published for it.

In Patchbay: disable nothing, tick **Framework: Piano Roll** and **RESS-SQ**, then
click **Load**. If Polarity's Step Sequencer is also installed and ticked, both
mods put their own button below Drum/Hybrid — RESS-SQ's carries a different icon
and the tooltip **RESS-SQ Steps**.

## Building

Every mod builds from source with `tools/build_releases.py`, which compiles the
helper against your own Bitwig `bitwig.jar`, validates each hook with the BML
mod kit, generates the finished `mod.json`, and writes
`dist/<mod-id>-<version>.zip` with one top-level folder named for the mod id.
See [docs/releasing.md](docs/releasing.md).

## Publishing and forks

Follow the [shared Patchbay Launcher manifest](docs/patchbay-launcher.md#mods)
for ZIP names, release assets, and adding a fork as a source. The short version:
the manifest stays at `<mod-id>/mod.json` on the default branch, the ZIP is named
`<mod-id>-<version>.zip`, and it is attached to a GitHub Release rather than
committed.

## Devices

Native devices and modulators live in
[Ressa's Bitwig Devices](https://github.com/lordress/bitwig-devices), not here.
Patchbay loads them through **Framework: Nitro Devices** (`custom-device-registrar`);
they are `.bwdevice` files in per-version folders rather than mods.

## Licence

MIT for the mods' code and packaging. RESS-SQ keeps the original copyright notice
of Polarity's Step Sequencer in [ress-sq/package/LICENSE](ress-sq/package/LICENSE).
Bitwig Studio and Patchbay are their owners' products; neither is included.
