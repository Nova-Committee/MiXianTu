# Logging Guidelines

> Log actionable lifecycle, content, integration, and invariant information without turning normal gameplay into noise.

## Logger and Format

Use SLF4J parameterized messages:

```java
MiXianTu.LOGGER.error("Failed to load runtime dimension {}", key.identifier(), exception);
```

Main mod subsystems normally use `MiXianTu.LOGGER`, created by `LogUtils.getLogger()`. Focused low-level utilities such as tolerant codecs and the independent `mxt_test` mod currently own local `LogUtils` loggers; do not create a new logger per domain class without a reason.

Use `{}` arguments rather than string concatenation or eager formatting. Pass an unexpected throwable as the final argument when a stack trace is needed. A recoverable per-entry decode warning may report a concise error message without a stack trace when the bad entry and outcome are already clear.

## Levels

| Level | Use | Examples/anchors |
| --- | --- | --- |
| `debug` | Opt-in, bounded diagnostics useful during development; never required for normal operation. | Detailed resolution or state transitions when adding diagnostics. |
| `info` | One-time lifecycle or integration readiness. | `MxtKubeJsPlugin` bridge initialization; `MxtTestMod` load. |
| `warn` | Recoverable degradation, ignored invalid content, unknown optional callback/type, or cleanup failure. | `AutoIgnoreListCodec`, `MxtJsValueCallbacks`, `RuntimeDimensionService.unload`. |
| `error` | Unexpected callback/action failure, failed authoritative lifecycle operation, or impossible state after prevalidation. | `AbilityService`, `RuntimeDimensionService.load`, KubeJS callback adapters. |

Do not log expected cooldowns, failed conditions, insufficient resources, missing optional definitions, or ordinary invalid client intent at warn/error. Those are typed outcomes unless repeated evidence indicates a system fault.

## Required Context

Choose identifiers that let a developer reproduce the failing boundary:

- namespaced definition/callback/resource ID;
- extension type (`entity action`, `number provider`, and similar);
- dimension or registry key for world/registry lifecycle failures;
- bounded failure/state when an invariant breaks after prevalidation;
- operation name and relevant stable position/session ID when applicable.

Prefer stable IDs over dumping definitions, entities, ItemStacks, registries, or attachments. Definition graphs can be cyclic through Holders; `Ability.toString()` is intentionally shallow for this reason.

For an unexpected throwable, log a concise event plus the exception:

```java
MiXianTu.LOGGER.error("KubeJS {} '{}' failed", type, id, exception);
```

For a known invalid neutral result, state the fallback:

```java
MiXianTu.LOGGER.warn("KubeJS {} '{}' returned a non-finite value; using 0", type, id);
```

## Noise and Ownership

- Never emit info/warn/error once per entity tick, render frame, resource regeneration step, or repeated condition check.
- Dirty attachment flushing, normal payload receipt, successful costs, and registry lookups do not need logs.
- Log an error once at the boundary that owns recovery. Do not re-log the same exception in the handler, service, and caller.
- If repeated invalid external input can recur at high frequency, deduplicate/rate-limit at its owner or fix the registration/content source; do not flood the server log.
- Use typed results and metrics/state inspection for normal outcomes instead of logging control flow.
- Test-mod startup assertions should throw with a precise invariant message; they do not need a second error log before throwing.

## Sensitive and Excessive Data

Do not log:

- full attachment/component maps, inventories, NBT, or serialized player state;
- complete network payload dumps or arbitrary KubeJS parameter objects;
- access tokens, credentials, filesystem secrets, server addresses, or private configuration;
- large/cyclic definition graphs or entire registry contents;
- player-identifying data unless it is essential for an operator-visible diagnostic and the scope is minimized.

Use IDs, counts, bounded values, and operation names. Avoid logging formula variable maps because integrations may supply arbitrary values.

## Integration and Reload Boundaries

External script and resource failures need both the extension type and ID/resource path. Current KubeJS adapters fail closed/neutral and log unknown callbacks or exceptions. Tolerant collection codecs log each ignored invalid member; callers must not silently discard the entire diagnostic.

Client resource reloaders may continue after one malformed optional file, but the warning should name the folder/resource and the parse problem. Server-authoritative definition registries should rely on Codec load failure when the whole definition is invalid rather than catch-and-hide it.

## Review Checklist

- SLF4J `{}` arguments used; no eager concatenation.
- Level matches whether the event is normal, degraded, or unexpected.
- Stable type/ID/context identifies the boundary.
- Throwable is included for unexpected failures.
- Expected typed rejection is not logged as an error.
- No tick/frame spam or duplicate layer logging.
- No full state dumps, secrets, arbitrary script parameters, or cyclic definitions.
