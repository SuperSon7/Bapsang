# Bapsang

사랑하는 사람의 요리법과 추억을 기록하는 아카이빙 커뮤니티 서비스입니다.
이 저장소는 백엔드와 프론트엔드를 함께 관리하는 모노레포입니다.

## Apps

| App | Stack | README |
| --- | --- | --- |
| Backend | Java 21, Spring Boot 3, Gradle, MySQL, Redis | [apps/backend/README.md](apps/backend/README.md) |
| Frontend | Vanilla JavaScript, HTML, CSS, Express | [apps/frontend/README.md](apps/frontend/README.md) |

각 앱 README에는 실행 환경, 화면 자료, 아키텍처 설명처럼 앱 단위로 자주 바뀌는 내용을 둡니다.
루트 README는 GitHub 저장소 첫 화면에서 프로젝트 구조와 주요 문서로 이동하는 진입점 역할만 합니다.

## Repository Layout

```text
apps/
  backend/    Spring Boot backend application
  frontend/   Vanilla JS frontend and Express serving layer
docs/
  agent/      AI-assisted workflow notes and working agreements
```

## Local Development

백엔드는 백엔드 앱 디렉터리에서 실행합니다.

```bash
cd apps/backend
./gradlew test
./gradlew bootRun
```

프론트엔드는 프론트엔드 앱 디렉터리에서 실행합니다.

```bash
cd apps/frontend
npm install
npm test
node server.js
```

## Documentation

- [Backend architecture](apps/backend/docs/ARCHITECTURE.md)
- [Backend design](apps/backend/docs/BACKEND_DESIGN.md)
- [Agent workflows](docs/agent/WORKFLOWS.md)
- [Refactoring principles](docs/agent/REFACTORING_PRINCIPLES.md)
