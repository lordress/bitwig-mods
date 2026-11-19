# Bitwig Studio 6.1.7 and later

This folder contains the installer for Bitwig Studio 6.1.7 on macOS.

| Name | Type |
| --- | --- |
| [RESS-SQ](ress-sq-0.1.1.zip) | Mod (Piano Roll: step sequencer) |

RESS-SQ is a Patchbay mod, not a native device: import the ZIP with
**Mods → Add Mod…**, tick **Framework: Piano Roll** and **RESS-SQ**, then
**Load**. Do not unpack the ZIP.

Its `mod.json` declares `built_against` Bitwig 6.1.7 on macOS, and every Bitwig
name inside the helper is 6.1.7's, so the mod is used as written rather than
translated by a map pack. It will not load on another Bitwig version.
