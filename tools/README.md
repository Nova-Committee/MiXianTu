# Tools

Five scripts, two browser tools and one check script live here, none of which is part of the mod build:

| File | What it is |
| --- | --- |
| `mxt_bendable_cuboids_animation.js` | A Blockbench companion plugin. Documented below. |
| `generate_classic_gui_textures.py` | Regenerates `src/main/resources/assets/mxt/textures/gui/classic/` with Pillow. |
| `generate_rift_textures.py` | Regenerates the rift's surface pattern and the icons of the rift block and of the rift anchor with Pillow and NumPy. |
| `codex_token_usage.py` | Tallies Codex token spend from local rollout logs, filtered by working directory. |
| `export_alchemy_furnace.py` | Splits the Blockbench furnace export into multiblock models, blockstates and matching Java collision shapes. |
| `talisman_strokes.html` | A browser editor for a drawing recipe's `pattern.strokes`. Documented below. |
| `formation_figure.html` | A browser editor for a formation's figure: the ring and the star an array draws. Documented below. |
| `formation_figure_check.mjs` | Its check: `node tools/formation_figure_check.mjs` runs that page's own script against a stub DOM and compares the geometry with the mod's. |

## Alchemy Furnace (`export_alchemy_furnace.py`)

Edit `src/main/resources/assets/mxt/models/block/alchemy_furnace.bbmodel` in Blockbench,
then export the Java block model to the adjacent `alchemy_furnace.json`. Run:

```bash
python tools/export_alchemy_furnace.py
```

The standard-library-only script scales the model to 3×3×3, clips its cubes and UVs, and
overwrites the occupied-cell models and their lit variants, the five blockstate files,
unformed component models/item definitions, and `runtime/alchemy/AlchemyFurnaceShapes.java`.
Left/right refer to a player facing the front. Unformed components are cubes, not miniature
assembled furnaces.

The structure's two layers above the base are split cell by cell and are all occupied: core
is 10, main input is 14, auxiliary/catalyst input is 12, output is 22, and the remaining 18
cells are walls - including 13, which used to be the hollow one. The bottom layer only needs
its four corners (0, 2, 6, 8), so it is cut along the model's mid-lines into four 1.5-cell
quadrants, 24 units square, one per corner: the four corner blocks then draw the whole base
between them and the five cells between the corners need no part at all (the script deletes
stale ones). Rendering and collision use the same source bounds, so the Java shapes stay
clipped to each block's own 16³ cell: nobody can stand in those five cells anyway, because
the layer above them is solid.

Output is deterministic. Rotated cubes and rotated UVs are rejected rather than
silently producing different collision or texture geometry. Do not edit generated
parts or Java bounds independently; regenerate them after changing the source model.

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
python tools/codex_token_usage.py /path/to/MiXianTu        # one project
python tools/codex_token_usage.py /path/to/MiXianTu --json # machine readable
python tools/codex_token_usage.py --list-dirs             # every project, ranked
python tools/codex_token_usage.py /path/to/MiXianTu --index # most accurate attribution
```

The path is whatever directory you actually run Codex in — this checkout is not at a fixed location, so the
script takes it as an argument (`--list-dirs` prints the names it has seen, which is the easy way to copy one).

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

## Talisman Strokes (`talisman_strokes.html`)

A single-file browser editor for the `pattern.strokes` of an `mxt:talisman_drawing` recipe. Open it straight
from disk — `file://`, no build step, no server, no dependencies — and it draws the recipe's own canvas:
90 × 210 pixels, points normalised to `[0,1]²`, paper `#FFFE85` and ink `#FF0000`, which are the two colours
the recipe type defaults to. The board therefore looks like the workstation's paper.

Two drawing modes: **polyline** (click a vertex; `Enter`, a right click or a double click ends the stroke,
`Esc` drops it) and **freehand** (drag, and the stroke is simplified with Ramer-Douglas-Peucker at the ε in
the toolbar). `Ctrl+Z` / `Ctrl+Y` undo and redo, the list under the toolbar removes one stroke, and
`Load from JSON` takes back either a bare `strokes` array or a whole `pattern` block.

What it shows, and why:

* **Stroke order** — the dot and the number at each stroke's start, which is what `pattern.show_order: true`
  asks the game to draw.
* **Ink length** — the drawn length in pixels, which is about what the brush is charged: one pigment point
  per pixel of arc length.
* **Load-time problems** — a stroke with fewer than two points, more than 256 strokes, more than 4096 points
  in one stroke, and a point cloud with no spread at all are the ways `validateStrokes` rejects a pattern.
  The tool reports them before a data pack has to.

