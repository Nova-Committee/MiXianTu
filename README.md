# MiXianTu

**English** | [简体中文](README-zh.md)

MiXianTu is a **cultivation mod framework**: it provides the **generic rules and runtime** that cultivation gameplay
needs — cultivation, aura environment, realms and resources, abilities, formations, tribulations, forging, alchemy,
economy and sects — without prescribing any particular setting or numbers.

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

- **[Documentation](https://docs.iafenvoy.com/docs/mod/mxt)**: full documentation for datapacks, KubeJS and Java
  development. **Still being written**, and will keep being updated.
- **[Datapack Visual Editor](https://datapack.mcdev.tech/)**: edit this mod's datapacks in the browser as a form, with
  field descriptions and registry completion, so you never have to write JSON by hand.

## What the Mod Provides

### For Players

- **Keybinds**: `C` toggles cultivation mode, `Z` opens the character information panel, `LAlt` shows the ability
  hotbar, `V` fires spirit power; "Swap Main Hand with Back Weapon Slot" is unbound by default. All of them can be
  changed in the controls settings.
- **Interface**: resource bars, an aura concentration HUD, and a hotbar shared by abilities and spirit power (number
  keys 1–9 select entries).
- **Blocks**: Spirit Crafting Table (uses aura recipes and deducts aura when the result is taken out), Forge Table,
  Exchange Station, Trade Station, Cheque Table, Display Stand, plus Spirit Stone Ore and Spirit Stone Block.
- **Items**: materials such as Lesser to Supreme Spirit Stones, Spirit Iron and Spirit Wood; generic items such as
  Spirit Ring, Spirit Stone Bag, Spirit Vessel, Identification Mirror, Cultivation Jade Slip, Blank Talisman Paper with
  Talisman Brush and Ink, Contract Scroll, Beast Taming Bell, Spirit Beast Bag, Formation Plate, Wooden and Stone
  Tokens, Realm Token and Recall Talisman.
- **Slots**: three Curios slots — Back Weapon, Belt Item and Cultivation Technique — swappable with the main hand by
  keybind.
- **Commands**: the `/mxt` family (registry validation, resource and aura queries, cultivation status, breakthrough,
  sect territory and more) and `/display` to show equipment.

The actual gameplay content — realm values, aura distribution, abilities, formations and recipes — is provided by
datapacks or content packs; installing the mod alone does not give you a complete progression.

### For Datapack / Mod / Modpack Authors

- **Datapacks**: define resources, realms, aura, abilities, formations, crafts, economy and other rules with native
  datapack registries; they are validated on load and synced to the client.
- **Actions and conditions**: five families — entity, bi-entity, block, item and damage — freely combinable in
  datapacks.
- **Numbers**: constants, exp4j expressions or structured number providers, able to read context variables such as level
  and resources.
- **Item integration**: binding tables hook **existing** items into weapons, pills, techniques, resources and aura fuel,
  which makes integration with other mods easy.
- **KubeJS**: extension points such as action callbacks and number providers; register items from a script first and let
  the binding tables handle them.
- **Java API**: interfaces for data table registration, attachments, networking, screens and client rendering.

## Module Status

- **✅ Done**: everything is implemented and usable right away; minor changes may still happen later.
- **🚧 In Progress**: only part of the feature set is done, the rest is still being developed.
- **🔲 Planned**: only data structures or assets exist, or there is only a development plan.

| Module                          | Status | Description                                                                                                                                                                                                                                                            |
|---------------------------------|:------:|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Datapack Core                   |   ✅    | Gameplay rules are described by datapacks: conditions, effects, number calculation, item matching and trigger timing can all be freely combined, and a single entry can be disabled at any time.                                                                       |
| Hotbar and Character UI         |   ✅    | Players can open the ability and spirit power hotbars with a key and cast directly from them, open the character information panel to check their own state, and choose which entries appear on the hotbar.                                                            |
| Resources                       |   ✅    | Numeric resources such as cultivation progress and spirit power can be defined; they regenerate by rule, are consumed by abilities and cultivation, and are drawn as resource bars on the HUD.                                                                         |
| Aura                            |   ✅    | The world has different aura concentrations per dimension, biome and block, changing over time and with formations; players can query the concentration at their position and see the result through particles, fog and the HUD.                                       |
| Cultivation and Realms          |   ✅    | Players can meditate to accumulate cultivation progress, faster where aura is dense; once the requirements are met they can break through to the next realm, with those requirements defined by datapacks.                                                             |
| Elements                        |   🚧   | Defines elements and the overcoming and adaptation relations between them, read by spirit roots, aura and other gameplay.                                                                                                                                              |
| Spirit Roots and Physiques      |   🚧   | Spirit roots and physiques can be granted to players, affecting cultivation and ability strength or directly providing passive attributes; how they are obtained and which ones exclude each other is defined by datapacks.                                            |
| Techniques                      |   🚧   | Players can learn cultivation techniques to gain active moves or passive bonuses, and exclusion tags can stop certain techniques from being learned together; a technique can be bound to any existing item as its carrier.                                            |
| Titles and Badges               |   🔲   | Titles are granted to players by condition and provide passive attributes; badges define display entries such as achievements or a compendium.                                                                                                                         |
| Abilities and Curses            |   🚧   | Abilities can be cast from the hotbar with a cost, cooldown, duration and target selection, and can also fire automatically on attacking, being hurt, killing and other timing; curses attach to a character, trigger periodically and can be removed by purification. |
| Formations                      |   🔲   | Players can build and activate formations; a formation keeps running by consuming resources, applies effects within its area and temporarily provides buffs/debuffs.                                                                                                   |
| Tribulations                    |   🔲   | A tribulation can be triggered on a realm breakthrough: it advances in phases, gets harder with the realm, and success or failure each run their own outcome.                                                                                                          |
| Creature Profiles and Contracts |   🔲   | Creature profiles define a creature's strength, inner core and drops; players can also sign a contract with a creature, letting the spirit beast follow and fight, be stored in a Spirit Beast Bag or recalled with a Beast Taming Bell.                               |
| Secret Realms                   |   🔲   | Players can enter a separate secret realm with a Realm Token; a realm has player count and time limits, and is cleaned up and returns players to where they came from when it expires.                                                                                 |
| Spirit Crafting Table           |   ✅    | Crafting at the Spirit Crafting Table costs aura in addition to materials, deducted when the result is taken out.                                                                                                                                                      |
| Forging                         |   ✅    | At a Forge Table, several materials are hammered into a result following a blueprint; different tools unlock different methods, and the quality of the result depends on the process and the number of steps.                                                          |
| Alchemy                         |   🚧   | Recipes combine materials, aura, temperature and furnace tier to produce pills, with both the success and failure outcomes decided by the recipe; taking a pill applies its effect, and taking too many accumulates toxicity.                                          |
| Spirit Herbs                    |   🔲   | Defines binding and quality for spirit herb items, which serve as materials for alchemy and gathering gameplay.                                                                                                                                                        |
| Item Binding                    |   🚧   | Brings existing items into gameplay: attach passive behavior, weapon damage and attack speed to any item, or bind abilities that fire on right-click use and on attack.                                                                                                |
| Item Quality                    |   🚧   | Items can carry a quality shown in their tooltip, can be grouped and sorted by quality, and a quality group can also be read as a condition by other gameplay.                                                                                                         |
| Artifacts                       |   🚧   | Artifacts can store spirit power, carry the player in flight or provide abilities, and come with a refining gameplay.                                                                                                                                                  |
| Economy                         |   ✅    | Items can be defined as currency with a value, supporting exchange and change; players can trade directly with each other, or use trade stations and cheques to settle transactions.                                                                                   |
| Sects                           |   🔲   | A sect has members and ranks, contribution accumulation, sect tasks and exchange, and can claim and release territory.                                                                                                                                                 |
| Curios Slots                    |   🚧   | Players have three Curios slots — Back Weapon, Belt Item and Cultivation Technique — rendered on the character, and swappable with the main hand by keybind.                                                                                                           |

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
