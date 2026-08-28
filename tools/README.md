# Bendable Cuboids Animation Tools

`mxt_bendable_cuboids_animation.js` is a local Blockbench companion plugin for
GeckoLib Animation Utils. It is intended for Player Animation Library (PAL)
and BendableCuboids animations used by MiXianTu.

## Installation

1. Install **GeckoLib Animation Utils** from Blockbench's plugin browser.
2. Use `File -> Plugins -> Load Plugin from File` and select
   `bendable-cuboids-animation.js`.
3. Open a GeckoLib Animation project, select a bone in the timeline, then use
   `Animation -> Insert PAL Bend Keyframe`.

The timeline gains a `Bend (PAL)` channel. Its keyframe panel exposes one
field named `旋转角度` (bend angle) rather than X/Y/Z. Player Animation Library
passes only the bend track's X keyframes to its single-float
`PlayerAnimBone.bend` value, which BendableCuboids consumes. The plugin stores
the visible value as X and automatically writes Y/Z as `0` for GeckoLib's
vector JSON format.

## Export

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

## Project Persistence

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

## Preview Limits

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
