"""Build ress-sq/mod.json from the mod kit's hooks.json, following
docs/sdk/building.md, "The finished mod.json"."""
import json, pathlib, re

MOD = pathlib.Path(__file__).resolve().parent
WORKSPACE = MOD.parent
hooks = json.loads((WORKSPACE / ".tmp/sq-build/hooks.json").read_text())

SAFE = re.compile(r"[^A-Za-z0-9_.-]")


def target_id(obj):
    if obj["kind"] == "class":
        raw = obj["class"]
    else:
        raw = obj["owner"] + "." + obj["name"]
    raw = raw.replace("/", ".").replace("$", "_")
    raw = raw.replace("<init>", "init").replace("<clinit>", "clinit")
    return SAFE.sub("", raw)


targets = {}


def add_target(obj):
    tid = target_id(obj)
    base, n = tid, 2
    while tid in targets and targets[tid] != obj:
        tid = f"{base}-{n}"
        n += 1
    targets[tid] = obj
    return tid


out_hooks = []
for hook in hooks["hooks"]:
    entry = {
        "id": hook["id"],
        "target": add_target(hook["target"]),
        "transform": hook["transform"],
    }
    if "method_from" in hook:
        entry["method"] = {"from_target": add_target(hook["method_from"])}
        entry["body"] = {"template": hook["body"]}
    if "match" in hook:
        match = hook["match"]
        m = {"opcode": match["opcode"], "occurrence": match["occurrence"]}
        if "target" in match:
            m["target"] = add_target(match["target"])
        else:
            m["owner"] = match["owner"]
            m["name"] = match["name"]
            m["descriptor"] = match["descriptor"]
        entry["match"] = m
    entry["bridge"] = hook["bridge"]
    out_hooks.append(entry)

opens = sorted(add_target(entry) for entry in hooks["access"])

mod = {
    "manifest_version": 2,
    "mod": {
        "id": "ress-sq",
        "name": "Piano Roll: RESS-SQ",
        "version": "0.1.0",
        "author": "Ressa",
        "description": (
            "A sixteenth-note step grid for the Detail Editor with a playhead, page following, "
            "randomise and clear tools, and per-step velocity, offset, length, repeat, pitch and chance."
        ),
        "documentation": "readme",
    },
    "built_against": {
        "bitwig_version": "6.1.7",
        "platform": "macos",
        "architecture": "universal",
    },
    "helper": {"jar": "helper.jar", "class": hooks["hook_classes"][0]},
    "targets": targets,
    "hooks": out_hooks,
    "opens": opens,
    "requires": [{"id": "piano-roll-framework", "min_version": "0.2.0-dev.1"}],
    "resources": {
        "readme": {"kind": "file", "path": "README.md", "media_type": "text/markdown"}
    },
}

(MOD / "mod.json").write_text(json.dumps(mod, indent=2) + "\n", encoding="utf-8")
print(f"mod.json written: targets={len(targets)} hooks={len(out_hooks)} opens={len(opens)}")
