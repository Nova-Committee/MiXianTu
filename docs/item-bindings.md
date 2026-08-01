# Data-Driven Items

Logical items are split by category while the runtime uses one common factory:

```text
mxt:item_binding -> Minecraft Item -> mxt:item | mxt:pill | mxt:weapon -> mxt:item_effect
```

- `mxt:item`: other logical items.
- `mxt:pill`: logical pill items.
- `mxt:weapon`: logical weapon items.
- `mxt:item_effect`: reusable effect definitions, including `weapon`, `pill`, and `spirit_root`.
- `mxt:item_binding`: the only bridge to Minecraft's physical `Item` registry.

All three logical item registries use `ItemDefinition`. A created stack stores both
the category and its definition ID in `mxt:item_definition`, so the same ID can
exist in different categories without being confused at runtime.

## Pack layout

```text
data/<namespace>/mxt/item/<id>.json
data/<namespace>/mxt/pill/<id>.json
data/<namespace>/mxt/weapon/<id>.json
data/<namespace>/mxt/item_effect/<id>.json
data/<namespace>/mxt/item_binding/<id>.json
assets/<namespace>/items/mxt/<id>.json
```

For example, a weapon using `minecraft:diamond_sword` as its physical item:

```json
// data/example/mxt/item_effect/fire_sword_weapon.json
{
  "type": "weapon",
  "attack_damage": 8,
  "attack_speed": -2.4
}
```

```json
// data/example/mxt/weapon/fire_sword.json
{
  "effects": ["example:fire_sword_weapon"]
}
```

```json
// data/example/mxt/item_binding/fire_sword.json
{
  "item": "minecraft:diamond_sword",
  "definition": {
    "registry": "mxt:weapon",
    "id": "example:fire_sword"
  }
}
```

If `model` is omitted, `example:fire_sword` uses model ID
`example:mxt/fire_sword`, supplied by
`assets/example/items/mxt/fire_sword.json`.

## Item Matcher

The `items` field accepts one value or an array. Strings retain the original
meaning: physical item IDs and `#` item tags. Objects select a data-driven item
by category and definition ID; strings and objects can be mixed in one array.

```json
{
  "items": [
    "minecraft:stick",
    "#minecraft:swords",
    { "registry": "mxt:weapon", "id": "example:fire_sword" },
    { "registry": "mxt:pill", "id": "example:healing_pill" }
  ]
}
```

The only accepted object registries are `mxt:item`, `mxt:pill`, and
`mxt:weapon`. Item bindings always require the category-qualified object form.

## Creating stacks

Use a category-qualified reference for every stack creation:

```java
var reference = new ItemDefinitionReference(
        ItemDefinitionRegistry.WEAPON,
        Identifier.parse("example:fire_sword")
);
ItemBindingService.create(Items.DIAMOND_SWORD, reference);
```

## Creative tabs

The `MiXianTu Items`, `MiXianTu Pills`, and `MiXianTu Weapons` creative tabs
iterate their respective datapack registries in native registry order. Disabled
definitions and definitions without exactly one physical item binding are not
shown.

The general-items tab creates the physical item without an
`mxt:item_definition` component and therefore uses that item's normal model.
The pill and weapon tabs create category-qualified bound stacks, including the
definition model and weapon attribute components.
