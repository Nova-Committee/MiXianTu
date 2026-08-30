# MiXianTu Backend Development Guidelines

> Source-backed rules for the common and server-side code of the `mxt` NeoForge mod.

## Scope

MiXianTu is a Java 25, Minecraft `26.1.2`, NeoForge `26.1.2.92` mod. Its backend is not an HTTP application: it is the data-driven common/server runtime built from native datapack registries, Java-owned type registries, Attachments, ItemStack data components, payload handlers, and runtime services.

These guides apply to changes under `src/main/java/com/iafenvoy/mxt/` that affect definitions, registration, persistent state, gameplay decisions, networking, or integrations. Client presentation still has to respect the authority boundary described here.

## Evidence Authority

Resolve conflicts in this order:

1. Current source, `build.gradle`, `gradle.properties`, `src/test-mod/**`, and fresh command output.
2. `docs/ai/SKILL.md`, `docs/ai/FORMAT.md`, and current `docs/guide/**` pages, after checking their claims against current Codecs and runtime consumers.
3. `README-zh.md` for project scope and status; verify inventory and completion claims in source.
4. `docs/模块实现审计.md` as historical evidence only. It marks itself outdated.
5. `research/**` as proposals, risks, and test ideas, never as proof of implemented behavior.

For example, current `MxtDatapackRegistries.newDatapackRegistries` has 30 registration calls. The 31-registry statements in README and the historical audit are drift, not a current contract.

## Pre-Development Checklist

- Inspect `git status --short` and preserve unrelated work.
- If `.codegraph/` exists, use CodeGraph before direct source search for symbols, architecture, or call paths.
- Identify the logical side and trace the complete path: definition/Codec -> registry/Holder -> runtime service -> attachment or world state -> sync -> client consumer.
- Read the relevant guide below and the corresponding `docs/guide/**` topic.
- Treat the current Codec and runtime consumer as the field and behavior authority.
- Plan matching `testMod` code or datapack fixtures for behavioral changes.

## Guide Index

| Guide | Use it for |
| --- | --- |
| [Directory Structure](./directory-structure.md) | Source sets, package ownership, dependency direction, and naming. |
| [Persistence and Data State](./database-guidelines.md) | Attachments, data components, reloadable definitions, world state, and mutation/sync rules. |
| [Registries and Codecs](./registries-and-codecs.md) | Native versus fixed registries, Holder references, immutable definitions, tags, and extension types. |
| [Server Authority and Networking](./server-authority-and-networking.md) | C2S intent, server revalidation, atomic mutation, S2C state, and client limits. |
| [Error Handling](./error-handling.md) | Typed rejection results, invalid definitions, invariants, integration failures, and atomicity. |
| [Logging](./logging-guidelines.md) | SLF4J usage, level selection, context, exception reporting, and noise control. |
| [Quality](./quality-guidelines.md) | Evidence workflow, test-mod gates, documentation sync, audit handling, and review checks. |

## Stable Terminology

- `action` performs behavior, `condition` decides whether behavior is allowed, and `Cost` represents expenditure.
- A **definition** is decoded datapack content. Runtime services consume it; they do not mutate it.
- A **Holder** preserves registry identity and tag membership across registry-aware operations.
- An **attachment** or **data component** owns mutable/persistent gameplay state; it is not a definition registry.
- `docs/SKILL.md` and `docs/ai/SKILL.md` are project guidance pages. Executable project skills are separate packages such as those under `.agents/skills/`.

## Baseline Verification

Run the narrowest relevant checks, with this baseline for common/server changes:

```powershell
./gradlew.bat compileJava compileTestModJava --offline --no-daemon --console=plain
```

Codec, network, runtime, and rendering changes normally also require the appropriate `runTestClient` or `runTestServer` observation described in [Quality](./quality-guidelines.md).
