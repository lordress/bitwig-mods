"""Build RESS-SQ's release ZIP against a stock Bitwig install.

Compiles the helper against Bitwig's own jar, validates every hook with the mod
kit's `finish`, generates the finished `mod.json` from the hooks it reports, and
writes `dist/<mod-id>-<version>.zip` with one top-level folder named for the mod
id, as Patchbay's launcher requires.

The Bitwig jar is only read; nothing in the Bitwig install is changed.
"""

import argparse
import hashlib
import json
import re
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path
from zipfile import ZIP_DEFLATED, ZipFile

MOD = "ress-sq"
REPO = Path(__file__).resolve().parent.parent
SAFE = re.compile(r"[^A-Za-z0-9_.-]")


def run(command):
    print("+", " ".join(str(part) for part in command))
    subprocess.run([str(part) for part in command], check=True)


def target_id(target):
    """The manifest id for one hook target, per the mod kit's naming rule."""
    if target["kind"] == "class":
        raw = target["class"]
    else:
        raw = target["owner"] + "." + target["name"]
    raw = raw.replace("/", ".").replace("$", "_")
    raw = raw.replace("<init>", "init").replace("<clinit>", "clinit")
    return SAFE.sub("", raw)


def finish_manifest(source, hooks):
    """Add the hooks, targets and opens the compiled helper needs."""
    targets, hooks_out, opens = {}, [], []

    def add(target):
        tid = target_id(target)
        base, n = tid, 2
        while tid in targets and targets[tid] != target:
            tid = f"{base}-{n}"
            n += 1
        targets[tid] = target
        return tid

    for hook in hooks["hooks"]:
        entry = {
            "id": hook["id"],
            "target": add(hook["target"]),
            "transform": hook["transform"],
        }
        if "method_from" in hook:
            entry["method"] = {"from_target": add(hook["method_from"])}
            entry["body"] = {"template": hook["body"]}
        if "match" in hook:
            match = hook["match"]
            entry["match"] = {
                "opcode": match["opcode"],
                "occurrence": match["occurrence"],
                **(
                    {"target": add(match["target"])}
                    if "target" in match
                    else {
                        "owner": match["owner"],
                        "name": match["name"],
                        "descriptor": match["descriptor"],
                    }
                ),
            }
        entry["bridge"] = hook["bridge"]
        hooks_out.append(entry)

    for entry in hooks["access"]:
        opens.append(add(entry))
    opens.sort()

    manifest = {
        "manifest_version": source["manifest_version"],
        "mod": source["mod"],
        "built_against": source["built_against"],
        "helper": source["helper"],
        "targets": targets,
        "hooks": hooks_out,
    }
    if opens:
        manifest["opens"] = opens
    if source.get("requires"):
        manifest["requires"] = source["requires"]
    manifest["resources"] = {
        "readme": {"kind": "file", "path": "README.md", "media_type": "text/markdown"}
    }
    return manifest


def sha256(path):
    digest = hashlib.sha256()
    with open(path, "rb") as handle:
        for block in iter(lambda: handle.read(1 << 20), b""):
            digest.update(block)
    return digest.hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--bitwig-bin", required=True, type=Path,
                        help="Bitwig's bin folder, or Contents/Java on macOS")
    parser.add_argument("--modkit", required=True, type=Path,
                        help="folder holding bml-api.jar and bml-modkit.jar")
    parser.add_argument("--framework", required=True, type=Path,
                        help="the piano-roll-framework helper.jar")
    parser.add_argument("--jdk-bin", required=True, type=Path,
                        help="folder holding java, javac and jar")
    parser.add_argument("--out", type=Path, default=REPO / "dist")
    parser.add_argument("--replace", action="store_true",
                        help="rebuild over an existing ZIP")
    args = parser.parse_args()

    mod_dir = REPO / MOD
    source = json.loads((mod_dir / "mod.json").read_text(encoding="utf-8"))
    version = source["mod"]["version"]
    bitwig_version = source["built_against"]["bitwig_version"]

    bitwig = args.bitwig_bin.resolve()
    bitwig_jar, libs_jar = bitwig / "bitwig.jar", bitwig / "libs.jar"
    for required in (bitwig_jar, libs_jar, args.framework, args.modkit / "bml-api.jar",
                     args.modkit / "bml-modkit.jar"):
        if not required.exists():
            sys.exit(f"MISSING {required}")
    java = args.jdk_bin / "java"
    javac = args.jdk_bin / "javac"
    jar = args.jdk_bin / "jar"

    out = args.out.resolve()
    out.mkdir(parents=True, exist_ok=True)
    package = out / f"{MOD}-{version}.zip"
    if package.exists() and not args.replace:
        sys.exit(f"EXISTS {package} (pass --replace to rebuild)")

    work = Path(tempfile.mkdtemp(prefix=f"{MOD}-build-"))
    classes, stage = work / "classes", work / MOD
    classes.mkdir(parents=True)
    stage.mkdir(parents=True)

    # One compile jar per Bitwig build: classes and members, no method bodies.
    compile_jar = out / f"bitwig-{bitwig_version}-compile.jar"
    if not compile_jar.exists():
        run([java, "-jar", args.modkit / "bml-modkit.jar", "compile-jar", bitwig_jar, compile_jar])

    classpath = ":".join(str(p) for p in (
        compile_jar, libs_jar, args.modkit / "bml-api.jar", args.framework.resolve()))
    sources = sorted(str(p) for p in (mod_dir / "src").rglob("*.java"))
    if not sources:
        sys.exit(f"NO_SOURCES {mod_dir / 'src'}")
    run([javac, "--release", "21", "-proc:none", "-encoding", "UTF-8", "-implicit:none",
         "-cp", classpath, "-d", classes, *sources])

    hooks_json = work / "hooks.json"
    run([java, "-jar", args.modkit / "bml-modkit.jar", "finish", classes, hooks_json,
         bitwig_jar, libs_jar, args.framework.resolve()])
    hooks = json.loads(hooks_json.read_text(encoding="utf-8"))

    (stage / "mod.json").write_text(
        json.dumps(finish_manifest(source, hooks), indent=2) + "\n", encoding="utf-8")

    run([jar, "--create", "--file", stage / "helper.jar", "-C", classes, "."])

    shutil.copy2(mod_dir / "README.md", stage / "README.md")
    shutil.copy2(mod_dir / "build.json", stage / "build.json")
    shutil.copytree(mod_dir / "src", stage / "src")
    if (mod_dir / "package").is_dir():
        for item in (mod_dir / "package").iterdir():
            destination = stage / item.name
            if item.is_dir():
                shutil.copytree(item, destination)
            else:
                shutil.copy2(item, destination)

    sums = "".join(
        f"{sha256(path)}  {path.relative_to(stage).as_posix()}\n"
        for path in sorted(stage.rglob("*")) if path.is_file()
    )
    (stage / "SHA256SUMS.txt").write_text(sums, encoding="utf-8")

    with ZipFile(package, "w", ZIP_DEFLATED) as archive:
        for path in sorted(stage.rglob("*")):
            if path.is_file():
                archive.write(path, f"{MOD}/{path.relative_to(stage).as_posix()}")

    print(f"BUILD_OK {package} hooks={len(hooks['hooks'])} "
          f"opens={len(hooks['access'])} sha256={sha256(package)}")
    print(f"ZIP top level: {MOD}/  ({package.stat().st_size} bytes)")
    shutil.rmtree(work, ignore_errors=True)


if __name__ == "__main__":
    main()
