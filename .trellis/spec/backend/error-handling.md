# Error Handling

> Separate expected gameplay rejection, invalid encoded data, broken invariants, and external integration failures.

## Failure Categories

| Category | Representation | Current anchors |
| --- | --- | --- |
| Expected gameplay rejection | Typed result record plus bounded `Failure`/`State` enum, or `Optional` for absence | `AbilityService.UseResult`, `CultivationActionService.Result`, `ForgingService.StrikeResult`, `ResourceTransactions.Result` |
| Invalid definition or saved value | Codec `DataResult` failure or `IllegalArgumentException` at construction/mutation | definition `DIRECT_CODEC`s, `ResourceHolderAttachment`, `ResourceTransactions.evaluate` |
| Impossible post-validation state/lifecycle misuse | `IllegalStateException` | `ForgingService` post-precheck assertion, `ServerCache` chain validation, registry access without a server |
| Optional/external callback failure | Catch at the adapter boundary, log context, and fail closed/neutral | `MxtJsActionCallbacks`, `MxtJsConditionCallbacks`, `MxtJsValueCallbacks` |
| Recoverable resource/runtime integration failure | Contextual warning/error and an empty/false result | `RuntimeDimensionService`, client resource reload adapters |

Do not collapse these categories into `boolean` everywhere, throw exceptions for normal cooldown/insufficient-resource outcomes, or catch invariant failures and report success.

## Typed Gameplay Results

Use a domain result when callers need to distinguish outcomes or carry committed values:

- include a clear success/commit/state field;
- include a domain `Failure` enum for expected rejection reasons;
- include only useful context such as `failedResource`, paid amounts, or next tick;
- construct results through named factories (`committed`, `rejected`, `started`) where that prevents inconsistent combinations;
- use immutable/defensive result collections when a result crosses an event/integration boundary.

The result owner defines the taxonomy. Network, command, KubeJS, and UI adapters consume it; they do not invent competing failure codes.

Use `Optional.empty()` for ordinary absence, such as a missing/disabled registry entry, when no reason taxonomy is required. Use an exception only if absence violates the caller's declared invariant.

## Validation Failures

Reject invalid content as close to its boundary as possible:

- required Codec fields remain required;
- constructors/record compact constructors validate finite numbers, ranges, non-blank IDs/sources, and coherent timestamps;
- runtime formula evaluation rechecks finiteness because expressions are dynamic;
- transaction preparation rejects duplicate or non-positive costs before touching state;
- server handlers treat stale/missing IDs as rejected intent, not a crash.

Do not add permissive defaults merely to keep corrupt or obsolete data loading. Collection codecs that intentionally ignore invalid entries are an explicit field contract and log the ignored element; they are not a reason to make every schema tolerant.

## Atomicity and Rollback

Follow precheck -> commit -> effects:

1. Resolve all current definitions and owner state.
2. Evaluate conditions and formulas once.
3. Validate every item/resource/state transition, using detached drafts when necessary.
4. Commit live attachments/components/session state.
5. Emit post-events and execute non-rollbackable world actions.

`ResourceTransactions.tryConsume` scans every cost before applying any debit. `AbilityService` previews composite steps on attachment/item drafts. If state changes after a successful precheck in a path that is expected to be serialized on the main thread, throw/log an invariant failure; do not silently return an ordinary rejection after partial commit.

If an operation cannot be atomic because Minecraft world effects cannot be rolled back, document the ordering and keep all rejectable validation ahead of the first irreversible effect.

## Exception Boundaries

Catch exceptions only where the caller can make a correct decision:

- KubeJS action callbacks: log and stop that external action.
- KubeJS conditions: log and return `false`.
- KubeJS numeric/value providers: log and return the documented neutral `0.0` for missing, throwing, null, or non-finite callbacks.
- Optional runtime/resource loading: return empty/false only when the caller can safely continue without that optional object.

Core services should not wrap an entire operation in `catch (Exception)` and continue. Narrow catches may translate a known validation exception to a typed failure, as `ForgingService` maps invalid session construction/cost formulas. Do not catch `Error` or unrelated runtime failures.

When extension-provided actions execute after a committed transaction, contain and log them as extension failures. Never roll back only part of a committed attachment transaction based on an external callback exception.

## Networking and User Feedback

There is no HTTP error envelope. Payload handlers either delegate to a typed service result, notify through the owning gameplay/UI mechanism, or safely ignore stale intent. Do not serialize Java exception text or stack traces to clients.

Commands should translate typed failures into stable translatable components, as `MxtTestCommands` does for cultivation and sect actions. User-facing messages must not become the machine-readable source of failure state.

## Logging Relationship

Expected rejection is normally data, not an error log. Log when:

- an invariant fails after prevalidation;
- an external callback throws or returns invalid data;
- optional resource/world lifecycle work fails and requires diagnosis;
- invalid tolerant collection members are discarded.

Include the relevant type/ID/dimension/failure and pass unexpected throwables to SLF4J. See [Logging](./logging-guidelines.md) for levels and noise rules.

## Review Checklist

- Expected user/content outcome represented without an exception.
- Invalid encoded state rejected at Codec/construction boundary.
- No partial mutation before all rejectable checks pass.
- Catch scope is limited to a known integration/translation boundary.
- Fail-closed or neutral fallback is explicit and safe.
- No exception details cross to the client.
- Unexpected invariant has enough context for diagnosis.
