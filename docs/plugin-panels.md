# Framework: Plug-in Panels — working notes

Source: [Patchbay · frameworks/plugin-panels-framework](https://github.com/shaboogen-dev/Patchbay/tree/main/frameworks/plugin-panels-framework)
(read at `0.3.0-dev.2`, `mod.json` `built_against.bitwig_version` 6.1.4).
The framework's own `README.md` is the full specification — this file is the
short path through it plus what I verified locally.

## What it is, and what it is not

Replaces the generic parameter list of a **VST2 / VST3 / CLAP** plug-in with a
panel of Bitwig's own widgets, bound to the plug-in's parameter objects. So
drag, undo, automation recording and modulation behave exactly as on Bitwig's
own plug-in controls — this is not an overlay drawn on top of the plug-in's
window, and it does not touch the plug-in's own GUI.

A panel is a **component**: one `*.json` file in Patchbay's `components/`
folder, chosen in Patchbay. Two consequences worth remembering:

- the framework **ships no panels** (`mod.json` has no bundled component), so
  every panel is yours to write;
- a newly chosen panel applies the next time **Bitwig starts**.

| Fact | Value |
| --- | --- |
| Mod ID | `plugin-panels-framework` |
| Area | `Framework` |
| Requires | `action-menu-composition-framework` ≥ `0.6.0-dev.1`, Patchbay ≥ `0.36.0-dev.11` |
| Compile against (per Patchbay `docs/sdk/frameworks.md`) | `0.3.0-dev.1` — behind main's `0.3.0-dev.2`; a mod only links the framework's Java package, not layouts |
| Component group | `panels`, selection `multiple`, `detect: { "panel": "*.json" }` |
| Registrations | `actions.properties` → `bml.pluginpanels.internal.CopyParameters`, `helper.jar` |

The framework hooks Bitwig's plug-in body builder, not the plug-in, so it only
works because Patchbay's loader is present. It cannot be shipped as a plain
"theme" or by dropping JSON into a Bitwig folder.

## The loop

1. **Get the parameter sheet.** In Bitwig, right-click the plug-in device →
   **Generate Parameter Sheet** (this lives behind Framework: Actions and Menus).
   It lands in `<Patchbay settings>/mods/plugin-panels-framework/sheets/` as
   `<plug-in> <format>.parameters.json`; the copy happens up to 3 s later, once
   the plug-in has answered for each parameter's text at 0 and 1.
2. **Write the layout**, in the builder or by hand (format below).
3. **Install it.** Builder → **Copy JSON**, then in Bitwig right-click the
   plug-in → **Save Panel from Clipboard** (validates first and says why a panel
   can't be used), or just drop the file in `components/`.
4. **Choose it** in Patchbay, restart Bitwig.
5. **Check the log.** One line per panel: `height available N units, needed M`,
   or the reason it fell back to Bitwig's page.

The builder (`builder/index.html`, open from disk — no server, no build step)
is optional but does the tedious parts: it reads a parameter sheet, hints at
which parameters are toggles / integers / unreliable text, previews at Bitwig's
UI scale with the real default theme, and validates. Hand-writing JSON is fine
for small panels; use the builder the moment there are guides, spacers, knob
alignment or mode colours involved.

## The shape of a layout

```jsonc
{
  "name": "Shown in Patchbay",
  "match": { "name": "ValhallaVintageVerb", "vendor": "…", "format": "VST3" },
  "columns": [
    { "header": "FILTER", "width": 54, "divider": true, "items": [
      { "knob": "Cutoff", "size": "large", "values": { "min": 20, "max": 20000, "unit": "Hz", "curve": "log" } },
      { "row": [ { "knob": "Res", "size": 34 }, { "toggle": "Bypass" } ], "gap": 4 }
    ]}
  ],
  "footer": [ { "dropdown": "Mode", "choices": [["A", 0], ["B", 1]] } ]
}
```

- `match.name` is **required** (`"*"` matches all); `vendor`/`format` optional;
  name comparison is case-insensitive. `vendor`+`name` are identical across a
  plug-in's VST3 and CLAP builds, so one layout covers both.
- `columns` **or** `pages: [{ name, columns, footer }]`; `footer` is one row
  under the columns.
- Layout `width` is the **device body** width in layout units (Bitwig's own is
  170). Height is not yours to set: every device in the chain shares one height,
  so a too-tall layout is zoomed down to 60 % and then cut off — put controls in
  rows.
- Stacking only: column = top to bottom, `row` = side by side, rows nest. No
  absolute positions, no overlap, no z-order.

### Items — exactly one kind key each

`knob`, `dropdown`, `toggle`, `value`, `slider`, `meter`, `text`, `row`,
`envelope`, `spacer` (`true`, alone).

| Kind | Notes that bite |
| --- | --- |
| `knob` | `size`: theme size (`tiny`…`huge`) or a **number** → fixed-size cell (label + knob + value centred, value never widens it). Stepped parameters snap and show the step name; no tick marks |
| `dropdown` | `choices` presets are `[["Label", normalisedValue], …]`, one undo step per pick. A **continuous** parameter needs `choices`, otherwise the plug-in's step names are used |
| `toggle` | Lit at ≥ 0.5, click sets 0/1. Wrong for anything with more than 2 steps (validator warns) |
| `meter` | `style`: `needle` (64×40 default), `bar` (13×56), `level` (4×56, one per channel) |
| `envelope` | Read-only ADSR-family shape, redrawn on parameter change. `attack` required; `sustain` needs `decay`; per-segment `values` make widths real times, without them widths are normalised |
| `spacer` | Takes spare height in a column (or spare width in a fixed-width row). This is how you pin a control to the bottom with `fill_height` |
| `text` | Layout text only: `{ "text": "MODULATION", "font": "Bold", "color": "Panel Orange" }` |

### References — how items find parameters

`"Cutoff"` (name, case-insensitive; falls back to ignoring spaces/underscores/
hyphens so `PreDelay` finds `Pre Delay`) · `"Dry / Wet|Mix"` (first match wins,
for per-format name drift) · `id:PID4` · `#7` (sheet index) · `auto:knob` /
`auto:list` / `auto:toggle` (next unshown parameter of that kind).

**Failure policy is the single most important design decision in a layout:**

- any unresolved reference → the **whole layout is dropped** and Bitwig's own
  page is shown, unless you set `min_resolved` (percent of references that must
  match); with it, missing items are simply left out and the layout only falls
  back below the threshold.

For plug-ins whose parameter set varies by version or format, set
`"min_resolved": 60`-ish and keep the panel.

### Values and typing

`values` maps normalised 0–1 ↔ the displayed value, and drives typed input:

```json
{ "min": 0.2, "max": 70, "unit": "s", "curve": "log" }
```

- `curve`: `linear` (default), `log`/`exp` (geometric, min and max must be > 0),
  `{ "pow": 2 }` (any exponent), or `points: [[display, normalised], …]` for an
  arbitrary taper instead of min/max/curve.
- `unit` conversions are understood on typed input: ms↔s, Hz↔kHz, and `k` for
  ×1000 ("8k" → 8000 Hz). Out-of-range typed values clamp.
- `values.min/max` should be read from the sheet's `min_text`/`max_text`; to map
  a parameter with no readable ends, type 0 / 0.25 / 0.5 / 0.75 / 1 with
  `"typing": "normalised"` and read the plug-in's window.
- `typing`: `plugin` (ask the plug-in's own text-to-value), `mapping` (read in
  display units via `values`, default when `values` are present), `normalised`.
- `refresh`: `"settle"` re-asks the plug-in's text 150/500/1000 ms after a
  change — the fix for lagging text (ValhallaVintageVerb). `displayValue` /
  `value_format` (`"percent"`, `{range, decimals, unit}`, `{map: [[n, text]]}`)
  only changes what is drawn, never the plug-in's value or Bitwig's popups.

### Chrome: toolbar, stripe, mode colours, fill_height

- `toolbar`: `true`/absent (Bitwig's row), `false` (no row at all — panel starts
  like a native device), or an object with `pages`, `sidechain`, `outputs`,
  `search`, `position` (`top`|`right`), `style`, `size`, `gap`, `background`,
  `divider`, `list`, `order`, `align`.
  - `"position": "right"` is the interesting one: a vertical bar with Bitwig's
    own buttons (and with `"list": true`, Layout/List **mode buttons** so the
    user can flip to Bitwig's full parameter list). It adds `size + gap` to the
    device width.
  - The **container button can never be removed** — it is the only route to the
    plug-in's FX container. Don't fight this.
  - The Parameters tab survives any settings that keep `sidechain`/`outputs`,
    otherwise a device could be stranded with no way back to the panel.
- `stripe`: accent line on the top edge, default the theme's `Knob Value Color`;
  `{ "color": …, "height": 1..20 }`.
- `mode_colors`: `param` + `map` (mode name → colour, or → per-target colours) +
  optional `mix` + `apply` (`stripe`, `knob_arc`, `header`, `divider`,
  `background_tint`, `envelope`, `toolbar`). The mode is read from the nearest
  `dropdown` `choices` on the same parameter, else the step name — **never** the
  plug-in's value text. This is how a VintageVerb/Amp-mode panel recolours
  itself without a rebuild; it repaints only, nothing re-lays-out.
- `fill_height: true`: columns run to the body bottom (or down to the footer),
  free height going under the content or per the column's `valign`. Required for
  spacer-based bottom anchoring.
- Fonts and colours are **theme names** (`Grey 1`…`Grey 6`, `Panel body`,
  `Default`, `Bold 16`, …) so they follow the user's theme; `#rrggbb(aa)` also
  works. An unknown name is logged and replaced by the default.

## Gotchas worth the time

- `padding` sits **inside** `width` on a column/row: `width: 120, padding: [6,6,2,4]`
  leaves 108 for content. With `background`, the colour fills the padding and
  adds 2 units on every side outside it.
- Knobs in a row self-align label and centre lines once on screen;
  `"align_knobs": false` opts out.
- Every column on a page gets a header line if any column has a header (empty
  where absent) so labels line up.
- Panel JSON is re-read when the file changes, but the built body is not:
  reload the plug-in / reopen the project. Programs↔Parameters tab switching
  sometimes suffices.
- Multiple chosen panels are tried in the order chosen, first match wins; the
  log names later ones that also matched.
- A file holds one layout or `{"name": …, "layouts": […]}`.
- Validator is authoritative and self-documenting:
  `builder/core.js` (`KINDS`, `LAYOUT_KEYS`, `ITEM_KEYS`, `VALUES_KEYS`, `validate`).

## Verify a layout without Bitwig

```sh
cd <patchbay>/frameworks/plugin-panels-framework/builder
node test/roundtrip.js <sheet>.parameters.json your-panel.json
```

It round-trips each layout (checks JSON reads back identically and keeps key
order), prints per-parameter hints for a sheet, and runs the framework's own
validator against the sheet when `match` lines up. No dependencies, no browser.
Note it only checks *structure* when no sheet is supplied — unresolved
parameter references cannot be seen without one.

Layouts in the framework's `test-layouts/` (`shaperbox-3`, `surge-xt`,
`valhalla-vintageverb`) are the best worked examples of the format.

## Checked locally

- `node test/roundtrip.js ../test-layouts/*.json` → all three `SAME after
  export; key order unchanged`.
- A hand-written TAL-Chorus-LX panel (2 columns, header, toggle row, footer
  text, `fill_height`, `stripe`) validated clean against a synthetic sheet:
  references resolved, hints came back `values 0..100 %` for the knobs and
  `toggle` for `Chorus 1/2`.
- Existing `test-components/tal-chorus-lx.json` trips the validator:
  `toolbar.order` lists `"list"` without `"list": true` (warning only — the
  button just doesn't show).
