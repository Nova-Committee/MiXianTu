# Directory Structure

> Put code with the layer that owns its contract, not with the first caller that needs it.

## Source Sets

`build.gradle` establishes the authoritative layout:

```text
src/
|-- main/
|   |-- java/com/iafenvoy/mxt/     production mod code
|   `-- resources/                  hand-authored assets, data, metadata
|-- generated/resources/            datagen output added to main resources
`-- test-mod/
    |-- java/com/iafenvoy/mxt/testmod/  independent mxt_test development mod
    `-- resources/                      test datapacks and client fixtures
```

Do not put development fixtures in `src/main/resources`. `testMod` has its own compile/runtime classpaths and is attached to `runTestClient` and `runTestServer` by the Gradle configuration.

## Package Ownership

| Package | Owns | Representative anchors |
| --- | --- | --- |
| root `com.iafenvoy.mxt` | Mod identity and top-level registration composition. | `MiXianTu`, `MiXianTuClient` |
| `registry` | Resource keys, native datapack registry creation, fixed registries, and DeferredRegister wiring. | `MxtResourceKeys`, `MxtDatapackRegistries`, `MxtRegistries`, `MxtAttachments` |
| `data` | Decoded definitions, `Codec`/`MapCodec` contracts, actions, conditions, costs, and value objects. | `Ability`, `Resource`, `EntityAction`, `EntityCondition`, `Cost` |
| `attachment` | Entity/chunk/level state containers and their persistence/sync codecs. | `ResourceHolderAttachment`, `CultivationAttachment`, `CurseHolderAttachment` |
| `runtime` | Server decisions, validation, orchestration, transactions, lifecycle caches, and world adapters. | `AbilityService`, `ResourceTransactions`, `ServerCache`, `RuntimeDimensionService` |
| `network` and `network.payload` | Payload schemas, registration, and thin side-specific handlers. | `NetworkManager`, `ServerNetworkHandler`, `AbilityActionC2SPayload` |
| `event` | Typed pre/post extension points around runtime transactions. | `AbilityUseEvent`, `ResourceConsumeEvent`, `ForgingEvent` |
| `compat` | Optional-mod adapters and deliberately narrow public extension boundaries. | `compat.kubejs.MxtKubeJsApi`, callback adapters, JEI/Jade integrations |
| `config` | Jupiter-backed client/server configuration definitions; consumers still enforce logical-side and authority rules. | `MxtClientConfig`, `MxtServerConfig` |
| `item`, `recipe`, `loot`, `advancement`, `command` | Minecraft-facing adapters that delegate gameplay rules to definitions and runtime services. | `MxtCommand`, registered items and serializers |
| `particle` | Common particle option/type data that can be registered without loading client render classes. | `SpiritWispParticleOptions`, `SpiritWispParticleType`, `MxtParticleTypes` |
| `render`, `screen`, client handlers | Client input and presentation of synchronized state, including particle providers. | resource-bar renderers, screens, `ClientNetworkHandler` |
| `mixin` | Narrow accessors for Minecraft internals that cannot be reached through a supported API; no gameplay policy. | `ClientInputAccessor`, `MinecraftServerRuntimeAccessor` |
| `util` | Reusable, domain-neutral codec, formula, Holder, matcher, and sync primitives. | `CollectionCodecs`, `RegistryCodecs`, `FormulaContext`, `ShouldSyncAttachment` |

Do not move state mutation into `data`, registry bootstrapping into a service, or business validation into a screen/payload record. Optional integrations must call public runtime operations rather than reach through to attachment internals.

## Dependency Direction

The normal server flow is:

```text
datapack JSON -> data Codec -> native registry Holder
client intent -> payload -> server handler -> runtime service/stateful owner
runtime service/stateful owner -> attachment/data component/world state -> sync
sync/native registry -> client state -> render or screen
```

Apply these rules:

- `registry` wires types and keys; it must not become a second gameplay service layer.
- `data` definitions may describe behavior through registered `action`, `condition`, and `Cost` abstractions. They remain decoded values, not live state owners.
- `runtime` may depend on definitions, Holders, attachments, events, and Minecraft server APIs. It owns authoritative sequencing and mutation.
- `network` handlers resolve current server objects and delegate domain decisions to the owning service or stateful menu. A narrow, single-owner adapter operation may remain local, as the current Curios back-slot swap does, but it is not a precedent for duplicating reusable gameplay rules.
- client presentation may read client-synchronized registries, attachments, or snapshots, but must not decide authoritative costs, rewards, eligibility, or world changes.
- common/server packages must not load client-only classes. Put renderer registration and graphical behavior in client packages/events.

## Organizing a New Module

Add only the layers the feature actually needs:

1. Put a reloadable content schema in `data/<domain>/` and register it through `MxtResourceKeys` and `MxtDatapackRegistries`.
2. Put Java-owned polymorphic implementations in the relevant `data` hierarchy and a focused `Mxt...` fixed registry.
3. Put saved owner state in `attachment`, ItemStack-owned results in a data-component record, and server-lifetime derived state in `runtime`.
4. Put authoritative operations in `runtime/<domain>/...Service` or a transaction class.
5. Add the smallest payload needed for user intent, with a thin handler in `network`.
6. Add `mxt_test` definitions and executable startup/command coverage in `src/test-mod`.

Do not create a package merely to reserve a future architecture. Follow an existing vertical slice such as ability, cultivation, forging, or resource.

## Naming and Shape

- Registries and registration holders use `Mxt...` names: `MxtCosts`, `MxtDataComponents`.
- Reloadable content types use domain nouns: `Ability`, `Formation`, `Resource`.
- Mutable saved state uses the `Attachment` suffix and exposes mutation methods that maintain dirty/sync invariants.
- Authoritative orchestration uses `Service` or `Transactions`; transport schemas end in `C2SPayload` or `S2CPayload`.
- Use records for decoded/value results when their invariants can be enforced at construction. Use enums for bounded failure/state taxonomies.
- Prefer stable domain packages over generic `manager`, `helper`, or `common` dumping grounds. A utility belongs in `util` only when it is not the owner of gameplay policy.

## Resource and Test Placement

- Main datapacks use `src/main/resources/data/<namespace>/...`; generated output belongs only under `src/generated/resources`.
- Native MXT definitions use `data/<namespace>/mxt/<registry>/<id>.json`.
- Native MXT tags use `data/<namespace>/tags/mxt/<registry>/<tag>.json`.
- Test definitions use the `mxt_test` namespace and should exercise the current Codec, Holder, tag, and runtime consumer together.
- `MxtTestMod` startup audits are appropriate for registry/Codec and lifecycle invariants; `MxtTestCommands` supplies explicit playable flows. The Java plugin creates its conventional `test` source set, but this repository has no `src/test` tree or JUnit dependency/tests; gameplay verification is implemented in `testMod`.
