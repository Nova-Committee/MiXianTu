# MiXianTu Backend Spec Evidence Map

## Purpose

This note persists the repository evidence used to plan the backend Trellis specification bootstrap. It is implementation context, not a replacement for reading current source before writing a rule.

## Authority Order

1. Current source, build configuration, test-mod fixtures, and fresh command output.
2. `docs/ai/SKILL.md`, `docs/ai/FORMAT.md`, and current topic documentation, checked against current Codec and runtime consumers.
3. `README-zh.md` for project scope and status context, with drift-sensitive claims verified independently.
4. `docs/模块实现审计.md` as historical evidence only; it marks itself outdated.
5. `research/` as design input, test hypotheses, and rejected or unimplemented alternatives.

## Verified Architecture Evidence

| Concern | Evidence | What it establishes |
| --- | --- | --- |
| Mod entry and fixed registries | `src/main/java/com/iafenvoy/mxt/MiXianTu.java`, constructor | Java-side registries and integrations are registered from the mod entrypoint. |
| Native datapack registries | `src/main/java/com/iafenvoy/mxt/registry/MxtDatapackRegistries.java`, `newDatapackRegistries` | Current source has 30 native datapack registry registration calls and Holder-aware lookup with `mxt:disabled`. |
| Package boundaries | `src/main/java/com/iafenvoy/mxt/{registry,data,attachment,runtime,network,compat,item,render,screen,util}` | Definitions, persistent state, orchestration, transport, integrations, and client presentation are separate concerns. |
| Persistent state | `src/main/java/com/iafenvoy/mxt/attachment/`, `src/main/java/com/iafenvoy/mxt/data/`, runtime world services | The project has no database/ORM; it uses Attachments, data components, native registries, and world/runtime state. |
| Server authority | `docs/guide/java/network.md`, `src/main/java/com/iafenvoy/mxt/network/payload/`, `src/main/java/com/iafenvoy/mxt/runtime/` | C2S payloads carry intent and identifiers; services revalidate and commit server-owned state. |
| Failure contracts | `AbilityService`, `CultivationActionService`, `ForgingService`, `ResourceTransactions`, and other `runtime` services | Expected gameplay rejection is represented with typed `Result`/`Failure`; invariant violations use exceptions. |
| Logging | `MiXianTu.LOGGER`, KubeJS callback adapters, `AbilityService`, `RuntimeDimensionService` | SLF4J parameterized logging is centralized; external callback failures include type/ID/context and fail safely. |
| Test model | `build.gradle`, `src/test-mod/java`, `src/test-mod/resources` | The repository uses an independent `testMod` source set, datapack fixtures, commands, and startup audits rather than JUnit. |
| Documentation contract | `docs/ai/SKILL.md`, `docs/ai/FORMAT.md`, `docs/guide/` | Codecs are authoritative for fields; docs distinguish implemented, in-progress, and reserved behavior and require server/client boundary clarity. |

## Confirmed Drift and Rejected Research Assumptions

- `README-zh.md` and the historical audit say 31 native datapack registries; current `MxtDatapackRegistries` registers 30.
- Research proposals for custom reload snapshots, `schema_version`, unknown-field tolerance, and mandatory stored definition versions are not current implementation contracts.
- The project is unpublished and current docs explicitly do not promise old datapack, attachment, save, or network compatibility.
- `resource.bars` is currently inline within `resource`; it is not an independent dynamic registry.
- `docs/SKILL.md` and `docs/ai/SKILL.md` are project guidance documents. Project-local executable skills under `.agents/skills/` are Trellis workflow skills.

## Planned Spec Mapping

| Spec | Primary evidence |
| --- | --- |
| `directory-structure.md` | Main/test source trees, package ownership, `MiXianTu` registration flow. |
| `database-guidelines.md` retitled in content to persistence/data state | Attachments, data components, datapack registries, runtime transactions; explicitly no database/ORM. |
| `registries-and-codecs.md` | `MxtDatapackRegistries`, fixed `MapCodec` registries, Holder references, `DIRECT_CODEC`/`CODEC`, immutable collections. |
| `server-authority-and-networking.md` | Payload records, runtime services, synchronized client snapshots, validation boundaries. |
| `error-handling.md` | Typed results/failures, constructor/Codec validation, atomic precheck/commit patterns, safe callback failure. |
| `logging-guidelines.md` | `MiXianTu.LOGGER`, parameterized messages, warn/error boundaries, contextual identifiers. |
| `quality-guidelines.md` | `docs/ai/SKILL.md`, `docs/ai/FORMAT.md`, `build.gradle`, `src/test-mod`, audit-drift handling. |

## Scope Boundary

This task changes `.trellis/spec/backend/` and its Trellis planning/context artifacts only. It does not repair Java defects or rewrite `docs/` and `research/` sources.
