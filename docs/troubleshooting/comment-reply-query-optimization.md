# 댓글 대댓글 조회 최적화 트러블슈팅

## 배경

이 문서는 PR #2에서 진행한 댓글 서비스 개선 작업을 정리한다.
작업은 다음 리뷰 포인트에서 시작했다.

- 부모 댓글이 현재 게시글에 속하는지 검증하는 조건이 잘못되어 있었다.
- 댓글 트리 응답 조립 과정에서 repository 조회가 반복될 수 있었다.
- 댓글 생성, 대댓글 생성, 예외 케이스를 보호하는 테스트가 부족했다.
- `CommentService`가 검증, 저장, 트리 조립, 카운트 변경 책임을 함께 가지고 있었다.

목표는 댓글 도메인 전체를 다시 설계하는 것이 아니라, 현재 문제가 되는 동작을 테스트로 보호하면서 조회 구조와 책임 경계를 작게 개선하는 것이었다.

## 문제

기존 `CommentService#getComments`는 먼저 루트 댓글을 조회한 뒤, 각 루트 댓글을 응답으로 변환하는 과정에서 `findRepliesByCommentGroup`을 호출했다.

트리 조립 함수 내부에서 재귀적으로 repository를 호출하지는 않았지만, 루트 댓글마다 대댓글 조회가 한 번씩 발생하는 구조였다.

예를 들어 한 페이지에 루트 댓글이 10개 있으면 조회 형태는 다음과 같았다.

- 루트 댓글 조회 1번
- 대댓글 조회 최대 10번

대댓글을 조회하는 것 자체가 문제는 아니었다.
문제는 같은 요청 안에서 루트 댓글 수만큼 조회가 반복되는 구조였다.

## 의사결정 기록

### 결정 1: 현재 페이지의 루트 댓글만 대상으로 대댓글을 조회한다

- 선택: 현재 응답 페이지에 포함된 루트 댓글들의 대댓글만 조회한다.
- 다른 선택지: 게시글의 모든 대댓글을 한 번에 조회한다, replies를 메모리나 Redis에 캐시한다, 기존처럼 루트 댓글마다 조회한다.
- 트레이드오프: 현재 페이지 기준 조회도 쿼리 2번은 필요하다. 대신 응답 범위와 메모리 사용량이 현재 페이지로 제한된다. 모든 대댓글을 한 번에 가져오는 방식보다 과조회 위험이 작다.
- 선택 이유: 화면은 현재 페이지의 루트 댓글에 붙일 대댓글만 필요하다. 게시글 전체 대댓글을 미리 가져오면 댓글이 많은 게시글에서 오히려 비용이 커질 수 있다. Redis 캐시는 아직 실제 병목이 확인된 문제가 아니라서 이 단계에서는 과한 선택이었다.

### 결정 2: 루트별 조회를 `IN` 조회로 바꾼다

- 선택: `findRepliesByCommentGroup(String commentGroup)`을 `findRepliesByCommentGroupIn(List<String> commentGroups)`로 교체한다.
- 다른 선택지: JPA entity graph를 사용한다, Hibernate batch size를 조정한다, recursive CTE를 사용한다, projection 전용 쿼리를 만든다.
- 트레이드오프: 서비스에서 조회된 대댓글을 `commentGroup` 기준으로 다시 묶어야 한다. repository 쿼리는 단일 그룹 조회보다 넓어지지만, 페이지당 대댓글 조회 횟수는 1번으로 줄어든다.
- 선택 이유: 현재 모델은 이미 `commentGroup`을 루트 댓글 그룹 키로 사용하고 있다. 따라서 `IN` 조회는 기존 스키마와 잘 맞고, 구조 변경 범위도 가장 작다.

### 결정 3: 서비스 분리는 트리 조립 책임부터 시작한다

- 선택: `CommentTreeAssembler`를 추가해 응답 변환과 트리 조립을 서비스에서 분리한다.
- 다른 선택지: `CommentCommandService`와 `CommentQueryService`를 바로 나눈다, `CommentValidator`나 `CommentPolicy`를 먼저 만든다, 기존 서비스 private helper로 유지한다.
- 트레이드오프: `CommentService`에는 아직 검증과 카운트 변경 책임이 남아 있다. 완전한 책임 분리는 아니지만, 가장 독립적인 트리 조립 책임을 먼저 분리해 변경 범위를 줄였다.
- 선택 이유: 트리 조립은 저장/검증과 성격이 다르고, 이번 조회 최적화와 직접 연결되어 있다. 검증 정책이나 카운트 변경 분리는 규칙이 더 늘어나거나 동시성 요구가 분명해졌을 때 진행하는 편이 낫다.

