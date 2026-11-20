# RESS-SQ

A sixteenth-note step grid for Bitwig Studio's Detail Editor, built for my own
Bitwig 6.1.7 on macOS. It is a fork of
[Polarity's Step Sequencer](https://github.com/polarity/bitwig-mods/tree/main/step-sequencer)
(MIT, © 2026 shaboogen), ported to 6.1.7's names and extended with a few tools I
wanted while writing patterns.

Open a MIDI clip in the Detail Editor and click **Show Steps** below
Drum/Hybrid. The grid replaces the native note content until you click it again
or switch to Piano or Drums.

## What it does

- **Draw steps.** Click a cell to add or remove a note. Start on an empty cell
  and drag across the grid to draw several notes in one undo step.
- **Edit a step.** Pick **Velocity**, **Offset**, **Length**, **Repeat**,
  **Pitch** or **Chance** in the footer, then drag an occupied cell vertically.
  Escape cancels the gesture.
- **Pages.** 16 sixteenth-note steps per page. The header arrows change pages.
  Move the mouse wheel over the grid to scroll the pitch rows.
- **Erase.** Right-drag or Shift-click-drag across the grid. The red outline
  previews what will go, and release erases it in one undo step.
- **Outline and marks.** The playhead outlines its set steps. The bright fill is
  velocity. A thin line marks a held note in a later cell. A number marks several
  notes starting in the same cell, and those cells are protected from clicks that
  would delete ambiguously.

## Rows: mark, fold, mute, solo, name

The label column holds one control row per pitch:

- **Mark** — the small square on the left of the name. **FOLD**, in the footer
  before **Velocity**, hides every unmarked row.
- **M** — mutes that pitch: its notes go to velocity 0 in one undo step and keep
  the velocity they had, so unmuting restores it. Nothing is deleted.
- **S** — solos that pitch: while any row is soloed, every other row is muted the
  same way.
- **→** — selects every note of that pitch in the clip, so RND and Bitwig's own
  tools work on the row.
- **Rename** — click the row's pitch name (`B2`, not the whole column) to pick one (Kick, Snare, Clap, …). The row
  then reads `B2 Kick`, with Bitwig's own pitch name kept in front. **Clear** puts
  Bitwig's name back.

Muted rows draw dimmed and their steps drop out of the **RND** and **CLR** tools.
The **→** pill tints softly while that row's notes are selected.

Marks, names, mutes and solos belong to the clip: switch to another clip and come
back and the row state is as you left it. Mutes are stored with the velocities
they replaced, so unmuting still restores them.

## What RESS-SQ adds

- **FLW — follow the playhead.** With it on, the grid turns to the page the
  transport is playing, so a long clip follows along. On by default; click to
  turn it off when you want to browse pages while it plays.
- **RND — randomise.** Replaces the velocity of every step on the current page
  with a random value between 40% and 100%, in one undo step. **If notes are
  selected in the editor, only those notes change**; when none are selected it
  covers the whole page.
- **CLR — clear.** Removes every step the grid shows on the current page in one
  undo step. Cells it draws as protected are left alone.
- **Its own look.** Violet steps, an amber playhead and a RESS-SQ header, so it
  is obvious which grid is up.

## Install with Patchbay

1. Install Patchbay Mod Loader and patch Bitwig from its window.
2. **Mods → Add Mod…** and pick `ress-sq-0.1.5.zip` (do not unpack it).
3. Tick **Framework: Piano Roll** (it ships with Patchbay) and **RESS-SQ**, then
   click **Load**.

RESS-SQ needs `piano-roll-framework` 0.2.0-dev.1 or newer. It is built against
Bitwig **6.1.7 on macOS** only: every Bitwig name inside it is 6.1.7's, so do not
install it on another Bitwig version.

**Runs beside Polarity's Step Sequencer.** Both add a button below Drum/Hybrid.
RESS-SQ recognises when the other mod is enabled, lets it build its button, and
puts its own next to it — so you get both, with RESS-SQ's carrying its own icon
and the tooltip **RESS-SQ Steps**. Clicking either one shows that mod's grid
(same mode, so they take turns rather than stacking).

## Build

From the repository root, with JDK 25 and a Bitwig 6.1.7 install:

```sh
python3 tools/build_releases.py \
  --bitwig-bin "/Applications/Bitwig Studio.app/Contents/Java" \
  --modkit "<BML>/modkit" \
  --framework "<BML>/mods/piano-roll-framework/helper.jar" \
  --jdk-bin "<jdk-25>/Contents/Home/bin"
```

It compiles the helper against Bitwig's own jar, validates every hook with the mod
kit, generates the finished `mod.json` from the hooks it reports, and writes
`dist/ress-sq-0.1.5.zip`. See [../docs/releasing.md](../docs/releasing.md) for the
offline check and publishing.

## Layout

| Path | What |
| --- | --- |
| `mod.json` | The source manifest: id, name, version, `built_against`, the framework it needs |
| `build.json` | Which frameworks the source compiles against |
| `src/com/ress/sq/RessSq.java` | Hooks: the Show Steps button, the panel mount, mode switches |
| `src/com/ress/sq/SqWidget.java` | The grid widget: painting, mouse gestures, RESS-SQ tools |
| `src/com/ress/sq/SqClip.java` | Reads the focused clip, its rows, colour and playhead |
| `src/com/ress/sq/SqGrid.java` | Pure geometry: cells, pages, placements, overlap rules |
| `src/com/ress/sq/SqNote.java` | Native note repeats and chance as one undo step |
| `package/LICENSE` | The original copyright notice, copied into the release ZIP |

`mod.json` here stays the source manifest. The build script writes the packaged
one, with `targets`, `hooks` and `opens` taken from the compiled helper, so the
two cannot drift apart.

The Bitwig class names in the source are 6.1.7's, each with a comment saying what
it is. They change with every Bitwig release.

## Credits

The idea, the interaction model and most of the code come from
[Polarity's Step Sequencer](https://github.com/polarity/bitwig-mods/tree/main/step-sequencer),
MIT licensed; see [package/LICENSE](package/LICENSE).
