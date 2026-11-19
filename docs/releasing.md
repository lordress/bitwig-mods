# Build and publish a release

## What a release is

A mod is published as a ZIP attached to a GitHub Release, never committed to the
repository. The launcher reads the manifest at `<mod-id>/mod.json` on the default
branch for catalog metadata and the ZIP's own `mod.json` on install, so the two
must agree on the `mod` block. The rules are the
[shared packaging manifest](patchbay-launcher.md#mods).

The ZIP holds exactly one top-level folder named for the mod id:

```
ress-sq/
  mod.json        generated: the source manifest plus targets, hooks, opens, resources
  helper.jar      the compiled helper
  README.md       the mod's documentation, and the manifest's "readme" resource
  LICENSE
  build.json
  src/            the sources, so anyone can rebuild it
  SHA256SUMS.txt
```

## Prerequisites

| What | Notes |
| --- | --- |
| JDK 25 | `--jdk-bin` points at the folder holding `java`, `javac` and `jar` |
| Bitwig Studio 6.1.7 on macOS | `--bitwig-bin` points at `Contents/Java`; the jar is only read |
| The BML mod kit | `--modkit` points at the folder holding `bml-api.jar` and `bml-modkit.jar`, in a Patchbay install under `modkit/` |
| Framework: Piano Roll | `--framework` points at its `helper.jar`, in `mods/piano-roll-framework/` |

## Build

From the repository root:

```sh
python3 tools/build_releases.py \
  --bitwig-bin "/Applications/Bitwig Studio.app/Contents/Java" \
  --modkit "$HOME/Library/Application Support/PatchbayLauncher/installs/BML-0.36.0-dev.16-macos/modkit" \
  --framework "$HOME/Library/Application Support/PatchbayLauncher/installs/BML-0.36.0-dev.16-macos/mods/piano-roll-framework/helper.jar" \
  --jdk-bin "$HOME/jdk-25.0.5+8/Contents/Home/bin"
```

It prints `MODKIT_FINISH_OK hooks=<n> non_public=<n> classes_rewritten=<n>` and
`BUILD_OK <zip> hooks=<n> opens=<n> sha256=<hash>`. `MODKIT_ERROR` means a hook
does not match Bitwig's jar: a wrong method name, an ambiguous name (the message
lists the descriptors), or a parameter type that cannot accept the value. Fix the
source and build again; `--replace` rebuilds over an existing ZIP.

The compile jar is cached in `dist/` per Bitwig version, so repeat builds skip the
slow step. Both `dist/` and `*.jar` are ignored by git.

## Check offline before publishing

The build compiles and validates hooks, but only BML's own preparation proves the
mod prepares: it translates and checks every mod, patches Bitwig's classes,
verifies them, and resolves every class and member the helper uses.

Put the mod folder and every framework it needs into one folder, then run the
loader of the Patchbay install you use:

```sh
mkdir -p /tmp/check-mods
unzip -q dist/ress-sq-0.1.1.zip -d /tmp/check-mods
cp -R "$BML/mods/piano-roll-framework" /tmp/check-mods/

sh "$BML/loader/prepare-mod.sh" \
  "/Applications/Bitwig Studio.app/Contents/Java" /tmp/check-mods
```

The last line must be `PREPARE_OK ... runtime_build=macos-6.1.7` with exit code 0.
`PREPARE_FAILED`, `DEEP_VERIFY_FAIL` or `UNMAPPED` names the problem. Because
RESS-SQ is written against 6.1.7's own names, BML uses it as written on 6.1.7 and
has nothing to translate; on another Bitwig version the names will not resolve.

Adding Polarity's Step Sequencer to the same check folder is worthwhile: it is
what the coexistence code in `RessSq.addStepsButton` and `RessSq.mount` is written
for, and the preparation reports both mods together.

## Publish

1. Bump `mod.version` in `ress-sq/mod.json` together with anything changed, and
   add the changelog line to the release notes.
2. Build and check as above. The ZIP's name must be
   `<mod-id>-<version>.zip`, matching `mod.version` exactly: `ress-sq-0.1.1.zip`,
   with no OS or platform suffix.
3. Attach the ZIP to a GitHub Release. Prereleases work; several mods may share a
   release because the launcher matches each asset name on its own.
4. Commit the version bump and the sources. Never commit the ZIP: it is a release
   asset, and the launcher ignores repository files.

## When something fails

Report the Bitwig version, the command, the complete output from the first
`MODKIT_ERROR`, `PREPARE_FAILED` or `DEEP_VERIFY_FAIL` line to the end, the built
`mod.json` and `hooks.json`, and — for a preparation failure — the
`target-resolution.tsv` and `helper-linkage-preflight.tsv` from the loader's
`mod-runtime/`. Do not send Bitwig's jars or anything copied from Bitwig.
