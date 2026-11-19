# Bitwig mods

Mods for Bitwig Studio, installed through Patchbay Mod Loader.

| Mod | What it is |
| --- | --- |
| [RESS-SQ](Bitwig-6.1.7/ress-sq-0.1.1.zip) | sixteenth-note step grid for the Piano Roll |

Everything below describes RESS-SQ.

---

# RESS-SQ

A step sequencer for the Piano Roll: a sixteenth-note grid that draws steps and
edits their velocity, offset, length, repeat, pitch and chance, each gesture one
Undo step. It is a fork of
[Polarity's Step Sequencer](https://github.com/polarity/bitwig-mods/tree/main/step-sequencer)
(MIT), ported to Bitwig 6.1.7's internal names.

Open a MIDI clip in the Detail Editor and click **Show Steps** below Drum/Hybrid.
The grid replaces the native note content until you click it again or switch to
Piano or Drums.

## What it does

- **Draw steps.** Click a cell to add or remove a note. Start on an empty cell
  and drag across the grid to draw several notes in one undo step.
- **Edit a step.** Pick **Velocity**, **Offset**, **Length**, **Repeat**,
  **Pitch** or **Chance** in the footer, then drag an occupied cell vertically.
  Escape cancels the gesture.
- **Pages.** 16 sixteenth-note steps per page. The header arrows change pages;
  the footer arrows scroll the pitch rows.
- **Erase.** Right-drag or Shift-click-drag across the grid. The red outline
  previews what will go, and release erases it in one undo step.
- **Outline and marks.** The playhead outlines its set steps. The bright fill is
  velocity. A thin line marks a held note in a later cell. A number marks several
  notes starting in the same cell, and those cells are protected from clicks that
  would delete ambiguously.

## What RESS-SQ adds

- **FLW — follow the playhead.** With it on, the grid turns to the page the
  transport is playing, so a long clip follows along. On by default; click to
  turn it off when you want to browse pages while it plays.
- **RND — randomise.** Replaces the velocity of every step on the current page
  with a random value between 40% and 100%, in one undo step.
- **CLR — clear.** Removes every step the grid shows on the current page in one
  undo step. Cells it draws as protected are left alone.
- **Its own look.** Violet steps, an amber playhead, a RESS-SQ header, and its own
  button — three staggered bars, tooltip **RESS-SQ Steps** — so it is obvious
  which grid is up.
- **Runs beside the original.** Both mods add a button below Drum/Hybrid. RESS-SQ
  recognises when the other mod is enabled, lets it build its button, and puts its
  own next to it. Clicking either one shows that mod's grid; they take turns
  rather than stacking.

## Install with Patchbay

1. Download [`Bitwig-6.1.7/ress-sq-0.1.1.zip`](Bitwig-6.1.7/ress-sq-0.1.1.zip).
2. In Patchbay: **Mods → Add Mod…** and pick the ZIP. Do not unpack it.
3. Tick **Framework: Piano Roll** and **RESS-SQ**, then click **Load**.

RESS-SQ needs `piano-roll-framework` 0.2.0-dev.1 or newer. It is built against
Bitwig **6.1.7 on macOS** only: every Bitwig name inside it is 6.1.7's, so it will
not load on another version.

## Build from source

The source is in [`ress-sq/`](ress-sq/), with the manifest at
[`ress-sq/mod.json`](ress-sq/mod.json) — the file Patchbay Launcher reads for
catalog metadata — and the build steps in [ress-sq/README.md](ress-sq/README.md).
`ress-sq/build_manifest.py` regenerates `mod.json` from the mod kit's
`hooks.json`, so the manifest always matches the compiled helper.

## Licence

MIT for this mod's code and packaging. The original copyright notice is kept in
[ress-sq/LICENSE](ress-sq/LICENSE). Bitwig Studio and Patchbay are their owners'
products; neither is included.
