# Comment Reply Query Optimization Troubleshooting

## Context

This note documents the comment service follow-up work from PR #2.
The work started from four review concerns:

- parent comment validation needed to ensure the parent belongs to the current post
- comment tree response assembly could trigger repeated repository lookups
- create/reply/exception cases needed stronger test coverage
- `CommentService` was carrying validation, persistence, tree assembly, and count update responsibilities together

The goal was to fix the behavior and reduce query risk without rewriting the entire comment domain.

## Problem

`CommentService#getComments` first fetched root comments, then called `findRepliesByCommentGroup` while mapping each root comment to a response.
That avoided recursive repository calls inside the tree builder, but it still meant one reply query per root comment on the current page.

For a page of 10 root comments, the shape was effectively:

- 1 query for root comments
- up to 10 additional queries for replies

The query itself was not wrong. The problem was the repeated per-root call pattern.

## Decision Log

### Decision 1: Keep reply loading scoped to the current page

- Choice: fetch replies for only the root comments in the current response page.
- Alternatives: prefetch all replies for a post, cache replies in memory or Redis, keep one query per root comment.
- Tradeoffs: current-page loading still does a second query, but keeps memory and response scope bounded. It does not solve every possible high-volume pagination case, but it removes the obvious N+1 shape without adding infrastructure.
- Rationale: the UI only needs replies for the root comments being returned. Fetching all replies for the post would over-read and could become worse on large posts. Redis would be premature because this was a query-shape issue, not a proven caching problem.

### Decision 2: Replace per-root lookup with `IN` lookup

- Choice: replace `findRepliesByCommentGroup(String commentGroup)` with `findRepliesByCommentGroupIn(List<String> commentGroups)`.
- Alternatives: use JPA entity graphs, batch size tuning, recursive CTEs, or a custom projection query.
- Tradeoffs: the service now groups flat replies by `commentGroup` after fetching them. The repository query is slightly broader than the old single-group query, but it reduces the query count to one reply query per page.
- Rationale: the existing model already uses `commentGroup` as the root grouping key. An `IN` query fits the schema and requires the smallest change.

### Decision 3: Extract only tree assembly first

- Choice: introduce `CommentTreeAssembler` for response conversion and tree assembly.
- Alternatives: split `CommentService` into command/query services immediately, create a validator/policy class first, or leave all helpers in the service.
- Tradeoffs: `CommentService` still owns validation and count changes. The extraction is incomplete as a full service responsibility split, but it removes the most isolated responsibility without creating speculative abstractions.
- Rationale: tree assembly is pure response-building behavior and directly related to the query optimization. Validator and count separation can follow after more behavior is covered.

### Decision 4: Add focused tests instead of broad integration coverage

- Choice: add unit tests for missing parent comment, max depth, empty root comments, and multi-root reply assembly via a single `IN` lookup.
- Alternatives: add controller tests for every exception path, repository integration tests for the JPQL query, or full end-to-end comment API tests.
- Tradeoffs: unit tests do not prove the JPQL query against a real database. They do lock down the service behavior and repository interaction shape. A repository slice test remains a valid follow-up if query portability becomes a concern.
- Rationale: the immediate risk was service behavior and N+1 query shape. The existing `CommentServiceTest` suite was the smallest place to protect that behavior.

## Implementation Summary

- `CommentRepository` now exposes `findRepliesByCommentGroupIn(List<String> commentGroups)`.
- `CommentService#getComments` collects root comment groups from the current page, fetches all replies in one query, groups them by `commentGroup`, and passes each group to the assembler.
- `CommentTreeAssembler` owns deleted-comment masking, author response creation, flat reply-to-tree assembly, and `CommentResponse` construction.
- `CommentServiceTest` now covers:
  - missing parent comment
  - max depth exceeded
  - no root comments avoiding reply lookup
  - multiple root comments using one reply lookup and assembling replies under the correct root

## Validation

Validated with:

```bash
./gradlew test --tests com.vani.week4.backend.comment.service.CommentServiceTest
```

Result:

- 17 tests passed
- build successful

The full test suite was not used as the final validation gate for this change because an existing `PostIntegrationTest` failure was caused by Redis connection failure in the test environment, unrelated to the comment service changes.

## Follow-ups

- Add repository-level coverage for `findRepliesByCommentGroupIn` if JPQL behavior becomes a concern.
- Add controller/API tests that assert comment exceptions map to the expected HTTP status and error body.
- Add boundary tests once the final max comment depth policy is confirmed.
- Consider extracting validation into a `CommentPolicy` or `CommentValidator` after create/update/delete rules grow further.
- Revisit comment count updates separately if concurrency or bulk update requirements become visible.

## Retrospective

The useful framing was separating "a query is necessary" from "the same query shape is repeated per root comment."
The chosen `IN` query kept the request bounded to the current page and avoided introducing cache or a broader domain rewrite.

The service split also worked better when done one responsibility at a time.
Extracting `CommentTreeAssembler` first was low-risk because it moved response assembly without changing command behavior.

Process-wise, the branch needed care.
The local and remote `refactor/comment-test` histories had equivalent patches with different SHAs.
The branch was rebased onto `origin/main`, duplicate upstream patches were dropped, and the existing remote branch was updated with `--force-with-lease`.
