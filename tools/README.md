# Tools

Four scripts live here, none of which is part of the mod build:

| File | What it is |
| --- | --- |
| `mxt_bendable_cuboids_animation.js` | A Blockbench companion plugin. Documented below. |
| `generate_classic_gui_textures.py` | Regenerates `src/main/resources/assets/mxt/textures/gui/classic/` with Pillow. |
| `generate_rift_textures.py` | Regenerates the rift's surface pattern and the icons of the rift block and of the rift anchor with Pillow and NumPy. |
| `codex_token_usage.py` | Tallies Codex token spend from local rollout logs, filtered by working directory. |

## Rift Textures (`generate_rift_textures.py`)

```bash
python tools/generate_rift_textures.py
```

Writes two files and overwrites them without asking:

| Output | Contents |
| --- | --- |
| `textures/entity/rift.png` | A 128x128 seamless ridged fractal. Red and green carry the pattern's brightness and blue is lifted slightly, so it reads cold; alpha is the pattern's coverage, which the rift shader takes as how much of the surface a sample lights up. |
| `textures/item/rift.png` | The rift block's icon: a 16x16 lens-shaped tear with a bright core, drawn at 4x and downsampled. |

Both are deterministic: the surface texture is seeded, so regenerating it reproduces the same image.

## Codex Token Usage (`codex_token_usage.py`)

Reads the JSONL rollout logs Codex writes under `~/.codex/sessions/` and reports how many tokens
were spent in a given working directory.

```bash
python tools/codex_token_usage.py E:/Java/MiXianTu        # one project
python tools/codex_token_usage.py E:/Java/MiXianTu --json # machine readable
python tools/codex_token_usage.py --list-dirs             # every project, ranked
python tools/codex_token_usage.py E:/Java/MiXianTu --index # most accurate attribution
```

Useful flags: `--subdirs` (include sessions in nested working directories), `--since` /
`--until` (`YYYY-MM-DD`), `--by-dir`, `--main-only` (exclude sub-agent rollouts),
`--no-archived`, and `--sessions-root` to point at a different Codex home.

Two details of the log format make a naive sum of file totals wrong, and the script handles both:

* **Resumed sessions span several files.** Resuming continues the same session id in a new
  `rollout-…-<session-id>_<new-thread-id>.jsonl` whose `total_token_usage` counter starts from an
  inherited baseline rather than zero. Files are grouped by session id and only each file's
  *growth* is counted, so resumed tokens are counted once.
* **Some `token_count` events are written twice** within a second with an identical cumulative
  total. The script reads the cumulative `total_token_usage` instead of summing the per-call
  `last_token_usage`, which would over-count.

Sub-agent rollouts carry their own counters and are *not* included in their parent's totals, so
they are reported as a separate line rather than being folded in. `input + output = total`, and
`cached` / `reasoning` are already-contained, discounted subsets of `input` / `output`.

### Where the logs live, and why `--index` exists

Rollouts are split across **two** stores, both of which are scanned by default:

| Store | Contents |
| --- | --- |
| `~/.codex/sessions/<YYYY>/<MM>/<DD>/` | live threads |
| `~/.codex/archived_sessions/` | threads archived in the UI — still real spend |

For directory attribution, prefer `--index`, which reads the per-thread totals from Codex's
`~/.codex/state_*.sqlite` index instead of the rollout files. It is authoritative because the
index records the **project** directory, while a rollout's `session_meta.cwd` can be a scratch
folder: Codex Desktop sometimes opens `~/Documents/Codex/<date>/<name>` and drives the real
project from there, so a rollout-only scan silently misses those threads. The index also covers
threads whose rollout file has been deleted.

The two modes differ slightly and reconcile exactly: the index reports one `tokens_used` per
thread (a resumed thread's total does not re-include the pre-resume tail), whereas the rollout
scan diffs each file, so it can be a few percent higher on heavily resumed threads.

## Bendable Cuboids Animation (`mxt_bendable_cuboids_animation.js`)

A local Blockbench companion plugin for GeckoLib Animation Utils. It is intended for Player Animation
Library (PAL) and BendableCuboids animations used by MiXianTu.

### Installation

1. Install **GeckoLib Animation Utils** from Blockbench's plugin browser.
2. Use `File -> Plugins -> Load Plugin from File` and select
   `mxt_bendable_cuboids_animation.js`.
3. Open a GeckoLib Animation project, select a bone in the timeline, then use
   `Animation -> Insert PAL Bend Keyframe`.

The timeline gains a `Bend (PAL)` channel. Its keyframe panel exposes one
field named `旋转角度` (bend angle) rather than X/Y/Z. Player Animation Library
passes only the bend track's X keyframes to its single-float
`PlayerAnimBone.bend` value, which BendableCuboids consumes. The plugin stores
the visible value as X and automatically writes Y/Z as `0` for GeckoLib's
vector JSON format.

### Export

GeckoLib Animation Utils exports the channel directly:

```json
"torso": {
  "bend": {
    "vector": [25, 0, 0]
  }
}
```

The channel can be previewed in any Blockbench project with bone animation and
cube meshes. Its exported JSON is intended for GeckoLib/PAL/BendableCuboids and
is read back when the same animation JSON is imported.

### Project Persistence

Saving a `.bbmodel` writes bend tracks into a dedicated root field so they do
not depend on Blockbench retaining an unknown animation channel:

```json
"mxt_bendable_cuboids": {
  "version": 1,
  "animations": [
    {
      "uuid": "...",
      "name": "animation",
      "animators": {
        "bone-uuid": [{"channel": "bend", "time": 0.5, "data_points": [{"x": "25", "y": "0", "z": "0"}]}]
      }
    }
  ]
}
```

The normal `animations` section stores every non-bend channel. When a project
is loaded with this plugin enabled, its bend tracks are restored before
Blockbench creates the animation timeline. Animation UUIDs are used first;
the animation name is only a fallback for older project files.

### Preview Limits

The viewport preview tessellates each cube and applies the default player
BendableCuboids deformation to the local mesh. It previews the result without
altering the `.bbmodel` geometry or export model.

The preview is deliberately limited to the default non-inverted bend direction
used by Minecraft player limbs. It cannot preview runtime-only details such as
PAL's `applyBendToOtherBones`, armor overlays, or another mod's custom cuboid
pivot/direction configuration. Test those details in-game.

For player models, a direct child bone named `right_item` or `left_item`
(underscores, spaces, and hyphens are interchangeable) is treated as a held
item. During an arm bend, the plugin keeps that bone rigid and moves it around
the arm mesh's actual local bend centre on the X axis. This accounts for model
files whose `right_item` / `left_item` origin is already placed at the hand.
Other child bones remain unaffected.
