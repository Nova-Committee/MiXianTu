# Persistence and Data State Guidelines

> The filename is retained for Trellis compatibility. MiXianTu has no database, ORM, SQL layer, or migration framework.

## State Model

Choose storage by ownership and lifetime:

| State kind | Mechanism | Current anchors |
| --- | --- | --- |
| Reloadable content definitions | Native NeoForge datapack registries, referenced through `Holder` | `MxtDatapackRegistries`, `Ability`, `Resource` |
| Entity/player state | NeoForge Attachments with a `MapCodec`; selected types are synchronized and copied on death | `MxtAttachments`, `ResourceHolderAttachment`, `CultivationAttachment` |
| Chunk or level state | Owner-specific serialized Attachments | `AuraChunkAttachment`, `AuraWorldAttachment`, `FormationWorldAttachment` |
| ItemStack state | Persistent `DataComponentType` values; network sync only when consumers need it | `MxtDataComponents`, `SpiritStorageComponent`, `ForgingResultComponent` |
| Server-lifetime derived state | Rebuilt runtime caches or live world maps | `ServerCache`, `RuntimeDimensionService` |
| Transaction drafts/sessions | Short-lived runtime values, sometimes backed by a dedicated attachment when gameplay spans ticks | `ResourceTransactions.Evaluation`, `ForgingSessionAttachment` |

Do not introduce tables, repositories, DAO objects, migrations, or database transaction terminology for these mechanisms.

## Definitions Are Not Saved Mutable State

Native datapack entries are decoded on load/reload and synchronized by NeoForge. Treat the resulting objects and their collections as immutable. Store registry identity with a `Holder` or resource key/identifier according to the current Codec; do not copy a definition into an attachment and mutate it.

`ServerCache` demonstrates the correct derived-state pattern: rebuild validated indexes after server start/datapack load, replace the complete maps only after validation, and clear the singleton at server stop. A cache is never a second source of truth for the definitions.

## Attachment Rules

- Register attachment persistence in `MxtAttachments` with the owner-appropriate Codec.
- Use the `entity(...)` helper only when state should serialize, sync, and copy on player death. Use `entityWithoutDeathCopy(...)` for in-progress state that must not duplicate on respawn, such as forging sessions or flight state.
- Mutable synchronized attachments extend `ShouldSyncAttachment`. Every effective mutator must call `markDirty()`; no-op mutations should not create sync traffic.
- Route changes through attachment/service methods. Do not mutate a collection returned by an accessor unless that owner explicitly provides and documents the required dirty handling.
- `MxtAttachments.flushDirtyAttachments` consumes the dirty bit on the server tick and calls `entity.syncData`. Do not send a parallel payload for the same ordinary attachment state.
- Validate constructor and mutation invariants. `ResourceHolderAttachment` rejects non-finite values and invalid audit bounds before storing them.
- Decide copy-on-death, sync, and persistence independently; they are gameplay semantics, not defaults to apply blindly.

When a multi-step operation needs rollback-free validation, use a detached draft such as `ResourceHolderAttachment.copy()`, validate the whole operation, then commit through the live attachment's methods.

## Item Data Components

`MxtDataComponents` owns persistent ItemStack results and state. Follow these rules:

- A component contains data owned by that stack, not active player attachment state.
- Make the component value immutable and replace it with `ItemStack.set(...)` after a change.
- Use `.persistent(CODEC)` for saved state. Add `.networkSynchronized(...)` only when a client consumer needs the value.
- Use registry-aware stream codecs when component fields include Holders.
- Keep calculated definitions in datapack registries; store only the result or selected reference needed by the item.

## World and Runtime State

World-owned state must remain scoped to its `ServerLevel`, chunk, or server lifecycle. Do not put per-world state in an unscoped static map. If a runtime service must alter Minecraft's live world map, synchronize around the owned map and mark the server's worlds dirty, as `RuntimeDimensionService` does.

Derived runtime caches must document when they are built, refreshed, and cleared. Rebuild after server datapack load/reload, not for client tag synchronization events or every player access.

## Mutation and Transaction Boundary

Runtime services own state changes. A safe sequence is:

1. Resolve enabled Holders and current owner state.
2. Evaluate formulas/conditions once in the correct context.
3. Validate all resources, items, ownership, and invariants without partial live mutation.
4. Commit the complete state change.
5. Emit post-events and let attachment/component synchronization publish the result.

`ResourceTransactions.evaluate` rejects invalid or duplicate costs and `tryConsume` checks every balance before any debit. `AbilityService` and `ForgingService` compose this transaction boundary rather than decrementing resources ad hoc.

## Codec Changes and Compatibility

The project is currently unpublished and does not promise old save, attachment, datapack, or network compatibility. There is no current `schema_version` or data-migration contract.

When changing saved fields:

- Treat the current attachment/component Codec as authoritative.
- Update the corresponding test-mod fixtures/startup assertions and public documentation.
- Add defaults only when they express valid current behavior, not to conceal corrupt state.
- Do not add schema versions, migration registries, unknown-field policies, or stored definition versions solely because they appear in `research/**`.
- If compatibility becomes a product requirement, design and approve it as a separate cross-cutting task.

## Review Checks

- Is the chosen owner entity, item, chunk, level, server, or datapack content?
- Does every effective mutable attachment path mark dirty exactly once?
- Are copy-on-death and network sync intentional?
- Are Holder-bearing codecs registry-aware?
- Is a transaction prechecked before live mutation?
- Can reload/lifecycle code rebuild or clear derived state without retaining stale definitions?
