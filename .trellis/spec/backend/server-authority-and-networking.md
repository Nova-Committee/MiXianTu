# Server Authority and Networking

> Clients express intent and render synchronized results; the server resolves, validates, mutates, and commits gameplay state.

## Authoritative Flow

The standard request path is:

```text
client input
  -> C2S payload (IDs, selection, operation)
  -> MainThreadPayloadHandler
  -> ServerNetworkHandler revalidation
  -> runtime service / transaction / stateful menu owner
  -> attachment, ItemStack, menu, or world mutation
  -> attachment sync, native registry sync, or focused S2C snapshot
  -> client-only state and rendering
```

`NetworkManager` registers play-to-server payloads against `ServerNetworkHandler` and the aura snapshot against `ClientNetworkHandler`. Runtime services such as `AbilityService`, `CultivationModeService`, `ForgingWorkstationService`, and `PlayerTradeService` remain the decision owners.

## C2S Payload Contract

C2S records carry the minimum user intent needed to reproduce the action on the server:

- identifiers such as an ability, archetype, resource, or forging definition;
- bounded operation enums/booleans such as start, strike, finish, cancel, or firing;
- a position or selection when the server must locate a current world/container object.

`AbilityActionC2SPayload` sends an ability ID and use/cancel intent. It does not send cost, cooldown, damage, permission, or a claimed result.

Never accept client-authored balances, formula results, rewards, item ownership, eligibility, cooldown completion, or transaction totals. A new payload must define a `TYPE`, a deterministic `STREAM_CODEC`, and the correct direction in `NetworkManager`. Protocol registration changes must be reviewed against the current registrar version and both sides together.

## Server Handler Responsibilities

Handlers are thin trust-boundary adapters. They must:

1. Confirm the required server-side actor type, menu, container, position, or ownership context.
2. Resolve current enabled definitions through `MxtDatapackRegistries` and Holders.
3. Read current server attachments, inventory, world, and game time.
4. Build the server formula/context values.
5. Delegate the decision and mutation to the owning runtime service or stateful menu owner.
6. Translate an expected failure only when user feedback or protocol state requires it.

Examples in `ServerNetworkHandler` include checking the current menu before cheque/station operations, resolving ability/forging definitions on receipt, and delegating cultivation and spirit-burst state changes. Do not copy service conditions into a payload handler or screen.

`onBackSlotSwap` is a current narrow exception that validates and performs one Curios/inventory exchange in the handler. Treat it as an adapter-local operation, not as a pattern for new domain logic; extract an owning service if the rule grows or gains another caller.

Missing/disabled IDs or stale UI selections are expected request rejections. They may be ignored or mapped to the owning service's failure behavior; they must not crash the network thread or mutate partial state.

## Runtime Validation and Atomic Mutation

The runtime service revalidates all gameplay rules, even if the client UI already disabled the action:

- definition is loaded and enabled;
- actor owns or may use it;
- logical side and lifecycle are valid;
- conditions, permissions, target selection, cooldowns, and formula values pass;
- inventory/resources exist in sufficient amounts;
- menu, block, session, and world state still match the request.

For multi-owner changes, prepare or draft first and commit after all checks. `ResourceTransactions` checks every cost before any debit. `AbilityService` prepares typed costs/state and uses detached drafts for composite prevalidation. `ForgingService` validates the session and payment before advancing it.

Minecraft world actions are generally not rollbackable. Perform reversible validation and economic/state commitment in the established order, then execute world effects. If a post-precheck invariant fails, report it as an invariant failure rather than treating it as ordinary user rejection.

## S2C and Synchronization

Prefer an existing synchronization owner:

- Native datapack registries are synchronized by NeoForge; client code reads them with client registry access.
- Synced entity attachments are serialized by the Codec/stream codec registered in `MxtAttachments` and flushed after `markDirty()`.
- Item data components are network synchronized only where `MxtDataComponents` declares it.
- A focused S2C payload is appropriate for derived local display state that is not already represented by those mechanisms. `AuraStateS2CPayload` carries fully resolved actual/environment aura maps for `AuraClientState`.

Send the smallest stable snapshot the client needs. Preserve independent map/type semantics: resource and aura values of different IDs are not interchangeable and must not be summed into one authoritative scalar.

Do not send a duplicate custom payload merely to make a correctly synchronized attachment update faster. Do not let the client recompute authoritative formula bounds from definitions when the server already owns the resolved snapshot.

## Client Boundary

Client packages may:

- collect input and send intent;
- render synchronized registries, attachments, data components, and client snapshots;
- maintain presentation-only selection, animation, and layout state;
- validate purely local rendering requirements.

They may not consume costs, grant abilities, advance cultivation, settle trades, choose authoritative targets, change world/entity state, or fabricate success from local prediction. Client display checks improve UX but never replace server validation.

Keep client-only classes out of common/server load paths. Register rendering and key mappings on client events, while keeping shared payload schemas free of graphical types.

## Java and KubeJS Extensions

Java commands, events, items, menus, and KubeJS bindings follow the same server-authoritative service boundary as payloads. `MxtKubeJsApi` explicitly rejects client-side calls for authoritative actions and delegates to `AbilityService`, `CultivationService`, `CurseService`, and `ResourceTransactions`.

An integration must expose intent/query methods or typed service results, not direct attachment internals. External callback exceptions are contained at the integration boundary and must not convert into a successful transaction.

## Review Checklist

- Payload contains intent, not a client-calculated outcome.
- Handler is on the correct direction and main-thread path.
- Current actor/menu/world/Holder/state is re-resolved on receipt.
- Runtime service or stateful menu owner owns validation and mutation; any adapter-local exception stays narrow and documented.
- All preconditions are checked before irreversible or multi-owner changes.
- Result is synchronized by exactly one appropriate mechanism.
- Client code only consumes synchronized state for display/input.
- Invalid or stale requests fail without crash or partial mutation.
