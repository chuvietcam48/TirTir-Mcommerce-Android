# rune-fix

> Rune L2 Skill | development


# fix

## Platform Constraints

- MUST NOT: Never run commands containing hardcoded secrets, API keys, or tokens. Scan all shell commands for secret patterns before execution.
- MUST: After editing JS/TS files, ensure code follows project formatting conventions (Prettier/ESLint).
- MUST: After editing .ts/.tsx files, verify TypeScript compilation succeeds (no type errors).
- SHOULD: Monitor your context usage. If working on a long task, summarize progress before context fills up.
- MUST: Before summarizing/compacting context, save important decisions and progress to project files.
- SHOULD: Before ending, save architectural decisions and progress to .rune/ directory for future sessions.

## Purpose

Apply code changes. Fix receives a plan, debug finding, or review finding and writes the actual code. It does NOT investigate root causes — that is the rune-debug rule's job. Fix is the action hub: locate, change, verify, report.

<HARD-GATE>
Never change test files to make tests pass unless the tests themselves are provably wrong (wrong expected value, wrong test setup, testing a removed API). The rule: fix the CODE, not the TESTS.
If unsure whether the test is wrong or the implementation is wrong → call `the rune-debug rule` to investigate.
</HARD-GATE>

## Triggers

- Called by `cook` Phase 4 IMPLEMENT — write code to pass tests
- Called by `debug` when root cause found and fix is ready
- Called by `review` when bugs found during review
- `/rune fix <issue>` — manual fix application
- Auto-trigger: after successful debug diagnosis

## Calls (outbound)

- `debug` (L2): when root cause unclear before fixing — need diagnosis first
- `test` (L2): verify fix with tests after applying changes
- `review` (L2): self-review for complex or risky fixes
- `verification` (L3): validate fix doesn't break existing functionality
- `docs-seeker` (L3): check correct API usage before applying changes
- `hallucination-guard` (L3): verify imports after code changes
- `scout` (L2): find related code before applying changes

## Called By (inbound)

- `cook` (L1): Phase 4 IMPLEMENT — apply code changes
- `debug` (L2): root cause found, ready to apply fix
- `review` (L2): bug found during review, needs fixing
- `surgeon` (L2): apply refactoring changes
- `review-intake` (L2): apply fixes identified during structured review intake

## Cross-Hub Connections

- `fix` ↔ `debug` — bidirectional: debug diagnoses → fix applies, fix can't determine cause → debug investigates
- `fix` → `test` — after applying fix, run tests to verify
- `fix` ← `review` — review finds bug → fix applies correction
- `fix` → `review` — complex fix requests self-review

## Execution

### Step 1: Understand

Read and fully understand the fix request before touching any file.

- Read the incoming request: debug report, plan spec, or review finding
- Identify what is broken or missing and what the expected behavior should be
- If the request is ambiguous or root cause is unclear → call `the rune-debug rule` before proceeding
- Note the scope: single function, single file, or multi-file change

### Step 2: Locate

Find the exact files and lines to change.

- Use `the rune-scout rule` to locate the relevant files, functions, and surrounding code
- Read the file to examine the specific file:line identified in the debug report or plan
- Find files by pattern to find related files: types, tests, config that may also need updating
- Map all touch points before writing a single line of code

### Step 3: Change

Apply the minimal set of changes needed.

- Edit the file to targeted modifications to existing files
- Use write/create the file only when creating a genuinely new file is required
- Follow project conventions: naming, immutability patterns, error handling style
- Keep changes minimal — fix the stated problem, do not refactor unrelated code (YAGNI)
- Never use `any` in TypeScript; never use bare `except:` in Python
- If a new import is needed → note it for Step 5 hallucination-guard check

### Step 4: Verify

Confirm the change works and nothing is broken.

- Run a shell command to run the relevant tests: the specific failing test first, then the full suite
- If tests fail after the fix:
  - Investigate with `the rune-debug rule` (max 3 debug loops before escalating)
  - Do NOT change test files to make tests pass — fix the implementation code
- If project has a type-check command, run it via run a shell command
- If project has a lint command, run it via run a shell command

### Step 5: Post-Fix Hardening (Defense-in-Depth)

After the fix works, make the bug **structurally impossible** — not just "fixed this time."

