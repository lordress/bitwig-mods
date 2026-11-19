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
- **Its own look.** Violet steps, an amber playhead and a RESS-SQ header, so it
  is obvious which grid is up.

## Install with Patchbay

1. Install Patchbay Mod Loader and patch Bitwig from its window.
2. **Mods → Add Mod…** and pick `ress-sq-0.1.1.zip` (do not unpack it).
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

Needs JDK 25 and a Bitwig 6.1.7 install, plus the BML mod kit.

```sh
JAVA=.../jdk-25/bin/java
JAVAC=.../jdk-25/bin/javac
KIT=.../BML/modkit
BW=/Applications/Bitwig\ Studio.app/Contents/Java

# 1. compile jar, once per Bitwig build
$JAVA -jar $KIT/bml-modkit.jar compile-jar $BW/bitwig.jar /tmp/bw617-compile.jar

# 2. compile against Bitwig, libs, the API and the framework
$JAVAC --release 21 -proc:none -encoding UTF-8 -implicit:none \
  -cp /tmp/bw617-compile.jar:$BW/libs.jar:$KIT/bml-api.jar:.../piano-roll-framework/helper.jar \
  -d /tmp/classes $(find src -name '*.java')

# 3. validate every hook against Bitwig's jar and write hooks.json
$JAVA -jar $KIT/bml-modkit.jar finish /tmp/classes /tmp/hooks.json $BW/bitwig.jar $BW/libs.jar .../piano-roll-framework/helper.jar

# 4. regenerate mod.json from hooks.json
python3 build_manifest.py
```

`src/` holds only Java. `build_manifest.py` writes `mod.json` from the mod kit's
`hooks.json`, so the manifest always matches the compiled helper.

## Layout

| Path | What |
| --- | --- |
| `src/com/ress/sq/RessSq.java` | Hooks: the Show Steps button, the panel mount, mode switches |
| `src/com/ress/sq/SqWidget.java` | The grid widget: painting, mouse gestures, RESS-SQ tools |
| `src/com/ress/sq/SqClip.java` | Reads the focused clip, its rows, colour and playhead |
| `src/com/ress/sq/SqGrid.java` | Pure geometry: cells, pages, placements, overlap rules |
| `src/com/ress/sq/SqNote.java` | Native note repeats and chance as one undo step |
| `build_manifest.py` | Turns `hooks.json` into the finished `mod.json` |

The Bitwig class names in the source are 6.1.7's, each with a comment saying what
it is. They change with every Bitwig release.

## Credits

The idea, the interaction model and most of the code come from
[Polarity's Step Sequencer](https://github.com/polarity/bitwig-mods/tree/main/step-sequencer),
MIT licensed; see [LICENSE](LICENSE).