### 결정 4: 테스트는 서비스 동작과 조회 형태를 먼저 보호한다

- 선택: 존재하지 않는 부모 댓글, 최대 depth 초과, 루트 댓글 없음, 여러 루트 댓글 대댓글 일괄 조립 케이스를 `CommentServiceTest`에 추가한다.
- 다른 선택지: 모든 예외에 대한 controller 테스트를 먼저 추가한다, JPQL repository 통합 테스트를 추가한다, 댓글 API 전체 end-to-end 테스트를 작성한다.
- 트레이드오프: 단위 테스트만으로 JPQL이 실제 DB에서 동작하는지까지 보장하지는 않는다. 대신 서비스 동작과 repository 호출 형태를 빠르게 고정할 수 있다.
- 선택 이유: 당장 위험한 부분은 서비스의 검증 동작과 루트별 반복 조회 구조였다. 기존 테스트 기반도 `CommentServiceTest`에 있었기 때문에 가장 작은 비용으로 회귀 방어선을 만들 수 있었다.

## 구현 요약

- `CommentRepository`에 `findRepliesByCommentGroupIn(List<String> commentGroups)`를 추가했다.
- `CommentService#getComments`는 현재 페이지의 루트 댓글에서 `commentGroup` 목록을 만든 뒤, 대댓글을 한 번에 조회하고 `commentGroup` 기준으로 묶는다.
- `CommentTreeAssembler`는 삭제 댓글 마스킹, 작성자 응답 생성, flat replies를 tree로 조립하는 책임을 가진다.
- `CommentServiceTest`에 다음 케이스를 추가했다.
  - 존재하지 않는 부모 댓글
  - 최대 depth 초과
  - 루트 댓글이 없을 때 대댓글 조회를 하지 않는 케이스
  - 여러 루트 댓글의 대댓글을 한 번에 조회하고 각 루트에 조립하는 케이스

## 검증

다음 명령으로 검증했다.

```bash
./gradlew test --tests com.vani.week4.backend.comment.service.CommentServiceTest
```

결과:

- 17개 테스트 성공
- build successful

전체 테스트를 최종 검증 기준으로 사용하지는 않았다.
기존 `PostIntegrationTest`가 Redis 연결 실패로 깨지는 문제가 있었고, 이는 댓글 서비스 변경과 직접 관련이 없는 테스트 환경 문제였다.

## 후속 작업

- `findRepliesByCommentGroupIn`에 대한 repository-level 테스트 추가 검토
- 댓글 예외가 기대한 HTTP 상태와 에러 바디로 변환되는 controller/API 테스트 추가
- 댓글 최대 depth 정책 확정 후 경계값 테스트 추가
- 검증 규칙이 더 늘어나면 `CommentPolicy` 또는 `CommentValidator` 분리 검토
- 댓글 count 변경 책임 분리 또는 동시성 대응 필요성 검토

## 회고

이번 작업에서 중요한 구분은 "대댓글 조회가 필요하다"와 "루트 댓글마다 같은 형태의 조회가 반복된다"를 분리해서 보는 것이었다.
처음에는 `findRepliesByCommentGroup` 자체가 필요한 쿼리처럼 보였지만, 실제 개선 포인트는 조회 필요 여부가 아니라 조회 반복 구조였다.

`IN` 조회는 현재 페이지로 범위를 제한하면서도 루트 수만큼 반복되는 쿼리를 제거했다.
캐시, 전체 prefetch, 복잡한 query projection 같은 선택지도 있었지만, 현재 문제에는 기존 모델의 `commentGroup`을 활용하는 방식이 가장 작고 명확했다.

서비스 분리도 한 번에 크게 나누는 대신 `CommentTreeAssembler`부터 분리한 것이 적절했다.
트리 조립은 저장, 검증, 카운트 변경과 변경 이유가 다르고, 테스트로도 분리 효과를 확인하기 쉬웠다.

브랜치 정리 과정도 교훈이 있었다.
로컬과 원격 `refactor/comment-test`가 같은 패치이지만 다른 SHA를 가진 상태였기 때문에, 기존 브랜치를 `origin/main` 위로 rebase하고 중복 패치를 정리한 뒤 `--force-with-lease`로 push했다.
이런 경우 새 브랜치로 우회하기보다, 사용자가 기대한 기존 브랜치 흐름을 먼저 확인하는 것이 맞다.
