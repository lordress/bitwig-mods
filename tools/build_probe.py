"""Build the probe mod: compile, finish hooks, write dist/<id>-<version>.zip."""
import json, shutil, subprocess, sys, tempfile, zipfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from build_releases import finish_manifest, sha256  # noqa: E402

MOD = "probe-remote-widgets"
REPO = Path(__file__).resolve().parent.parent
JDK = (REPO.parent / "tools/jdk/jdk-25.0.5.1+2/Contents/Home/bin").resolve()
BML = Path.home() / "Library/Application Support/PatchbayLauncher/installs/BML-0.36.0-dev.16-macos"
BITWIG = Path("/Applications/Bitwig Studio.app/Contents/Java")

def run(cmd):
    print("+", " ".join(str(c) for c in cmd))
    subprocess.run([str(c) for c in cmd], check=True)

mod_dir = REPO / MOD
src = json.loads((mod_dir / "mod.json").read_text())
version = src["mod"]["version"]
work = Path(tempfile.mkdtemp(prefix="probe-"))
classes, stage = work / "classes", work / MOD
classes.mkdir(); stage.mkdir()
compile_jar = REPO / "dist" / "bitwig-6.1.7-compile.jar"
run([JDK/"javac", "--release", "21", "-proc:none", "-encoding", "UTF-8", "-implicit:none",
     "-cp", f"{compile_jar}:{BITWIG/'libs.jar'}:{BML/'modkit'/'bml-api.jar'}",
     "-d", classes, *sorted(str(p) for p in (mod_dir/"src").rglob("*.java"))])
hooks_json = work / "hooks.json"
run([JDK/"java", "-jar", BML/"modkit"/"bml-modkit.jar", "finish", classes, hooks_json,
     BITWIG/"bitwig.jar"])
hooks = json.loads(hooks_json.read_text())
(stage/"mod.json").write_text(json.dumps(finish_manifest(src, hooks), indent=2) + "\n")
run([JDK/"jar", "--create", "--file", stage/"helper.jar", "-C", classes, "."])
shutil.copy2(mod_dir/"README.md", stage/"README.md")
shutil.copy2(mod_dir/"build.json", stage/"build.json")
sums = "".join(f"{sha256(p)}  {p.relative_to(stage).as_posix()}\n" for p in sorted(stage.rglob("*")) if p.is_file())
(stage/"SHA256SUMS.txt").write_text(sums)
out = REPO/"dist"/f"{MOD}-{version}.zip"
with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as z:
    for p in sorted(stage.rglob("*")):
        if p.is_file(): z.write(p, f"{MOD}/{p.relative_to(stage).as_posix()}")
print(f"BUILD_OK {out} hooks={len(hooks['hooks'])} sha256={sha256(out)}")
