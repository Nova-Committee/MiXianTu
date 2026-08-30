# Registries and Codecs

> Keep reloadable content separate from Java-owned behavior types, and preserve registry identity through Holders.

## Two Registry Layers

MiXianTu uses two distinct mechanisms:

| Layer | Purpose | Registration path | Extension boundary |
| --- | --- | --- | --- |
| Native datapack registry | Reloadable named content such as abilities, resources, formations, and item bindings | `MxtResourceKeys` plus `MxtDatapackRegistries.newDatapackRegistries` | Datapacks add entries and native tags; NeoForge handles load/reload and client sync. |
| Fixed Java registry | `MapCodec` implementations and other built-in algorithms selected by `type` | `MxtRegistries`, focused `Mxt...` DeferredRegisters, then `MiXianTu` bus registration | Java registers implementations; datapacks select them but do not inject new Java code. |

Do not replace native datapack registries with a custom reload snapshot. Do not make reloadable content a fixed `MapCodec` type just because it has a `type` field internally.

## Current Native Registry Baseline

`MxtDatapackRegistries.newDatapackRegistries` is the inventory authority. It currently calls `register(event, key, codec)` 30 times. `README-zh.md` and `docs/模块实现审计.md` still say 31; treat that as documentation drift. `resource.bars` is an inline field of `Resource`, not a separate native registry.

Avoid using the numeric count as application logic. When adding or removing a native registry, verify the call count, test fixture coverage, docs index, and audit/status text together.

## Definition Codec Naming

For definitions that are referenced by other registry-aware data, follow the established pair:

```java
public static final Codec<Holder<Ability>> CODEC =
        RegistryFixedCodec.create(MxtResourceKeys.ABILITY);
public static final Codec<Ability> DIRECT_CODEC = /* record codec */;
```

- `CODEC` decodes a Holder reference to an already registered entry.
- `DIRECT_CODEC` decodes the entry body registered by `MxtDatapackRegistries`.
- Some leaf definitions, such as `SpiritHerb` and `CreatureProfile`, currently expose only a direct `CODEC`; the exact codec passed by `newDatapackRegistries` is authoritative.
- Do not introduce the paired names mechanically when no Holder codec is needed. Do not pass a Holder codec as the entry body codec.

The current Codec defines valid fields and defaults. Verify docs and examples against it rather than inferring fields from old JSON.

## Fixed `MapCodec` Dispatch

Polymorphic behavior uses a named fixed registry and a `type` discriminator. `EntityAction.SINGLE_CODEC`, `EntityCondition.SINGLE_CODEC`, and `Cost.TYPED_CODEC` use `MxtRegistries...byNameCodec().dispatch(...)`; focused holders such as `MxtEntityActions`, `MxtEntityConditions`, and `MxtCosts` register implementations.

When adding a fixed implementation:

1. Put the interface/record in the owning `data` domain and expose a `MapCodec`.
2. Register it in the matching `Mxt...` DeferredRegister.
3. Return that exact codec from the implementation's `codec()` method.
4. If adding an entirely new fixed registry, add its key and `MxtRegistries` instance, register the registry event, and wire its DeferredRegister in `MiXianTu`.
5. Add test-mod JSON that exercises the dispatched `type` through a real runtime consumer.

Use `action` for executable behavior, `condition` for predicates, and `Cost` for expenditure. Do not create parallel terms or one-off dispatch schemes.

## Holder and Identifier Rules

- Use `Holder<T>` for cross-registry references that need stable identity or tag membership.
- Use `MxtDatapackRegistries.holder(...)` when the caller must retain identity; use `get(...)` only when the value is sufficient.
- Pass the relevant `RegistryAccess`/`HolderLookup.Provider` on side-aware paths instead of relying on the active server singleton from client code.
- Use `HolderHelper.id(...)` or the Holder key for diagnostics; do not recursively print whole cyclic definitions.
- A missing or `mxt:disabled` entry is absent from enabled lookup. Callers must handle `Optional.empty()` as an expected content/eligibility outcome.
- Do not resolve a Holder to an object early and then use object identity as a persistent key.

## Collections, Defaults, and Validation

- Treat all decoded definition objects and collections as immutable after load.
- Use `CollectionCodecs.list/map` where the field intentionally tolerates invalid individual entries. `AutoIgnoreListCodec` produces `List.copyOf`; `AutoIgnoreMapCodec` produces an immutable map and logs ignored invalid entries.
- `CollectionCodecs.set` uses an ordinary `listOf` followed by `Set.copyOf`; it makes the decoded set immutable but does not ignore an invalid member. Use it when any invalid element should reject the collection.
- Use ordinary `listOf`, map codecs, or required fields when any invalid member must reject the whole definition. Tolerance is a field-level product decision, not a universal fallback.
- Use `optionalFieldOf` only with a semantically valid default. Required identity or transaction fields remain required.
- Reject non-finite numeric values and invalid ranges at decode/construction or at the runtime formula boundary.
- Use shared codecs such as `MiscCodecs.COLOR`/`COLOR_NO_ALPHA`, `RegistryCodecs`, `ItemMatcher`, and `NumberProvider`; do not create local parsers for an existing format.

## Native Tags

Native tags are the only general tag mechanism. MXT registry tags live at:

```text
data/<namespace>/tags/mxt/<registry>/<tag>.json
```

Every native datapack registry supports the `mxt:disabled` tag through `MxtDatapackRegistries`. Use `isTagged`, Holder tag checks, or `RegistryCodecs.holderOrTag...` for tag semantics. Do not duplicate tags in arbitrary definition fields. Tag value order is not a general gameplay ordering contract; use a domain-specific reader when order is meaningful, as item-quality ordering does.

## KubeJS Boundary

KubeJS extends approved dispatch points through the built-in `mxt:js` action, condition, and value-provider types and through `MxtKubeJsApi` service methods. It does not create another native registry system.

- Scripts register unique callback IDs; datapacks select the existing `mxt:js` codec and ID.
- Script-facing operations call validated server services and return safe typed results.
- Do not expose attachment maps or direct mutation handles as the scripting API.
- Unknown callbacks and callback exceptions fail closed or return the documented neutral value, with contextual logging.
- `docs/SKILL.md` and `docs/ai/SKILL.md` describe project use; they are not executable KubeJS or agent registrations.

## Change Checklist

- Native or fixed registry chosen for the correct reason.
- Correct entry-body Codec registered; Holder references used at cross-table fields.
- New collection tolerance and defaults are intentional and tested.
- `mxt:disabled` and relevant tag behavior are covered.
- Fixed registration is wired to the event bus when required.
- Test-mod definition, runtime assertion, and corresponding docs are updated.
- Current source count is rechecked instead of copying the stale 31 claim.