Single validation at one point can be bypassed by different code paths, refactoring, or mocks. Add validation at EVERY layer data passes through:

| Layer | Purpose | Example |
|-------|---------|---------|
| **Entry Point** | Reject invalid input at API boundary | Validate params not empty/exists/correct type |
| **Business Logic** | Ensure data makes sense for this operation | Check preconditions specific to this function |
| **Environment Guard** | Prevent dangerous ops in specific contexts | In tests: refuse writes outside tmpdir |
| **Debug Instrumentation** | Capture context for forensics if bug recurs | Log stack trace + key values before risky ops |

Apply this when: the bug was caused by invalid data flowing through multiple layers. Skip for trivial one-liner fixes.

### Step 6: Self-Review

Verify correctness of the changes just made.

- Call `the rune-hallucination-guard rule` to verify all imports introduced or modified are real and correctly named
- Call `the rune-docs-seeker rule` if any external API, library method, or SDK call was added or changed
- For complex or risky fixes (auth, data mutation, async logic): call `the rune-review rule` for a full quality check

### Step 7: Report

Produce a structured summary of all changes made.

- List every file modified and a one-line description of what changed
- Include verification results (tests, types, lint)
- Note any follow-up work if the fix is partial or has known limitations

## Constraints

1. MUST NOT change test files to make tests pass — fix the CODE, not the TESTS
2. MUST have a diagnosis (from debug or clear error) before applying fixes
3. MUST run tests after each fix attempt — never batch multiple untested changes
4. MUST NOT exceed 3 fix attempts — if 3 fixes fail, re-diagnose via the rune-debug rule (which will classify: wrong approach → brainstorm rescue, wrong design → plan redesign)
5. MUST follow project conventions found by scout — don't invent new patterns
6. MUST NOT add unplanned features while fixing — fix only what was diagnosed
7. MUST track fix attempt number — this feeds debug's 3-Fix Escalation classification

## Mesh Gates

| Gate | Requires | If Missing |
|------|----------|------------|
| Evidence Gate | Debug report OR clear error description before fixing | Run the rune-debug rule first |
| Test Gate | Tests run after each fix attempt | Run tests before claiming fix works |

## Output Format

```
## Fix Report
- **Task**: [what was fixed/implemented]
- **Status**: complete | partial | blocked

### Changes
- `path/to/file.ts` — [description of change]
- `path/to/other.ts` — [description of change]

### Verification
- Lint: PASS | FAIL
- Types: PASS | FAIL
- Tests: PASS | FAIL ([n] passed, [m] failed)

### Notes
- [any caveats or follow-up needed]
```

## Sharp Edges

Known failure modes for this skill. Check these before declaring done.

| Failure Mode | Severity | Mitigation |
|---|---|---|
| Modifying test files to make tests pass | CRITICAL | HARD-GATE blocks this — fix the code, never the tests (unless test setup is provably wrong) |
| Applying fix without a diagnosis | HIGH | Evidence Gate: need debug report or clear error description before touching code |
| Exceeding 3 fix attempts without re-diagnosing | HIGH | Constraint 4: after 3 failures, call debug again — the hypothesis was wrong |
| Introducing unrelated refactoring while fixing | MEDIUM | YAGNI: fix only what was diagnosed — unrelated changes belong in a separate task |
| Not running tests after each individual change | MEDIUM | Constraint 3: never batch untested changes — run tests after each edit |
| Fixing at crash site without tracing data origin | HIGH | Defense-in-depth: trace where bad data ORIGINATES, add validation at every layer it passes through |
| Single-point validation (fix one spot, hope it holds) | MEDIUM | Step 5: add entry + business logic + environment + debug layers for data-flow bugs |

## Done When

- Root cause identified (debug report or clear error received)
- Minimal changes applied targeting only the diagnosed problem
- Tests pass for the fixed functionality (actual output shown)
- Lint and type check pass
- hallucination-guard verified any new imports
- Fix Report emitted with changed files and verification results

## Cost Profile

~2000-5000 tokens input, ~1000-3000 tokens output. Sonnet for code writing quality. Most active skill during implementation.

---
> **Rune Skill Mesh** — 49 skills, 170+ connections
> Source: https://github.com/rune-kit/rune
> Full experience with subagents, hooks, adaptive routing → use Rune on Claude Code.