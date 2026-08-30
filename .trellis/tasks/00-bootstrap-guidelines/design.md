# Design: MiXianTu Backend Trellis Specifications

## Context

The generated backend spec directory contains generic web/backend templates that do not describe a NeoForge mod. MiXianTu instead uses native datapack registries, fixed Java `MapCodec` registries, Attachments, ItemStack data components, runtime services, explicit networking, and a test mod. The spec set must follow those real boundaries.

## Objectives

- Give future implementation and check agents stable, source-backed rules for MiXianTu backend work.
- Preserve the distinction between current implementation, normative documentation, historical audit claims, and research proposals.
- Cover the local architecture that the generated templates omit without duplicating whole source documents.

## Non-Objectives

- Product-code fixes or gameplay changes.
- Rewriting `docs/`, `research/`, README, build logic, generated resources, or dependencies.
- Creating a database abstraction, migration layer, custom reload registry, or compatibility policy that the project does not have.
- Converting documentation named `SKILL.md` into a new executable agent skill.

## Spec Information Architecture

The implementation will keep existing filenames to avoid unnecessary delete/move operations and add two project-specific topics.

| File | Responsibility |
| --- | --- |
| `.trellis/spec/backend/index.md` | Navigation, evidence policy, and when each guide applies. |
| `.trellis/spec/backend/directory-structure.md` | Source sets, package ownership, dependency direction, naming, and representative modules. |
| `.trellis/spec/backend/database-guidelines.md` | Retitled in content to persistence and data state; explicitly documents the absence of a database/ORM. |
| `.trellis/spec/backend/registries-and-codecs.md` | Native versus fixed registries, Codec naming, Holder references, immutability, tags, and KubeJS extension boundaries. |
| `.trellis/spec/backend/server-authority-and-networking.md` | C2S intent, server revalidation, runtime services, atomic mutation, synchronization, and client-only display. |
| `.trellis/spec/backend/error-handling.md` | Typed rejection results, invariant exceptions, safe integration failures, and rollback/atomicity. |
| `.trellis/spec/backend/logging-guidelines.md` | SLF4J usage, log levels, contextual IDs, rate/noise discipline, and sensitive-state exclusions. |
| `.trellis/spec/backend/quality-guidelines.md` | Required evidence workflow, testing, documentation/audit sync, prohibited patterns, and review checklist. |

## Evidence-to-Spec Flow

1. Read the source guidance in `docs/` and the relevant proposal/history in `research/`.
2. Verify drift-sensitive statements against current source, build configuration, test fixtures, and CodeGraph call paths.
3. Write compact rules with stable file paths and symbol names rather than copied implementations.
4. Label historical or proposed behavior; do not turn it into a mandatory current rule.
5. Cross-check all guides for consistent terminology and update `index.md` to match the actual file set.

## Content Contract

- Specs are written in English, matching the existing backend spec index contract; source titles and identifiers remain exact.
- Each important rule names at least one current source, test, build, or normative documentation anchor.
- Short snippets are allowed only when they clarify a local shape; copied large code blocks are prohibited.
- Rules explain applicability, the local pattern, evidence, anti-patterns, and relevant verification.
- Research-only requirements such as stored schema versions or custom reload snapshots are explicitly excluded unless current source proves them.

## Compatibility and Migration

- No product data, save, protocol, or API migration occurs.
- Existing spec filenames are preserved. The generic `database-guidelines.md` filename remains for Trellis compatibility, but its title and content become project-accurate persistence guidance.
- Existing user changes outside `.trellis/spec/backend/` and this task directory are untouched.

## Risks and Controls

| Risk | Control |
| --- | --- |
| Stale docs become false rules | Apply the authority order and verify against current Codecs/runtime consumers. |
| Specs become generic or aspirational | Require real paths/symbols and reject unimplemented research assumptions. |
| Too many overlapping guides | Keep cross-cutting topics in the seven focused guides above and remove repeated prose during convergence. |
| Windows decoding creates mojibake | Read UTF-8 explicitly and verify output bytes/no BOM. |
| Line numbers drift | Prefer stable symbols and paths; use line anchors only for audit evidence where necessary. |
| Unrelated dirty-worktree changes are overwritten | Restrict writes to the declared spec/task paths and inspect the final diff. |

## Rollback

All implementation changes are documentation-only and confined to `.trellis/spec/backend/`. Before any rollback, inspect the pre-existing dirty worktree and revert only task-owned hunks; do not use broad Git reset or checkout operations.
