# Implementation Plan: MiXianTu Backend Trellis Specifications

## Preconditions

- The user has approved the specification/bootstrap-only scope.
- Product Java, resources, `docs/`, `research/`, dependencies, generated output, and Git history remain out of scope.
- Phase 2 begins only after the final planning summary receives fresh explicit approval.

## Ordered Work

### 1. Preflight and ownership check

- Capture `git status --short` and identify the existing uncommitted files.
- Re-read `prd.md`, `design.md`, and `research/evidence-map.md`.
- Confirm CodeGraph is present before code discovery and use it first for source/call-path questions.
- Restrict writable ownership to `.trellis/spec/backend/` and this task's context artifacts.

### 2. Establish the spec index and directory architecture

- Rewrite `index.md` to list the final seven guides and the evidence authority order.
- Replace the directory template with actual `main`, `testMod`, generated-resource, and package ownership guidance.
- Document dependency direction among definitions/registries, attachments, runtime services, networking/integrations, and client presentation.

### 3. Replace the database template with persistence/data-state guidance

- Keep the filename `database-guidelines.md`, but retitle and rewrite the content.
- Document Attachments, data components, native datapack registries, runtime/world state, dirty/sync behavior, and transaction services.
- State explicitly that database, ORM, SQL, and migration-template rules do not apply.

### 4. Add registry/Codec and server-authority guides

- Create `registries-and-codecs.md` with native/fixed registry boundaries, Holder usage, Codec naming, immutable collection rules, tag behavior, and KubeJS limits.
- Create `server-authority-and-networking.md` with payload intent, server revalidation, atomic mutation, synchronization, and client-only rendering rules.
- Reconcile the verified 30-registry count without copying stale 31-registry claims.

### 5. Write error and logging guidance

- Replace error-handling placeholders with typed `Result`/`Failure`, validation exception, atomicity, and external-callback containment patterns.
- Replace logging placeholders with project SLF4J conventions, contextual identifiers, level selection, and noise/sensitive-data restrictions.
- Use multiple representative runtime/integration files so a single accidental pattern does not become a project rule.

### 6. Write quality, audit, documentation, and guidance rules

- Replace quality placeholders with required source-first discovery, server/client boundary review, test-mod expectations, documentation synchronization, and review gates.
- Explain that `docs/ai/SKILL.md` and `docs/SKILL.md` are project guidance documents, while actual executable project skills are discovered separately.
- Record how to use historical audit and research without presenting proposals as implemented behavior.

### 7. Convergence and verification

- Read every backend spec top to bottom and remove duplicated or contradictory rules.
- Verify the index exactly matches the final file set and all relative links resolve.
- Verify no template markers, empty headings, accidental mojibake, or UTF-8 BOM remain.
- Inspect the scoped diff and ensure no product/source file changed.

## Validation Commands

```powershell
python "./.trellis/scripts/task.py" validate "00-bootstrap-guidelines"
git diff --check
Get-ChildItem -File "./.trellis/spec/backend" -Filter "*.md" | Select-String -Pattern "To be filled","Replace with your actual","placeholder","TODO: fill" -SimpleMatch
"./gradlew.bat" compileJava compileTestModJava --offline --no-daemon --console=plain
```

Additional read-only verification will check UTF-8/no-BOM bytes, index targets, and that all changed paths stay inside the declared scope. The placeholder search must return no matches; Gradle and Trellis validation must exit 0.

## Review Gates

1. Implementation agent reports exact changed files and command evidence.
2. The domain lead checks actual files and scoped diff rather than accepting the implementation report.
3. An independent read-only check verifies source-backed claims, placeholders, links, encoding, and validation output.
4. The main session performs final integration review. Git commit or other external write requires separate user authorization.

## Rollback Points

- After each guide group, retain a scoped diff so a defective guide can be corrected without touching unrelated files.
- Never use broad `git reset`, `git checkout`, or recursive deletion in this dirty worktree.
