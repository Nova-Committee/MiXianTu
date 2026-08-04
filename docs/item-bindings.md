# Existing Item Bindings

MXT does not create logical datapack items. Physical items must be registered by Minecraft, a content mod, or KubeJS. Datapacks only attach MXT gameplay rules to those existing item IDs.

```text
KubeJS / mod item registry
        -> mxt:item_binding -> actions
        -> mxt:weapon_binding
        -> mxt:pill_binding
```

- `mxt:item_binding` maps existing items to an ordered generic action list, executed after vanilla consumption finishes.
- `mxt:weapon_binding` maps one existing item to weapon-only fields.
- `mxt:pill_binding` maps one existing item to pill-only fields.

Every binding uses the `items` matcher. It accepts one item ID, one item tag (such as `"#example:herbs"`), or a mixed array of both; one binding can therefore cover many physical items. When multiple bindings match an item, the matcher selects the highest-priority definition (all three binding types currently use priority `0`).

```json
// data/example/mxt/item_binding/fire_root_pellet.json
{
  "items": "kubejs:fire_root_pellet",
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
  "use_action": {"type": "mxt:add_resource", "resource": "example:qi", "amount": 5},
  "attack_action": {"type": "mxt:target_action", "action": {"type": "mxt:damage", "amount": 3}},
  "tick_action": {"type": "mxt:no_op"}
}
```

```json
// data/example/mxt/pill_binding/returning_pill.json
{
  "items": "kubejs:returning_pill",
  "on_consume": {"type": "mxt:heal", "amount": 4},
  "toxicity_gain": 10,
  "toxicity_threshold": 100,
  "toxicity_after_overdose": 25
}
```
