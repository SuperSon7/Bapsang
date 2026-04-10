# Agent Workflows

This document applies the Software 3.0 / Harness mindset to this repository.

The goal is not to let an AI "just do everything." The goal is to give the agent clear context, safe tools, reviewable workflows, and human checkpoints where judgment matters.

## Mental Model

- `AGENTS.md` is stable project configuration.
- `docs/agent` contains workflow details that may evolve.
- Scripts should handle deterministic repeated work when they are added.
- The agent should ask questions only when the decision is risky, irreversible, costly, or ambiguous.

## Review Workflow

Use this before refactoring or when investigating quality issues.

1. Inspect the smallest relevant scope first.
2. Identify behavior bugs, security risks, data consistency issues, missing tests, and unclear boundaries.
3. Report findings before proposing broad rewrites.
4. Prefer fixing one behavior risk at a time.
5. Add or update tests around the behavior before larger restructuring.

Good targets in this repository:

- Backend auth and token lifecycle
- Backend comment creation and tree rendering
- Backend like count cache / database synchronization
- Frontend API client and auth flow
- Frontend comment rendering and pagination

## TDD-Lite Workflow

Use this when fixing bugs or refactoring behavior-heavy code.

1. State the behavior in one sentence.
2. Add the smallest failing test that captures the behavior.
3. Make the test pass with the smallest implementation change.
4. Refactor only after the behavior is protected.
5. Run the narrowest relevant verification command.
6. Summarize what was tested and what was not tested.

For a concrete example, see `docs/agent/examples/comment-refactoring.md`.

## Refactoring Workflow

Use this when improving coupling, cohesion, or readability.
For project-specific refactoring principles, see `docs/agent/REFACTORING_PRINCIPLES.md`.

1. Separate behavior fixes from structure-only changes.
2. Keep commits small and focused.
3. Prefer responsibility-based extraction over pattern-first design.
4. Introduce patterns only when they reduce a real pain.
5. Avoid adding framework or infrastructure complexity unless it solves a current problem.

Useful backend extraction candidates:

- `CommentTreeAssembler`
- `RefreshTokenStore`
- `LikeCountCache`
- `CurrentUserResolverPolicy`
- `S3ObjectUrlProvider`

Useful frontend extraction candidates:

- `commentRenderer`
- `commentPageController`
- `authSession`
- `apiErrorHandler`
- `postDetailController`

## Human Checkpoints

Ask the user before:

- Removing files or deleting data
- Changing database technology or schema strategy
- Changing authentication/token storage strategy
- Replacing Vanilla JS with a frontend framework
- Changing deployment or cloud architecture
- Making broad package or dependency upgrades

Proceed without asking for:

- Narrow bug fixes
- Small tests around an identified behavior
- Documentation updates
- Formatting or naming cleanup within existing conventions
- Local-only scripts that do not change runtime behavior

## Context Budget Rules

- Do not load the whole repository when a single feature area is enough.
- Start from controllers/API entry points, then move to services, repositories, DTOs, and tests as needed.
- Keep `AGENTS.md` short; move detailed workflow notes here.
- When repeated instructions become mechanical, prefer adding a script later instead of expanding prose.

## Examples

- `docs/agent/examples/comment-refactoring.md`
