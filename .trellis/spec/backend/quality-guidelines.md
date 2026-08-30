# Quality Guidelines

> A change is complete only when its current-source contract, cross-layer behavior, fixtures, documentation, and observable validation agree.

## Evidence-First Workflow

Before editing:

1. Inspect the dirty worktree and preserve unrelated changes.
2. If `.codegraph/` exists, use CodeGraph first to locate symbols and trace callers/callees. Use targeted UTF-8 reads/searches only when a scoped CodeGraph result is unavailable or too broad.
3. Trace the full ownership path: Codec/definition -> registry/Holder -> runtime consumer -> mutable state -> sync -> client/integration.
4. Read the matching `docs/guide/**` topic and any relevant research, applying the authority order from [the backend index](./index.md).
5. State whether the change is common/server, client, or network-bilateral.

Do not turn one representative implementation into a global rule without checking other current consumers. Prefer stable paths and symbol names over line-number citations.

## Required Design Properties

- Keep decoded definitions immutable and mutable gameplay state in its owning attachment/component/world object.
- Keep payload handlers thin and server decisions in runtime services or stateful menu owners; keep any adapter-local operation narrow and documented.
- Use native datapack registries for reloadable content and fixed `MapCodec` registries for Java behavior types.
- Preserve Holder identity and native tags across cross-registry references.
- Evaluate and validate multi-part transactions before live mutation.
- Use typed results for expected rejection and exceptions for invalid data/invariants.
- Extend existing action, condition, `Cost`, formula, matcher, color, and collection abstractions instead of adding local formats.
- Keep optional integrations behind their public adapters and fail safely at the integration boundary.

## Prohibited Patterns

- Database/ORM/SQL/migration abstractions for Minecraft Attachments or datapack registries.
- Custom reload snapshots, `schema_version`, unknown-field policies, or stored definition versions without a separately approved product requirement.
- Trusting client-calculated costs, rewards, balances, cooldowns, eligibility, or world results.
- Direct attachment-map mutation that bypasses dirty/sync or transaction rules.
- Runtime mutation of Codec-decoded definition collections.
- Duplicating a runtime condition in a network handler, screen, command, and script adapter.
- Broad catch-and-continue around a core authoritative operation.
- Treating a documentation inventory/count as current without checking source.
- Presenting a `research/**` proposal or outdated audit status as implemented behavior.
- Treating `docs/SKILL.md` or `docs/ai/SKILL.md` as executable agent skills. Project executable skills are independently registered packages, currently visible under `.agents/skills/`.

## Test Model

The Java plugin supplies the conventional `test` source set, but the repository has no `src/test` tree, JUnit dependency, or JUnit tests. `build.gradle` defines the independent `testMod` source set used for gameplay verification:

- `src/test-mod/java/com/iafenvoy/mxt/testmod/MxtTestMod.java` runs startup audits against loaded registries, Codecs, runtime services, tags, and client-visible definitions.
- `MxtTestCommands` exposes operator-only playable flows and translates typed service failures.
- `src/test-mod/resources/data/mxt_test/**` supplies real definitions and recipes.
- `src/test-mod/resources/data/mxt_test/tags/**` and `data/mxt/tags/**` exercise native tags, including disabled entries and scenario selection.

Add coverage at the same boundary as the change. A Codec field change needs valid/invalid fixture coverage and a runtime consumer assertion. A transaction change needs a success case, each important rejection, and proof that rejected multi-cost operations leave all state unchanged. A network/client change needs observable client/server behavior, not only Codec construction.

Tests must fail if the behavior under test is removed. Avoid assertions that only restate constants created inside the test.

## Validation Matrix

| Change | Minimum checks |
| --- | --- |
| Common Java, registry wiring, attachment/component, runtime service | `compileJava` and `compileTestModJava` |
| Datapack Codec, Holder/tag, sync, or client-visible definition | compilation plus `runTestClient`; inspect `run-test-client/logs/latest.log` |
| Dedicated-server lifecycle, world state, or authoritative networking | compilation plus `runTestServer`; inspect `run-test-server/logs/latest.log` |
| Rendering or screen behavior | compilation plus a launched client and screenshot-level visual verification |
| Documentation/spec-only | link, encoding, consistency, and scoped diff checks; compile when the specification asserts current compile-time facts |

Baseline command:

```powershell
./gradlew.bat compileJava compileTestModJava --offline --no-daemon --console=plain
```

Use the run task that observes the changed path. A successful compile does not prove datapack load, registry synchronization, runtime atomicity, or rendering.

## Documentation Contract

`docs/ai/SKILL.md` defines the project coding quick guide and `docs/ai/FORMAT.md` defines public documentation structure/style. When product code changes fields, registries, public Java/KubeJS APIs, side boundaries, or completion status, update the applicable:

- Definition/Codec guide page;
- Java/KubeJS API page;
- test-mod JSON/example;
- README module/link/status text;
- historical/current audit state where the documented boundary changes.

Public docs are Simplified Chinese with exact API identifiers preserved. These Trellis backend guides remain English. Never document an uncertain field as implemented; verify the current Codec.

The project is unpublished and current docs do not promise old datapack/save/network compatibility. Deletion or renaming of fields still requires current fixtures/docs to converge, but compatibility prose is added only by an explicit product decision.

## Audit and Research Handling

Classify every non-source claim:

- **confirmed current**: verified in source/build/test output;
- **document drift**: docs disagree with verified source, such as 31 versus 30 native registry calls;
- **historical**: useful past audit evidence that is no longer authoritative;
- **proposal/test hypothesis**: a `research/**` idea not implemented in current code;
- **deferred product defect**: reproducible issue outside the approved change scope.

Do not silently merge these categories. Record a separate follow-up for a real out-of-scope defect; do not repair it during a specification or unrelated module task.

## Review Checklist

- Actual diff contains only authorized paths and no generated/transient artifacts.
- Package/layer owns the changed contract and dependency direction remains valid.
- Registry type, Codec, Holder/tag, and immutable-definition rules are correct.
- Persistence owner, dirty/sync, death-copy, and lifecycle semantics are explicit.
- Server revalidates all client or script intent; client remains display/input only.
- Typed failures and exception/logging boundaries match the failure category.
- Multi-step rejection leaves live state unchanged.
- Test-mod fixtures and observable run cover the affected boundary.
- Docs and status claims match current source; research stays labelled as non-current.
- Changed text is UTF-8 without BOM, relative links resolve, and no generic template text remains.
