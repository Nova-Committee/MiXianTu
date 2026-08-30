# Existing Item Bindings

MXT does not create logical datapack items. Physical items must be registered by Minecraft, a content mod, or KubeJS. Datapacks only attach MXT gameplay rules to those existing item IDs.

```text
KubeJS / mod item registry
        -> mxt:item_binding -> actions
        -> mxt:weapon_binding
        -> mxt:pill_binding
        -> mxt:technique_binding -> cultivation technique
```

- `mxt:item_binding` maps existing items to an ordered generic action list, executed after vanilla consumption finishes.
- `mxt:weapon_binding` maps one existing item to weapon-only fields.
- `mxt:pill_binding` maps one existing item to pill-only fields.
- `mxt:technique_binding` maps an existing book, jade slip, or other item to one `cultivation_technique`. Right-clicking it attempts to learn the technique; every learned technique remains enabled and contributes its passive effects.
- All four bindings may declare an optional `quality_group` reference to an `mxt:item_quality` tag. It selects the permitted quality group for that physical item; the tag's member order defines quality order, while an explicit stack component or forge result still determines the current quality.
- `conditions` is optional on every binding. Every matching binding condition and the current quality's condition must pass before the item can be used. Each entry may be an inline `EntityCondition`, or an object with `condition` and an optional translation-key `description`. Described entries are shown in the item tooltip in green when true and red when false. The check blocks right-click use, block interaction, attacks, data-driven item effects, weapon tick effects, technique learning, and binding-added weapon attributes.

Every binding uses the `items` matcher. It accepts one item ID, one item tag (such as `"#example:herbs"`), or a mixed array of both; one binding can therefore cover many physical items. When multiple bindings match an item, the matcher selects the highest-priority definition (all four binding types currently use priority `0`).

```json
// data/example/mxt/item_binding/fire_root_pellet.json
{
  "items": "kubejs:fire_root_pellet",
  "quality_group": "#example:group/pellet",
  "conditions": [{"condition": {"type": "mxt:realm", "realm": "example:foundation"}, "description": "condition.example.fire_root"}]
  "actions": [
    {
      "type": "mxt:grant_spirit_root",
      "spirit_root": "example:fire_root"
    }
  ]
}
```

```json
// data/example/mxt/weapon_binding/firebound_sword.json
{
  "items": ["kubejs:firebound_sword", "#example:fire_weapons"],
  "attack_damage": 8,
  "attack_speed": -2.4,
  "quality_group": "#example:group/firebound_weapon",
  "conditions": [{"type": "mxt:realm", "realm": "example:foundation"}],
  "use_action": {"type": "mxt:add_resource", "resource": "example:qi", "amount": 5},
  "attack_action": {"type": "mxt:target_action", "action": {"type": "mxt:damage", "amount": 3}},
  "tick_action": {"type": "mxt:no_op"}
}
```

```json
// data/example/mxt/pill_binding/returning_pill.json
{
  "items": "kubejs:returning_pill",
  "quality_group": "#example:group/pill",
  "conditions": [{"condition": {"type": "mxt:realm", "realm": "example:foundation"}, "description": "condition.example.pill"}],
  "on_consume": {"type": "mxt:heal", "amount": 4},
  "toxicity_gain": 10,
  "toxicity_threshold": 100,
  "toxicity_after_overdose": 25
}
```

```json
// data/example/mxt/technique_binding/fire_manual.json
{
  "items": "kubejs:fire_manual",
  "technique": "example:fire_manual",
  "quality_group": "#example:group/manual",
  "conditions": [{"type": "mxt:realm", "realm": "example:foundation"}],
}
```

`quality_group` must be a native item-quality tag reference prefixed with `#`. Its `values` order defines the group's quality order. When no explicit `mxt:item_quality` component or forge result exists, the last member not disabled by `mxt:disabled` becomes the default quality. An item cannot be used when its current quality is outside the group, the group has no usable member, a binding condition fails, or the quality's own `condition` fails.

`conditions` is optional on every binding. Each entry may be an inline `EntityCondition`, or an object with `condition` and an optional translation-key `description`. Described entries are shown in the item tooltip with a green `✓` when true or a red `✗` when false; the description text itself keeps its normal style.

`technique` is a required holder reference to `mxt:cultivation_technique`. Its own `learn_condition`, already-learned check, exclusive-tag conflict check, and event cancellation remain authoritative. A matching technique binding claims the item interaction even when learning fails, so the item's normal right-click behavior cannot bypass these checks.
