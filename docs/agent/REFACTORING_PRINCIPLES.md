# Refactoring Principles

This document captures the refactoring direction for the `Bapsang` monorepo.
Use it as a reminder before changing backend, frontend, tests, or architecture.

## North Star

Refactor to make the project easier to change safely.

The goal is not to make the code look more "enterprise" or to apply design patterns for their own sake. The goal is:

- lower coupling
- higher cohesion
- clearer responsibility boundaries
- safer behavior changes through tests
- smaller, reviewable steps

## Core Mindset

- Fix behavior before polishing structure.
- Prefer small, repeated improvements over one huge rewrite.
- Introduce patterns only when they reduce a real pain.
- Keep current stack defaults unless there is a clear reason to change them.
- Separate feature work, bug fixes, and structure-only refactoring whenever possible.

## Coupling Rules

Reduce coupling by making each layer depend on the right things.

- Controllers should handle request/response mapping, not business rules.
- Services should express use cases, not become catch-all utility classes.
- Repositories should handle persistence queries, not domain decisions.
- External systems such as Redis, S3, JWT, and HTTP APIs should be hidden behind focused components.
- Frontend pages should not directly mix API calls, DOM rendering, state management, and routing logic when the flow grows.

When a class is hard to test, ask:

- Does it know too much about external systems?
- Does it coordinate too many unrelated flows?
- Is it mixing read behavior and write behavior?
- Is a small adapter or policy object missing?

## Cohesion Rules

Increase cohesion by grouping code that changes for the same reason.

A class or module should be explainable in one sentence.

Good examples:

- `RefreshTokenStore`: stores, rotates, and deletes refresh tokens.
- `CommentTreeAssembler`: builds comment response trees from flat comments.
- `LikeCountCache`: reads and writes like counts in Redis.
- `commentRenderer`: renders comment data into DOM elements.

Warning signs:

- "This class handles login, signup, password checks, token storage, email lookup, and account cleanup."
- "This page file calls APIs, builds DOM, handles state, handles routing, and owns error handling."
- "This service has to change whenever cache, persistence, response shape, and validation rules change."

## Design Pattern Guidance

Do not start by asking, "Which pattern should I apply?"

Start by asking, "What pain am I trying to reduce?"

- Too many external dependencies in business logic: use Adapter / Port style boundaries.
- Complex object creation: consider Factory.
- Complex conditional behavior: consider Strategy or Policy objects.
- Large use-case orchestration: consider Facade or Application Service.
- Repeated response construction: consider Assembler or Mapper.
- Cache and database consistency rules: consider a dedicated synchronization component.

Avoid pattern-first refactoring. A named pattern is useful only when it makes the code easier to understand and test.

## TDD-Lite Workflow

Use this for bug fixes and behavior-heavy refactoring.

1. State the behavior in one sentence.
2. Write the smallest failing test that captures that behavior.
3. Make the test pass with the smallest code change.
4. Refactor only after the behavior is protected.
5. Run the narrowest relevant verification command.
6. Commit the behavior fix separately from larger cleanup when practical.

Example:

```text
Behavior: A reply can be created only when its parent comment belongs to the same post.
Failing test: Creating a reply under another post's comment is rejected.
Minimal fix: Compare parent.post.id with the target post id.
Refactor: Extract parent validation if it starts growing.
```

This is not strict academic TDD. It is a practical habit:

- no big untested rewrites
- no refactor without a safety net when behavior is risky
- no giant test suite requirement before making progress

## Backend Principles

### Controller

- Keep controllers thin.
- Validate request shape and delegate use cases.
- Avoid business decisions in controller methods.
- Return consistent status codes and DTOs.

### Service

- Prefer use-case-oriented services.
- Split command and query responsibilities when a service grows too broad.
- Keep transaction boundaries explicit and intentional.
- Avoid calling unrelated services just to fetch incidental data.

Potential future split examples:

- `PostCommandService`
- `PostQueryService`
- `CommentCommandService`
- `CommentQueryService`
- `AuthCommandService`
- `TokenLifecycleService`

### Domain and Policy

- Put domain rules near the domain when possible.
- Pull reusable rules into small policy objects when they are shared or complex.
- Avoid scattering authorization, validation, and status transition rules across controllers and services.

### Infrastructure Boundaries

- Redis should be accessed through focused cache/store components.
- S3 URL creation should be wrapped behind a clear provider/service boundary.
- JWT parsing and generation should stay behind token-specific components.
- Infrastructure failures should not leak confusing low-level errors into business flows.

Potential extraction candidates:

- `RefreshTokenStore`
- `LikeCountCache`
- `LikeCountSynchronizer`
- `S3ObjectUrlProvider`
- `CurrentUserPolicy`

## Frontend Principles

The frontend currently uses Vanilla JS, HTML, CSS, and Express. Preserve that unless a framework migration is explicitly chosen.

Keep page code from becoming a god module.

- `api`: HTTP communication only.
- `store`: auth/session state only.
- `renderer`: DOM creation and updates.
- `controller`: page orchestration and event binding.
- `utils`: small reusable helpers.

Potential extraction candidates:

- `commentRenderer`
- `commentPageController`
- `postDetailController`
- `authSession`
- `apiErrorHandler`

Avoid adding build complexity just to make small changes easier. Improve the current structure first.

## Testing Priorities

Start tests where behavior is risky and refactoring is likely.

Backend first:

- comment creation and reply validation
- comment tree response assembly
- refresh token rotation and logout
- like count cache fallback and synchronization
- current user resolution failure cases

Frontend first:

- API client response handling
- auth refresh flow assumptions
- comment rendering from API response shape
- page controller behavior around missing or failed API data

Do not chase coverage numbers early. Prefer high-value tests around bugs, boundaries, and regressions.

## Examples

Examples are kept separate so this principles document stays general.

- `docs/agent/examples/comment-refactoring.md`

## Commit Discipline

Prefer commits that tell a clear story.

Good examples:

- `test(comment): cover reply parent validation`
- `fix(comment): validate parent belongs to target post`
- `refactor(comment): extract comment tree assembler`
- `test(auth): cover refresh token rotation`
- `refactor(auth): isolate refresh token store`

Avoid commits that mix unrelated work:

- comment bug fix plus CSS cleanup
- auth refactor plus Docker changes
- test setup plus feature behavior changes

## Stop Conditions

Pause and reassess when:

- a refactor requires changing multiple domains at once
- a test needs excessive mocking to express a simple behavior
- a pattern makes the code harder to read
- a change affects authentication, persistence, deployment, or data deletion strategy
- the agent or developer cannot explain the class responsibility in one sentence

When in doubt, choose the smaller safe step.
