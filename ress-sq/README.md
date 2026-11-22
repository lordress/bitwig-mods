# RESS-SQ

A sixteenth-note step sequencer for Bitwig Studio's piano roll, built for Bitwig
**6.1.7 on macOS**.

Open a MIDI clip in the Detail Editor and click **Show Steps** below Drum/Hybrid.
The grid replaces the native note content until you click it again or switch to
Piano or Drums. One row per pitch or drum pad, sixteen steps per page, and every
gesture is one Undo step.

## Steps

- **Draw.** Click a cell to add or remove a note, or start on an empty cell and
  drag across the grid to draw several at once.
- **Edit.** Choose **Velocity**, **Offset**, **Length**, **Repeat**, **Pitch** or
  **Chance** in the footer, then drag an occupied cell vertically. Escape cancels.
- **Erase.** Right-drag or Shift-click-drag; a red outline previews every step
  that will go and release removes them in one Undo step.
- **Pages.** The header arrows change page, **▲** and **▼** scroll the pitch rows.
  **FLW** turns the page with the playhead while the transport plays.
- **Cells.** The bright fill is velocity, vertical marks are repeats, a thin line
  marks a held note in a later cell, and a number marks several notes starting in
  the same cell. Those cells are protected from clicks that would cut a note.

## Per-row controls

| Control | What it does |
| --- | --- |
| **N** | Rename: Kick, Snare, Clap, Hat, Open Hat, Rim, Tom, Ride, Crash, Perc, Bass, Chord, or **Clear**. The row reads `B2 Kick`, keeping the pitch in front. |
| **mark** | Marks the row; **FOLD** hides every unmarked row. |
| **o** | Marks the row for **RND** and **CLR**; with none marked they cover every row shown. |
| **M** | Mutes the pitch by taking its notes to velocity 0 in one Undo step, remembering what they had so unmuting restores it. |
| **S** | Solos the pitch; every other row is muted while any row is soloed. |

Muted rows draw dimmed and drop out of **RND** and **CLR**. Marks, names, mutes,
solos and the **o** scope belong to the clip: they survive switching clips and
quitting Bitwig.

## Grid

The header has two toggles:

- **`16 st` / `32 st`** — how many steps a page shows.
- **`1/16` / `1/8` / `1/32`** — what one step is worth. A page is that many steps long, so
  `1/16` with 16 steps is one bar and `1/8` with 32 steps is four.

Both choices are remembered. A painted note is a sixteenth long by default, or the step's
own length on a finer grid so it cannot overrun its cell. With **FOLD** on, painting only
writes to marked keys.

## Tools

- **GEN** — clears the **marked** rows on this page and fills each with a fresh random pattern: a random number of steps, in random columns, one Undo step.
- **RND** — randomises the velocity of every step in scope, 40% to 100%, one Undo
  step.
- **CLR** — removes every step in scope on the page, one Undo step.
- **MIDI key** — the footer shows the pitch you are playing, read from Bitwig's own
  MIDI input, from a controller or the computer keyboard.

## Install

Patchbay → **Mods → Add Mod…** → pick `ress-sq-0.5.9.zip` (do not unpack it) →
tick **Framework: Piano Roll** and **RESS-SQ** → **Load**.

Needs `piano-roll-framework` 0.2.0-dev.1 or newer. Built against Bitwig 6.1.7 on
macOS only: every Bitwig name inside it is 6.1.7's, so it will not load on another
Bitwig version. It runs beside Polarity's Step Sequencer, both buttons showing.

## Build

From the repository root:

```sh
python3 tools/build_releases.py \
  --bitwig-bin "/Applications/Bitwig Studio.app/Contents/Java" \
  --modkit "<BML>/modkit" \
  --framework "<BML>/mods/piano-roll-framework/helper.jar" \
  --jdk-bin "<jdk-25>/Contents/Home/bin"
```

## Layout

| Path | What |
| --- | --- |
| `mod.json` | The source manifest: id, name, version, `built_against`, requirements |
| `build.json` | Which framework the source compiles against |
| `src/com/ress/sq/RessSq.java` | Hooks: the Show Steps button, the panel mount, mode switches, MIDI input |
| `src/com/ress/sq/SqWidget.java` | The grid: painting, gestures, row controls, tools |
| `src/com/ress/sq/SqClip.java` | Reads the focused clip, its rows, colour, playhead and state key |
| `src/com/ress/sq/SqGrid.java` | Pure geometry: cells, pages, placements, overlap rules |
| `src/com/ress/sq/SqNote.java` | Native note repeats and chance as one Undo step |
| `package/LICENSE` | The original copyright notice, copied into the release ZIP |

The Bitwig class names in the source are 6.1.7's, each with a comment saying what
it is. They change with every Bitwig release.

## Thank you, Polarity

RESS-SQ is a fork of **[Polarity's Step Sequencer](https://github.com/polarity/bitwig-mods/tree/main/step-sequencer)**.
The grid, the interaction model, the page handling and most of the code are his.
This fork adds the per-row controls, the tools, the MIDI readout and the port to
6.1.7's names. See [his mods](https://github.com/polarity/bitwig-mods) — Scale
Fold, Note Intervals, Chord Tools and the rest, covering many more Bitwig versions
than this fork.

MIT; the original notice is in [package/LICENSE](package/LICENSE).
