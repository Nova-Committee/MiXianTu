# MiXianTu

**English** | [简体中文](README-zh.md)

MiXianTu is a **cultivation mod framework**: it provides the **generic rules and runtime** that cultivation gameplay
needs — cultivation, aura environment, realms and resources, abilities, formations, tribulations, forging, alchemy,
economy — without prescribing any particular setting or numbers.

The actual items, blocks and recipes are provided by datapacks, KubeJS or other content mods, and MiXianTu's data tables
and binding tables give them gameplay.

- **Players**: installing the mod on its own only gives you framework items, Curios slots, HUD and commands; what you
  can actually play depends on the datapack or content pack you use.
- **Content and mod authors**: define your own cultivation rules, aura distribution, abilities, formations, crafts and
  item bindings with datapacks, KubeJS or the Java API, without changing the mod itself.

**The project is still in development. Datapack formats and other interfaces are not final, and updates may no longer be
compatible with old saves or old datapacks. Changes that can cause crashes or data loss will be called out explicitly in
the changelog.**

## Links

- **[Documentation](https://mxt.iafenvoy.com/)**: full documentation for datapacks, KubeJS and Java
  development. **Still being written**, and will keep being updated.
- **[Datapack Visual Editor](https://datapack.mcdev.tech/)**: edit this mod's datapacks in the browser as a form, with
  field descriptions and registry completion, so you never have to write JSON by hand.
- **[AGENTS.md](AGENTS.md)**: the house rules to read before an AI (or any other contributor) changes this
  repository — verification commands, a code map, the code conventions and the documentation sync checklist.

## What the Mod Provides

### For Players

- **Keybinds**: `C` toggles cultivation mode, `Z` opens the character information panel, `R` opens the wheel (the one
  way abilities and spirit power are triggered), the arrow keys turn its pages; "Swap Main Hand with Back Weapon
  Slot" is unbound by default. All of them can be changed in the controls settings.
- **Interface**: the wheel is a main wheel plus pages read from what you carry, all strung together by one continuous
  cell numbering - you arrange the main wheel's twelve cells yourself, and the pages behind it come from your main hand,
  off hand, artifacts and the contract beast your Beast Taming Bell is tuned to, opening another page whenever one is
  not enough; plus resource bars and an aura HUD.
- **Blocks**: Spirit Crafting Table (uses aura recipes and deducts aura when the result is taken out), Forge Table,
  Exchange Station, Trade Station, Cheque Table, Display Stand, plus Spirit Stone Ore and Spirit Stone Block.
- **Items**: materials such as Lesser to Supreme Spirit Stones, Spirit Iron, Spirit Wood and Cinnabar; generic items
  such as Spirit Ring, Spirit Stone Bag, Spirit Vessel, Identification Mirror, Cultivation Jade Slip, Blank Talisman
  Paper with Talisman Brush and Ink, Talisman (written with a sigil, poured full of aura, and spent the moment it
  fires), Contract Scroll, Beast Taming Bell, Spirit Beast Bag, Formation Plate, Wooden and Stone Tokens, Secret Realm
  Token and Recall Talisman.
- **Talismans**: a Talisman Brush writes ability definitions onto a carrier, and pouring aura in loads it; the
  moment the bill is full every inscribed ability fires and one carrier is spent. A carrier that would not fire -
  or one billed nothing at all - fires from a right-click instead, and a definition may declare durability for its
  carrier (`durability` / `consume`), which spends wear instead of whole carriers until the carrier breaks and
  hands back whatever it never burned; `costs` is what the invocation takes from the holder and `quality` grades
  the paper it is written on.
- **Display Stand**: any item can be put on one to be shown; a talisman there fires the moment a spirit burst
  fills it, **from the stand's own position** - the formulas' `block_x`/`block_y`/`block_z`, the centre of an area
  selector and where a projectile is launched from all use the stand, not whoever filled it.
- **Slots**: three Curios slots — Back Weapon, Belt Item and Cultivation Technique — swappable with the main hand by
  keybind.
- **Commands**: the `/mxt` family (registry validation, resource and aura queries, cultivation status, breakthrough
  and more) and `/display` to show equipment.

The actual gameplay content — realm values, aura distribution, abilities, formations and recipes — is provided by
datapacks or content packs; installing the mod alone does not give you a complete progression.

### For Datapack / Mod / Modpack Authors

- **Datapacks**: define resources, realms, aura, abilities, formations, crafts, economy and other rules with native
  datapack registries; they are validated on load and synced to the client.
- **Actions and conditions**: five families — entity, bi-entity, block, item and damage — freely combinable in
  datapacks.
- **Numbers**: constants, exp4j expressions or structured number providers, able to read context variables such as level
  and resources.
- **Item integration**: binding tables hook **existing** items into weapons, pills, resources and aura fuel,
  which makes integration with other mods easy; items implementing `AuraItemAccess` (spirit stones) can additionally be
  charged by holding them down and pouring the holder's own aura into them. Per-stack additions carry no binding
  object: the quality chain, element, pill data, technique reading, forging methods and blueprints are components
  written onto the stack itself, layered on top of whatever a definition grants.
- **KubeJS**: extension points such as action, condition, cost, number provider, target selector, trigger matcher and
  loot callbacks, the `MxtEvents` server lifecycle events, and the `MxtTriggers` custom trigger signals a script can
  publish and subscribe to; register items from a script first and let the binding tables handle them.
- **Java API**: interfaces for data table registration, attachments, networking, screens and client rendering.

## Module Status

- **✅ Done**: everything is implemented and passed basic tests; minor changes may still happen later.
- **🚧 In Progress**: only part of the feature set is done, the rest is still being developed.
- **🔲 Planned**: only data structures or assets exist, or there is only a development plan.

| Module                        | Status | Description                                                                                                                                                                                                                                                                                                                       |
|-------------------------------|:------:|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Datapack Core                 |   ✅    | Gameplay rules are described by datapacks: conditions, effects, number calculation, item matching and trigger timing can all be freely combined, and a single entry can be disabled at any time.                                                                                                                                  |
| Wheel and Character UI        |   ✅    | Abilities (artifact skills included) and spirit power share one wheel: the main wheel's twelve cells are yours to arrange, the pages behind it follow what you carry and the Beast Taming Bell in hand, and holding `R` uses the cell you point at.                                                                               |
| Resources                     |   ✅    | Numeric resources such as cultivation progress and spirit power can be defined; they regenerate by rule, are consumed by abilities and cultivation, and are drawn as resource bars on the HUD.                                                                                                                                    |
| Aura                          |   ✅    | The world has different aura concentrations per dimension, biome and block, changing over time and with formations; players can query the concentration at their position and see the result through particles, fog and the HUD.                                                                                                  |
| Cultivation and Realms        |   ✅    | Players meditate to gather cultivation progress, faster where aura is dense, and break through to the next realm once the datapack requirements are met; realms and packs grant lifespan, and running out means death or rebirth as configured.                                                                                   |
| Elements                      |   ✅    | Defines elements and the overcoming and adaptation relations between them, read by spirit roots, aura and other gameplay.                                                                                                                                                                                                         |
| Spirit Roots and Physiques    |   ✅    | Spirit roots and physiques can be granted to players, affecting cultivation, ability strength or passive attributes; exclusions are defined by datapacks, and a held one can be switched off without being given up.                                                                                                              |
| Techniques                    |   🚧   | Learning a cultivation technique grants active moves or passive bonuses, and exclusion tags stop certain techniques from being learned together; a manual is an item with the `mxt:technique` component, and the mod generates a jade slip per technique.                                                                         |
| Abilities and Curses          |   ✅    | Abilities can be cast with a cost, cooldown, duration and target selection, and can also fire automatically on attacking, being hurt or killing; curses attach to a character, trigger periodically and can be removed by purification.                                                                                           |
| Formations                    |   ✅    | Players build and activate formations; a formation keeps running by consuming resources, applies effects within its area and temporarily provides buffs/debuffs.                                                                                                                                                                  |
| Tribulations                  |   ✅    | A tribulation can be triggered on a realm breakthrough: it consumes a timeline of beats (an action, an idle wait, or a wait for a condition), gets harder with the realm and the local aura, and success or failure each run their own outcome.                                                                                   |
| Creature Profiles & Contracts |   🚧   | Creature profiles define a creature's strength, inner core and the action it runs on spawn; players can sign a contract with a creature and order it to follow, wander, hold or come back with the Beast Taming Bell, or carry it in a Spirit Beast Bag.                                                                          |
| Secret Realms                 |   🚧   | Each entry opens an instance dimension on demand from a secret realm definition, with its own border, generation, structures and landing spot, claimable terrain, member and time limits, and enter/exit conditions.                                                                                                              |
| Spirit Crafting Table         |   ✅    | Crafting with a spirit crafting recipe at the Spirit Crafting Table costs aura in addition to materials, deducted when the result is taken out.                                                                                                                                                                                   |
| Forging                       |   ✅    | At a Forge Table, several materials are hammered into a result following a blueprint; different tools unlock different methods, and the quality of the result depends on the process and the number of steps.                                                                                                                     |
| Alchemy                       |   🔲   | An alchemy recipe describes the inputs, aura, temperature, furnace tier and duration that settle into a result or a failure; pills apply their effect, and too many accumulate toxicity.                                                                                                                                          |
| Spirit Herbs                  |   🔲   | Defines binding and quality for spirit herb items, which serve as materials for alchemy and gathering gameplay.                                                                                                                                                                                                                   |
| Item Binding                  |   🚧   | Brings existing items into gameplay: attach passive behavior and vanilla attribute modifiers to any item, or bind abilities that fire on right-click use and on attack.                                                                                                                                                           |
| Talismans                     |   🚧   | A Talisman Brush inscribes ability definitions onto a carrier (one carrier can hold several); holding right-click until it is full fires them, spends a carrier or the wear a definition declares, and starts the item cooldown. A definition may also declare a price and a tier.                                                |
| Quality                       |   ✅    | Items carry a quality shown in their tooltip; a quality chain fixes the ladder, its default tier and each step's price, definitions declare a default and a stack component overrides it.                                                                                                                                         |
| Artifacts                     |   🚧   | Items become artifacts via `artifact`: `items` claims them, `spirit_capacity` sets a per-aura ceiling, and `abilities` names what carrying it grants. An artifact may declare a mount (speed, seats, pose) that a technique-granted flying skill picks up from either hand, while storage and upkeep stay ordinary ability types. |
| Economy                       |   ✅    | Items can be defined as currency with a value, supporting exchange and change; players can trade directly with each other, or use trade stations and cheques to settle transactions.                                                                                                                                              |
| Curios Slots                  |   ✅    | Players have Curios slots for a back weapon, a belt item and four artifacts, rendered on the character and swappable with the main hand by keybind.                                                                                                                                                                               |
| Friend and Foe Identification |   ✅    | Every player keeps a list of the players they treat as their own, for the session or saved with the world; other mods or scripts can answer the same question through a TriState event asked by player id.                                                                                                                        |

## FAQ

### Why does nothing change in game after installing the mod?

The mod only provides the framework, generic items, slots, HUD and commands; it contains no realm values, abilities or
recipes. You need a datapack or content pack (including content written with KubeJS) before real gameplay appears.

### What dependencies are required?

Jupiter is a required dependency, and every other required dependency is bundled inside the mod. KubeJS is only needed
if you want to register content from scripts. JEI and Jade are optional compatibility mods, and the game works fine
without them.

### How do I disable a piece of content temporarily?

Add the entry to the `mxt:disabled` tag; disabled definitions stop taking part in gameplay, and you do not have to
delete any datapack files.

### Can I make my own content pack and distribute it?

You can build content packs on this framework, and distributing them is not restricted in any way.

**NOTE**: the mod's code and assets are **All Rights Reserved**, and commercial distribution (such as a paid
server) requires extra permission — see LICENSE for details.

## Got Ideas, Want to Contribute, or Have More Questions?

Join our [Discord](https://discord.gg/NDzz2upqAk) to discuss.
