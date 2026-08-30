# Bootstrap MiXianTu Backend Development Guidelines

## Goal

Establish evidence-based Trellis backend specifications for MiXianTu so future implementation and review agents follow the mod's actual architecture, documented contracts, validation expectations, and supported extension boundaries.

## User Value

- Future agents receive project-specific guidance instead of generic web/backend advice.
- Mod contracts and audit conclusions remain traceable to current source and repository evidence.
- Research can inform later design without being misrepresented as implemented behavior.

## Background and Source Authority

MiXianTu is a single Java 25 / NeoForge 26.1.2 mod with a server-authoritative, data-driven runtime. Its current backend spec directory is generated placeholder content.

Evidence must be interpreted in this order:

1. Current source, build configuration, test-mod fixtures, and fresh validation results establish implemented behavior.
2. `docs/ai/SKILL.md`, `docs/ai/FORMAT.md`, and current topic documentation establish intended conventions, subject to current Codec/runtime verification.
3. `README-zh.md` provides scope and status context, but drift-sensitive claims require independent verification.
4. `docs/模块实现审计.md` is historical evidence because it explicitly marks itself outdated.
5. `research/` supplies proposals, references, risk hypotheses, and candidate tests; it is not proof of implementation.

Verified current facts include:

- `src/main/java/com/iafenvoy/mxt/` separates registry, definition, attachment, runtime, network, integration, item, and client presentation concerns.
- `MiXianTu` registers fixed Java-side types; `MxtDatapackRegistries` registers native NeoForge datapack registries and exposes Holder-based enabled lookup.
- Current source contains 30 native datapack registration calls, while README and the historical audit state 31.
- The project has no database or ORM. Persistent and reloadable state uses Attachments, ItemStack data components, native datapack registries, world/runtime state, and transaction services.
- C2S payloads carry intent and IDs; runtime services revalidate and return typed `Result`/`Failure` outcomes. Client packages consume synchronized state for display/input.
- The Java plugin supplies the conventional `test` source set, but the repository has no `src/test` tree, JUnit dependency, or JUnit tests. Gradle additionally defines the independent `testMod` source set; `src/test-mod` contains the `mxt_test` mod, datapack fixtures, commands, and startup audits.
- `docs/SKILL.md` and `docs/ai/SKILL.md` are project guidance documents. Executable project-local skills under `.agents/skills/` are currently Trellis workflow skills.

## Requirements

### R1. Evidence and provenance

- Inventory relevant `docs/`, `research/`, source, build, and test evidence before defining rules.
- Back every important convention with a current source/test/build anchor, normative documentation, or an explicitly labelled research origin.
- Resolve conflicts according to the source authority order; do not merge incompatible statements into ambiguous rules.

### R2. Backend specification bootstrap

- Replace every placeholder in `.trellis/spec/backend/` with practical MiXianTu guidance and real repository examples.
- Cover source/package ownership, persistence/data state, registries/Codecs, server authority/networking, error handling, logging, and quality/testing.
- Reshape the generic database template instead of inventing database, ORM, SQL, query, or migration conventions.
- Update the backend index so it exactly matches the final spec file set.

### R3. Mod and extension contracts

- Document the rules a MiXianTu module must follow, including native versus fixed registries, Holder/Codec usage, immutable definitions, Action/Condition/Cost terminology, and server/client boundaries.
- Document supported Java, datapack, KubeJS, and client extension boundaries without exposing internal state as public API.
- Treat documentation named `SKILL.md` as project guidance unless a separate executable skill package is explicitly created and registered.

### R4. Audit and research handling

- Record verified audit drift and derive reusable review targets from it.
- Distinguish confirmed current behavior, stale findings, unimplemented proposals, rejected assumptions, and deferred product defects.
- Do not duplicate entire source documents inside Trellis specs.

### R5. Planning and review gates

- Maintain `design.md`, `implement.md`, `implement.jsonl`, and `check.jsonl` for this cross-cutting task.
- Obtain fresh approval of the final planning summary before Phase 2 writes backend specs.
- Require independent verification of the actual spec files, source anchors, links, encoding, and validation results.

## Acceptance Criteria

- AC1: Every adopted convention is traceable to repository evidence and does not contradict verified current code without an explicit drift/migration note.
- AC2: Every file indexed by `.trellis/spec/backend/index.md` contains project-specific guidance, real paths or symbols, and no template placeholder language.
- AC3: The final spec set covers persistence/data state, registry/Codec boundaries, server authority/networking, failure handling, logging, testing, documentation maintenance, and extension guidance.
- AC4: The 30-versus-31 registry discrepancy and other research/document drift are represented truthfully without changing product behavior.
- AC5: Research proposals are visibly separated from mandatory current behavior.
- AC6: Both context manifests contain real entries and `task.py validate` exits 0.
- AC7: Changed text files are UTF-8 without BOM, contain no accidental mojibake, and pass scoped placeholder/link/consistency checks.
- AC8: No product Java/resources, `docs/`, `research/`, dependency, generated-output, deployment, or Git-history change is included.

## Constraints

- Use CodeGraph before direct code search/read when resolving source architecture or call paths.
- Preserve the existing dirty worktree and modify only task-owned planning/context artifacts during planning.
- Backend specs are written in English, while exact Chinese source titles and API identifiers remain unchanged.
- Keep existing backend spec filenames; add focused guides only where the project architecture requires them.
- All authored text is UTF-8 without BOM.

## Out of Scope

- Implementing gameplay features or repairing product-code defects found during audit.
- Rewriting README, `docs/`, or `research/` source material.
- Creating executable agent skills from the documentation Skill pages.
- Adding databases, custom reload registries, compatibility migrations, dependencies, generated assets, release workflows, deployment state, or Git commits.

## Deferred Items and Risks

- Reproducible product defects discovered while verifying evidence become separate follow-up candidates.
- The online replacement linked by the historical audit was not readable during planning; no rule may depend solely on that unavailable source.
- Documentation counts/status can drift; specs should prefer stable architectural rules and current symbols over copied inventory claims.
