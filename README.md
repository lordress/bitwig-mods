# Ressa's Bitwig Mods

Mods for Bitwig Studio, installed through [Patchbay Mod Loader](https://github.com/shaboogen-dev/Patchbay).

| Mod | What it is |
| --- | --- |
| [Piano Roll: RESS-SQ](ress-sq/README.md) | a sixteenth-note step grid for the Detail Editor, with per-row mute, solo, fold and names |

Everything below describes RESS-SQ.

---

# RESS-SQ

A step sequencer for Bitwig Studio's piano roll. Open a MIDI clip in the Detail
Editor and click **Show Steps** below Drum/Hybrid: the grid replaces the native
note content until you click it again or switch to Piano or Drums.

Sixteen sixteenth-note steps per page, one row per pitch or drum pad, and each
gesture one Undo step.

## Steps

- **Draw.** Click a cell to add or remove a note. Start on an empty cell and drag
  across the grid to draw several notes at once.
- **Edit.** Pick **Velocity**, **Offset**, **Length**, **Repeat**, **Pitch** or
  **Chance** in the footer, then drag an occupied cell vertically. Escape cancels
  the gesture mid-drag.
- **Erase.** Right-drag or Shift-click-drag; a red outline previews every step
  that will go, and release removes them in one Undo step.
- **Pages and scrolling.** The header arrows change page; **▲** and **▼** in the
  footer scroll the pitch rows a page at a time.
- **Playhead.** The current column is outlined, and its set steps with it. **FLW**
  turns the page as the transport plays, so a long clip follows along.
- **What the cells show.** The bright fill is velocity, short vertical marks are
  repeats, a thin line marks a held note in a later cell, and a number marks
  several notes starting in the same cell. Those ambiguous cells are protected
  from clicks that would delete or cut a note by accident.

## Per-row controls

Each row has its own controls in the label column:

| Control | What it does |
| --- | --- |
| **N** | Rename the row: Kick, Snare, Clap, Hat, Open Hat, Rim, Tom, Ride, Crash, Perc, Bass, Chord, or **Clear** to go back to Bitwig's own pad name. The row then reads `B2 Kick`, keeping the pitch in front. |
| **mark** | Marks the row. **FOLD**, before **Velocity**, hides every unmarked row — useful to keep only the pads you are working on. |
| **o** | Marks the row for **RND** and **CLR**. With no row marked they work on every row shown. |
| **M** | Mutes the pitch. Its notes go to velocity 0 in one Undo step and keep the velocity they had, so unmuting restores it. Nothing is deleted. |
| **S** | Solos the pitch: while any row is soloed, every other row is muted the same way. |

Muted rows draw dimmed and drop out of **RND** and **CLR**. Marks, names, mutes,
solos and the **o** scope belong to the clip — switch to another clip and come
back and they are as you left them, and they survive quitting Bitwig.

## Tools

- **Grid** — the header has two toggles: `16 st` / `32 st` for how many steps a page
  shows, and `1/16` / `1/8` / `1/32` for what one step is worth. A new note is a
  sixteenth long by default. Both choices are remembered.
- **FOLD** — hides every row without a mark; it is remembered per clip, and with it on
  painting only writes to marked keys, so folded rows stay empty.
- **GEN** — clears the rows marked with **o** on this page and fills each with a fresh random pattern: a random number of steps, in random columns, one Undo step.
- **RND** — replaces the velocity of every step in scope with a random value
  between 40% and 100%, in one Undo step.
- **CLR** — removes every step in scope on the page, in one Undo step. Cells it
  draws as protected are left alone.
- **MIDI key** — the footer shows the pitch you are playing, from Bitwig's own
  MIDI input, both from a controller and from the computer keyboard.

## Install

1. Download [`ress-sq-0.8.0.zip`](https://github.com/lordress/bitwig-mods/releases).
2. In Patchbay: **Mods → Add Mod…** and pick the ZIP. Do not unpack it.
3. Tick **Framework: Piano Roll** and **RESS-SQ**, then click **Load**.

RESS-SQ needs `piano-roll-framework` 0.2.0-dev.1 or newer, which ships with
Patchbay, and **Bitwig Studio 6.1.7 on macOS**. Every Bitwig name inside it is
6.1.7's, so there is no cross-platform ZIP and it will not load on another
version.

It runs beside [Polarity's Step Sequencer](https://github.com/polarity/bitwig-mods/tree/main/step-sequencer):
both add a button below Drum/Hybrid, RESS-SQ's with its own icon and the tooltip
**RESS-SQ Steps**.

## Build

The source is in [`ress-sq/`](ress-sq/), with the manifest at
[`ress-sq/mod.json`](ress-sq/mod.json) — the file Patchbay Launcher reads for
catalog metadata. From the repository root, with JDK 25 and Bitwig 6.1.7 installed:

```sh
python3 tools/build_releases.py \
  --bitwig-bin "/Applications/Bitwig Studio.app/Contents/Java" \
  --modkit "<BML>/modkit" \
  --framework "<BML>/mods/piano-roll-framework/helper.jar" \
  --jdk-bin "<jdk-25>/Contents/Home/bin"
```

It compiles the helper against Bitwig's own jar, validates every hook with the BML
mod kit, generates the finished `mod.json` from the hooks it reports, and writes
`dist/ress-sq-<version>.zip`. See [docs/releasing.md](docs/releasing.md).

## Thank you, Polarity

RESS-SQ is a fork of **[Polarity's Step Sequencer](https://github.com/polarity/bitwig-mods/tree/main/step-sequencer)**,
which is the whole reason this exists: the grid, the interaction model, the page
handling and most of the code are his. RESS-SQ adds the per-row controls, the
tools, the MIDI readout and the port to Bitwig 6.1.7's names.

If you want a step sequencer, look at
[his mods](https://github.com/polarity/bitwig-mods) first — there is Scale Fold,
Note Intervals, Chord Tools and more, and they cover far more Bitwig versions
than this fork does.

## Devices

Native devices and modulators live in
[Ressa's Bitwig Devices](https://github.com/lordress/bitwig-devices), not here.

## Licence

MIT. The original copyright notice is kept in
[ress-sq/package/LICENSE](ress-sq/package/LICENSE). Bitwig Studio and Patchbay are
their owners' products; neither is included.
