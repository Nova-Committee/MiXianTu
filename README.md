# MiXianTu

**English** | [简体中文](README-zh.md)

MiXianTu is a **cultivation mod framework**: it provides the **generic rules and runtime** that cultivation gameplay
needs — cultivation, aura environment, realms and resources, abilities, formations, tribulations,
economy — without prescribing any particular setting or numbers.

The framework includes generic items and blocks. Datapacks, KubeJS and content mods supply progression, balance values
and gameplay definitions, while MiXianTu's data tables and item bindings connect them to the framework.

- **Players**: installing the mod on its own only gives you framework items, Curios slots, HUD and commands; what you
  can actually play depends on the datapack or content pack you use.
- **Content and mod authors**: define your own cultivation rules, aura distribution, abilities, formations, crafts and
  item bindings with datapacks, KubeJS or the Java API, without changing the mod itself.

**The project is still in development. Datapack formats and other interfaces are not final, and updates may no longer be
compatible with old saves or old datapacks. Changes that can cause crashes or data loss will be called out explicitly in
the changelog.**

## Links

- **[Documentation](https://docs.iafenvoy.com/docs/mod/mxt)**: player, datapack, KubeJS and Java development guides.
- **[Datapack Visual Editor](https://datapack.mcdev.tech/)**: edit this mod's datapacks in the browser as a form, with
  field descriptions and registry completion, so you never have to write JSON by hand.

## Features

### For Players

- **Keybinds**: `C` toggles cultivation mode, `Z` opens the character information panel, `R` opens the wheel and `V`
  activates the selected entry. Arrow keys switch wheel pages. "Swap Main Hand with Back Weapon Slot" is unbound by
  default. All keys can be changed in the controls settings.
- **Interface**: a configurable twelve-slot main wheel, additional ability pages based on held items, artifacts and
  contracted creatures, resource bars, and an aura HUD.
- **Blocks**: Spirit Crafting Table, Exchange Station, Trade Station, Cheque Table, Display Stands, Spirit Stone Ore and
  Spirit Stone Block. Spirit herb definitions add growth and harvesting rules to existing blocks.
- **Items**: materials such as Lesser to Supreme Spirit Stones, Spirit Iron, Spirit Wood and Cinnabar; generic items
  such as Spirit Ring, Spirit Stone Bag, Spirit Vessel, Identification Mirror, Technique Book, Jade Slip, Blank Talisman
  Paper, Talisman Brush, Talisman, Contract Scroll, Beast Taming Bell, Spirit Beast Bag, Formation Plate, Wooden and
  Stone Tokens, and Secret Realm Token.
- **Talismans**: inscribed carriers store aura and invoke their abilities when charged or used. Definitions control
  targeting, costs, consumption and durability; shared brushes, pigments and blank paper are included.
- **Display Stand**: displays items and activates charged talismans from the stand's own position.
- **Slots**: Curios equipment slots with character rendering and keybinds for swapping equipment with the main hand.
- **Commands**: the `/mxt` family (registry validation, resource and aura queries, cultivation status, breakthrough
  and more) and `/display` to show equipment.

The actual gameplay content — realm values, aura distribution, abilities, formations and recipes — is provided by
datapacks or content packs; installing the mod alone does not give you a complete progression.

### For Datapack / Mod / Modpack Authors

- **Datapacks**: define resources, realms, aura, abilities, formations, crafts, economy and other rules with native
  datapack registries; they are validated on load and synced to the client.
- **Item integration**: bind existing items to abilities, pills, resources and aura fuel. Item components carry
  per-stack quality, elements, pill data and technique reading progress; spirit stones can receive aura from their
  holder.
- **KubeJS**: extension points such as action, condition, cost, number provider, target selector, trigger matcher and
  loot callbacks, the `MxtEvents` server lifecycle events, and the `MxtTriggers` custom trigger signals a script can
  publish and subscribe to; register items from a script first and let the binding tables handle them.
- **Java API**: interfaces for data table registration, attachments, networking, screens and client rendering.

See the [Documentation](https://docs.iafenvoy.com/docs/mod/mxt/tutorial/) for more information.

## Core Mod Content

- **✅ Done**: everything is implemented and passed basic tests; minor changes may still happen later.
- **🚧 In Progress**: only part of the feature set is done, the rest is still being developed.
- **🔲 Planned**: only data structures or assets exist, or there is only a development plan.

| Module                         | Status | Description                                                                                                                                                                      |
|--------------------------------|:------:|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Datapack Core                  |   ✅    | Define gameplay through composable conditions, actions, number providers, item matchers and triggers, with load-time validation and conditional entries.                         |
| Wheel and Character UI         |   ✅    | Configure a twelve-slot main wheel, browse equipment and contract ability pages, and view character information, resources and aura.                                             |
| Resources                      |   ✅    | Define numeric resources such as cultivation progress and spirit power, with regeneration, consumption rules and HUD bars.                                                       |
| Aura                           |   ✅    | Configure aura concentration by dimension, biome and block, with changes over time, formation interactions, queries and visual feedback.                                         |
| Cultivation and Realms         |   ✅    | Accumulate cultivation progress through meditation and advance through realms according to datapack requirements; optionally integrate lifespan changes with Reincarnation.      |
| Elements                       |   ✅    | Define elements and their overcoming and adaptation relations for spirit roots, aura and other systems.                                                                          |
| Spirit Roots and Physiques     |   ✅    | Grant cultivation modifiers, ability effects and passive attributes, with configurable acquisition requirements, exclusions and activation.                                      |
| Techniques                     |   ✅    | Learn active abilities and passive bonuses from technique books, with exclusion rules controlling which techniques can be studied together.                                      |
| Abilities and Curses           |   ✅    | Configure ability costs, cooldowns, duration, targeting and event triggers, plus visual effects and curses that trigger periodically or can be purified.                         |
| Formations                     |   ✅    | Build and activate formations that consume resources, apply area effects and temporarily grant buffs or debuffs.                                                                 |
| Tribulations                   |   ✅    | Run sequences of actions, delays and condition checks during breakthroughs, with scaling difficulty and separate success and failure outcomes.                                   |
| Souls                          |   🚧   | Preserve entity snapshots as translucent apparitions, with configurable death generation, command creation, reclaiming and expiry.                                               |
| Creature Profiles & Contracts  |   🚧   | Define creature attributes, inner cores and mastery progression; contract creatures and command them to follow, wander, hold position or return.                                 |
| Secret Realms                  |   🚧   | Create instance dimensions with configurable generation, structures, landing positions, limits and entry rules; edit planar portals, summon them with tokens and export layouts. |
| Spirit Crafting Table          |   ✅    | Craft with recipes that consume both materials and aura, charging aura when the result is taken.                                                                                 |
| Pills and Medicinal Properties |   🚧   | Define pills, medicinal properties, usage limits, cooldowns and toxicity, alongside shared heat sources.                                                                         |
| Spirit Herbs                   |   ✅    | Add growth, age and harvesting rules to existing blocks, with fruit quality determined by herb age.                                                                              |
| Item Binding                   |   ✅    | Attach passive behavior, attribute modifiers and abilities triggered by use or attack to existing items.                                                                         |
| Talismans                      |   ✅    | Provide carriers, aura infusion, invocation rules and shared tools, with configurable targeting, costs, consumption and durability.                                              |
| Quality                        |   ✅    | Define quality tiers and resolve an item's quality from its component, its definition or a registry default.                                                                     |
| Artifacts                      |   ✅    | Give existing items aura storage and abilities, and configure flying artifacts with speed, seats, fuel and appearance.                                                           |
| Economy                        |   ✅    | Define item currencies and denominations, exchange money with change, and settle player trades through stations and cheques.                                                     |
| Curios Slots                   |   ✅    | Equip back weapons, belt items, techniques and artifacts, with character rendering and keybinds for main-hand swapping.                                                          |
| Friend and Foe Identification  |   ✅    | Maintain temporary or persistent friend lists and let mods or scripts provide relationship checks.                                                                               |

Reincarnation is optional. When installed, it provides lifespan, aging, reincarnation and past-life queries, and data
packs can configure lifespan changes through breakthrough success actions. Cultivation works without it. Reincarnation
controls attachment retention through its tag; data clears by default.

## Addons

The following optional addons provide workstation blocks and production workflows. Each requires MiXianTu and can be
installed independently of the others. Shared tools, materials, carriers and outputs remain in the core mod, and addon
items appear in the MiXianTu creative tab. Datapacks or content packs define the production recipes and parameters.

| Addon                                                                               | Features                                                                                                                   |
|-------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------|
| [Alchemy Workstations](https://docs.iafenvoy.com/en/docs/mod/mxt/addons/alchemy/)   | Modular furnaces, walls and input/output bins, with furnace and wall definitions, temperature control and alchemy recipes. |
| [Forging Workstations](https://docs.iafenvoy.com/en/docs/mod/mxt/addons/forging/)   | Forge tables, forging workflows, blueprints, methods and tool/blueprint bindings.                                          |
| [Talisman Workstations](https://docs.iafenvoy.com/en/docs/mod/mxt/addons/talisman/) | Drawing workstations, guided talisman tracing, stroke scoring, quality grading and drawing recipes.                        |

## FAQ

### What dependencies are required?

| Dependency                                         | Requirement                                     |
|----------------------------------------------------|-------------------------------------------------|
| Jupiter, Saturn, ApricityUI                        | Required; install separately.                   |
| Curios, Ponder, Catnip and other bundled libraries | Included; no separate installation needed.      |
| Reincarnation                                      | Optional; lifespan and reincarnation.           |
| KubeJS                                             | Optional; scripted content.                     |
| JEI, Jade                                          | Optional; recipe browsing and in-game tooltips. |

Install any dependencies required by the optional mods you choose.

### Why does nothing change in game after installing the mod?

The mod only provides the framework, generic items, slots, HUD and commands; it contains no realm values, abilities or
recipes. You need a datapack or content pack (including content written with KubeJS) before real gameplay appears.

## Licensing

The source code is open source under the **AGPL-3.0**. All resource files in `assets/` directories are **All Rights
Reserved** and are excluded from the source code license.

Because of AGPL's copyleft requirements, addons that form derivative or combined works with MiXianTu generally must
also be distributed under AGPL-3.0. To use a different license, including ARR, contact the author for separate
authorization.

## Got Ideas, Want to Contribute, or Have More Questions?

Join our [Discord](https://discord.gg/NDzz2upqAk) to discuss.