It is deliberately only the shape. The recipe still needs its `talisman`, its `result` grades and whatever
else it declares, and the tool does not write `pattern.tolerance` / `guide` / `show_order` — those are recipe
fields, not strokes. Scoring resamples every 2 px and simplifies at `0.5 × tolerance` (6.3 px at the default
`0.06`), so points closer together than that do not survive it.

## Formation Figure (`formation_figure.html`)

A single-file editor for a formation's figure — the ring plus the star an `mxt:particle` module draws. Open it straight
from disk: `file://`, no build step, no server, no dependencies. The board is the array seen from straight above, in
the mod's own coordinates: **+X to the right (east), +Z downwards (south), north up**, and the ring at r = 1 is the
circle the array's `radius` draws. Each end of the two axes carries its compass letter and the coordinate it sits at
(`1` on the ring, `-1` the same distance the other way), so the ring reads as the array's own edge. Coordinates are in
**units of that radius**, so one figure scales with every array that reads it.

**What it writes is one `mxt:formation_shape` entry**: save it as `data/<namespace>/mxt/formation_shape/<name>.json`
and name it from an `mxt:particle` module's `shape`. `mxt:five` (the ring plus a pentagram), `mxt:six` and `mxt:eight`
are the three the mod ships itself, and the board opens on `five`.

**Two primitives only: polylines and circles.** That is not a UI simplification, it is the renderer's own vocabulary —
`FormationShapePath` walks a circle by angle from due east and a polyline by arc length from its first point, and there
is nothing else it can sample. A stroke's `type` is a registry id like any other, so it carries its namespace:

```json
{
  "strokes": [
    {"type": "mxt:circle", "radius": 1, "color": [{"at": 0, "color": "#80c0ff"}, {"at": 1, "color": "#ffffff"}]},
    {"type": "mxt:polyline", "points": [[1, 0], [-0.809, 0.588], [0.309, -0.951], [0.309, 0.951], [-0.809, -0.588], [1, 0]], "color": "#ffd76a"}
  ]
}
```

A circle's optional `center` defaults to the controller, and both primitives start where the renderer starts them, so
where a stroke begins is also how it grows; a circle begins due east unless its `start_angle` (degrees past east,
positive towards +Z) says otherwise. **The namespace is required in the file** — a bare `"circle"` is
`minecraft:circle` to the mod and fails the whole definition — but the editor reads one anyway and writes it back as
`mxt:circle`, so a figure saved before the ids were namespaced loads and comes out fixed.

Editing:

* **Polyline** — click vertices, `Enter` / a right click / a double click ends the stroke, `Esc` drops it.
* **Circle** — press where the centre goes and drag out the radius. The centre is **wherever you press**, so a circle
  need not sit on the controller; the controller is a snap target of its own, so putting one there is still one click.
  A circle is grabbed later by dragging **its centre or its start point**, both of which are drawn on the board: the
  start point is also the rim, so dragging it out sets the radius and dragging it round turns the circle (snapping to
  the divisions when those are on).
* **Drag an existing vertex** to move it, `⇄` in the list reverses a stroke so it grows the other way, `✕` removes it,
  `Ctrl+Z` / `Ctrl+Y` undo and redo, and `Load from JSON` takes back either a bare stroke list or a block that holds one.
* **Snapping** is the three things that make a regular figure exact instead of nearly right: a 0.05 grid, the ring
  (r = 1), and the **N divisions of the circle** — N rays out of the controller with **angle 0 due east**, one every
  360/N degrees. The ring and the divisions beat the grid, so a vertex of a regular figure is not rounded twice, and a
  click near a ray keeps its radius and lands exactly on the ray. `rays` draws them, `Rays` writes them out as strokes
  (one two-point polyline each, out of the controller) — which is also the fastest way to author a spoke figure.
* **`Star N/K`** writes the {N/K} star the way `FormationShape` builds it — one closed polyline per cycle of the step —
  so `5/2` is `five`, `6/2` is `six` (two triangles), `8/2` is `eight` (two squares) and `K = 1` is the plain N-gon.
  `N-gon` adds one closed polyline; all three generators are undoable.
* **Paint** is per stroke and applies to what a stroke is drawn and grown as: `solid` (one hex) or `gradient` — a ramp
  **along the stroke's own length**, `at` 0 at its first point and 1 at its last, held outside the outermost stops.
  Both are the **same `color` field** in the file — a hex colour, or a list of stops — because a stroke is painted one
  way or the other and never both. There is no figure-wide colour to fall back to, so a stroke always carries paint and
  a fresh stroke is painted in the `new stroke colour`. The strip under the controls is that ramp (and the list carries
  a swatch of it): **click the strip to add a stop, drag a marker to move one**, `+ stop` / `− stop` for the middle and
  the ends, and select a stop to recolour it with the picker. A ramp is painted on the client in 32 flat steps rather
  than dot by dot, which is indistinguishable at the size of a dust quad.
