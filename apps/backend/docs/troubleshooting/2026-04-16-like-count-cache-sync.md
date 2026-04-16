# Like Count Cache Sync Troubleshooting Log

## Context

- Date: `2026-04-16`
- Related roadmap: [docs/roadmaps/issue-3-like-count-sync-roadmap.md](/home/vanillab/Bapsang/apps/backend/docs/roadmaps/issue-3-like-count-sync-roadmap.md)
- Scope: backend like count cache read/write path, Redis-DB sync path, unit tests

## Problem

좋아요 count 로직이 `LikeService` 와 scheduler 에 나뉘어 있었고, 둘 다 Redis 세부사항을 직접 다루고 있었다. 이 상태에서는 다음 문제가 있었다.

- 캐시 미스 상태에서 `decrement()` 가 먼저 실행되면 DB 기준 count 와 다른 값이 만들어질 수 있었다.
- count 조회, count 증감, DB 반영 규칙이 한 군데에 모여 있지 않아 정합성 규칙이 흔들릴 여지가 있었다.
- scheduler 가 실행 트리거 역할과 실제 sync 로직을 동시에 가져서 테스트 초점이 흐려졌다.

제약은 현재 Spring/Redis/JPA 구조를 유지한 채, 좁은 리팩터링으로 해결해야 한다는 점이었다.

## Decision Log

### Decision 1: 캐시 책임을 `LikeCountCache` 로 분리

- Choice:
  `LikeService` 에서 Redis 직접 접근을 제거하고 `LikeCountCache` 가 count 조회, 캐시 복구, 증감 책임을 맡도록 분리했다.
- Alternatives:
  RedisTemplate 접근을 `LikeService` 내부에 유지한 채 보조 메서드만 늘리는 방법.
- Tradeoffs:
  클래스 수가 하나 늘고 wiring 이 추가된다.
- Rationale:
  count 규칙이 한 곳에 있어야 캐시 미스 fallback, 파싱 실패 복구, 음수 방지를 같은 기준으로 처리할 수 있다.

### Decision 2: 증감 전에 DB fallback 을 허용

- Choice:
  increment/decrement 전에 캐시를 읽고, 값이 없거나 숫자 변환에 실패하면 DB count 를 다시 읽어 Redis 에 복구한 뒤 연산한다.
- Alternatives:
  캐시가 비어 있어도 Redis `increment/decrement` 를 그대로 호출하고 나중에 scheduler 에 맡기는 방법.
- Tradeoffs:
  캐시 미스 시 DB 조회가 한 번 추가된다.
- Rationale:
  지금 코드베이스에서는 write path 의 정합성이 read 최적화보다 중요하다. 캐시 미스에서 왜곡된 count 를 만든 뒤 나중에 수습하는 방식보다, 즉시 복구 후 연산하는 쪽이 리뷰와 운영 모두에서 예측 가능하다.

### Decision 3: scheduler 는 트리거만 담당

- Choice:
  실제 sync 로직을 `LikeCountSynchronizer` 로 옮기고, `LikeSyncScheduler` 는 스케줄 실행과 로그만 담당하게 정리했다.
- Alternatives:
  scheduler 내부에서 key 순회, 값 파싱, DB 반영을 그대로 유지.
- Tradeoffs:
  동기화 클래스가 추가된다.
- Rationale:
  sync 경로의 예외 처리 규칙을 테스트하기 쉬워지고, 나중에 수동 실행이나 다른 배치 진입점이 생겨도 재사용 가능하다.

## Implementation Summary

- [LikeService.java](/home/vanillab/Bapsang/apps/backend/src/main/java/com/vani/week4/backend/interaction/service/LikeService.java) 에서 RedisTemplate 의존성을 제거하고 토글 유스케이스만 남겼다.
- [LikeCountCache.java](/home/vanillab/Bapsang/apps/backend/src/main/java/com/vani/week4/backend/interaction/service/LikeCountCache.java) 를 추가해 count 조회, 캐시 미스 복구, 증감, key 조회를 모았다.
- [LikeCountSynchronizer.java](/home/vanillab/Bapsang/apps/backend/src/main/java/com/vani/week4/backend/post/batch/LikeCountSynchronizer.java) 를 추가해 key 순회와 DB 반영을 분리했다.
- [LikeSyncScheduler.java](/home/vanillab/Bapsang/apps/backend/src/main/java/com/vani/week4/backend/post/batch/LikeSyncScheduler.java) 는 synchronizer 호출만 하도록 단순화했다.
- [LikeCountCacheTest.java](/home/vanillab/Bapsang/apps/backend/src/test/java/com/vani/week4/backend/interaction/service/LikeCountCacheTest.java), [LikeServiceTest.java](/home/vanillab/Bapsang/apps/backend/src/test/java/com/vani/week4/backend/interaction/service/LikeServiceTest.java), [LikeCountSynchronizerTest.java](/home/vanillab/Bapsang/apps/backend/src/test/java/com/vani/week4/backend/post/batch/LikeCountSynchronizerTest.java) 를 추가했다.

명시적으로 제외한 작업:

- 실제 Redis/JPA 를 붙인 통합 테스트 추가
- Redis `keys("post:like:*")` 사용 방식의 성능 개선
- 게시글 삭제 시 캐시 키 정리 정책 추가

## Validation

실행한 검증:

- `./gradlew test --tests 'com.vani.week4.backend.interaction.service.LikeCountCacheTest' --tests 'com.vani.week4.backend.interaction.service.LikeServiceTest' --tests 'com.vani.week4.backend.post.batch.LikeCountSynchronizerTest'`
  - 결과: `7/7` 통과
- `./gradlew test --tests 'com.vani.week4.backend.post.PostServiceTest'`
  - 결과: `4/4` 통과

검증 중 보인 점:

- sandbox 환경에서는 Gradle wrapper lock 파일을 쓸 수 없어 테스트 실행 권한 승인이 한 번 필요했다.
- Gradle deprecated warning 이 출력됐지만 이번 변경의 기능 검증과 직접 관련된 실패는 없었다.

## Follow-ups

- 실제 Redis 와 JPA 를 함께 쓰는 통합 테스트를 추가해 scheduler 실행 전후 DB `likeCount` 수렴 여부를 확인할 것.
- `keys` 기반 전체 스캔이 운영 트래픽에 맞는지 확인하고, 필요하면 cursor 기반 순회나 dirty-set 전략으로 바꿀 것.
- 없는 게시글에 대한 캐시 키가 오래 남을 수 있으므로 정리 정책을 별도로 정할 것.

## Retrospective

잘 된 점:

- 기존 스택과 서비스 경계를 유지하면서 책임만 분리해 변경 범위를 작게 잡을 수 있었다.
- 캐시 미스, 증감, sync 예외 경로를 단위 테스트로 바로 잠가서 리팩터링이 방어되었다.

주의할 점:

- 현재 구조는 정합성 면에서는 나아졌지만, write path 에서 캐시 미스가 자주 나면 DB count 조회 비용이 커질 수 있다.
- sync 는 여전히 Redis 전체 key 스캔에 의존하므로 데이터가 커지면 병목 후보가 된다.

다음에 같은 류의 작업을 할 때는, scheduler 를 건드리기 전에 먼저 "실행 트리거" 와 "실제 처리 로직" 을 나누는 기준부터 문서화해 두는 편이 낫다.