* **`start` / `end`** are the window of the draw-in the selected stroke belongs to: the figure's progress is mapped
  onto it, so `start: 0.5` keeps a stroke for the second half and `start: 0.5, end: 1` on one stroke with `end: 0.5` on
  the next draws them one after another. A window has to open (`start` below `end`), so the two inputs never cross —
  the one being typed in wins and the other moves out of the way. `start_angle` is a circle's start in degrees and is
  disabled for a polyline, which starts at its first point.
* **`spacing` / `size`** are the stroke's own too: how many blocks apart its dusts sit and how big each one is drawn,
  standard (`0.33` and `2`) unless it says otherwise. A figure is therefore free to carry a sparse outer ring and a
  dense inner detail, and both fields are left out of the file while they are the standard ones.

What it shows, and why:

* **The growth**, because the game's rule is not a tool setting: every stroke is cut at its own share of the progress
  (its window, which is the whole draw-in unless it asks for less), so strokes written with the same window grow
  together. The slider is that progress, and **`Play` runs the draw-in the way the game plays it** —
  `duration_ticks` ticks at 20 a second, from the first point of the first stroke to the last of the last.
* **The animation's own knobs are preview-only.** `speed` is the list `0.1× 0.25× 0.5× 1× 1.5× 2× 3× 4× 6× 8×` (the
  default is the game's own 1×, which is why a `duration_ticks: 24000` draw-in is worth slowing down or, the other way,
  a 20-tick one worth speeding up), `loop` repeats it with a short hold on the finished figure so the restart is not a
  flicker, and the clock next to them reads off `elapsed / duration_ticks` — the ticks the game would have laid out by
  now. Changing the speed or the duration while it runs keeps the picture where it is instead of jumping. **None of the
  three is part of a figure and none of them is written to the file**; `duration_ticks` is the module's field and is.
* **The cost** — points per stroke are the mod's own `ceil(length in blocks / that stroke's spacing) + 1`, counted
  against the radius you type, so the readout is what one frame of that array asks the client for. The dots are painted
  at the dust quad size that stroke's `size` asks for, so `size` reads the way it will in game.
* **The lines animate too, not just the dust.** `lines` strokes each stroke from its own start to wherever the walk has
  reached — a circle is swept by angle, a polyline is walked by arc length, both cut at the stroke's window — so with
  `dust` off you are watching the figure being written rather than the figure being dusted. What has not been reached
  yet stays on as a **ghost** at the same place, which is what keeps a half-drawn figure editable and stops progress 0
  from being a blank board.
* **The problems** — a stroke with no length, and a stroke that wants the whole `MAX_POINTS_PER_STROKE = 2048` ceiling
  at this radius and its own spacing, after which its far end is dropped. A figure drawn past the ring is not one of
  them: it is drawn past the array's edge, which is what the axis labels are there to show.

**Nothing here is a plan.** `mxt:particle`'s `shape` names a `mxt:formation_shape`, and the mod reads exactly what this
page writes: the two stroke ids, `[x, z]` in units of the array's radius, `center`, `points`, `start_angle`, the
`start` / `end` window, `spacing`, `size`, and `color` — one hex, or the `{"at", "color"}` ramp stops the same field
takes. Only `radius` (the array's own) and the preview knobs belong to something other than the figure — they are
inputs here so the preview and the count are honest, and `radius` never reaches the file either. A stroke must carry
its paint, written one way or the other and never both or neither, the stops have to be written in ascending order,
the window has to open, a `start_angle` outside one turn either way is refused, and the spacing and size are bounded
like the module's used to be: any of those fails the whole definition at load.

`formation_figure_check.mjs` keeps the page honest: it runs the page's own script against a stub DOM and checks that its
geometry agrees with the mod — the vertex convention, all three built-in shapes rebuilt from the {N/K} rule, the
divisions and the ray snapping, the truncation at half progress, the windows (a stroke untouched before its window
opens, half a stroke half way through one), the start angles, the per-stroke `spacing` and `size` (the count at 0.33, 1
and 2, the fields written only when they are not the standard ones, and the load-time bounds), the animation (the clock
at half a 60-tick draw-in, the hold and the loop, the ramp re-anchoring when the speed changes mid-play), **the line
layer** (the stub canvas records every `arc` / `lineTo` with the style in force, so a half-drawn ring has to be half a
turn of arc, a walked stroke has to end on its own halfway point, and a finished one has to be whole with no ghost
left), the ramps and the JSON round trip (including the load-time refusals: a stroke with both a colour and a ramp, a
stroke with neither, a ramp with fewer than two stops, a window that never opens, an angle past one turn), and the point
counts the mod's own formation probe uses (52 points for the ring plus 78 for the pentagram at radius 8 and a spacing of
1, which is the probe's 130).
