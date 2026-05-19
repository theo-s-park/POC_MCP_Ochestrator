# MCP 관리 서버 — 서비스 아키텍처 (POC)

> 작성일: 2026-05-19

아키텍처 다이어그램: [docs/img/service-architecture.excalidraw](./img/service-architecture.excalidraw)

---

## 기술 스택

| 레이어 | 기술 |
|---|---|
| 런타임 | Java 25 / Spring Boot 4.x |
| LLM 연동 | Spring AI + OpenAI GPT-4o-mini |
| 데이터 | H2 File DB (JPA/Hibernate) |
| UI | Thymeleaf (서버사이드 렌더링) |
| API 문서 | SpringDoc (Swagger UI `/swagger-ui`) |
| 배포 | Docker on EC2 (Amazon Linux 2023) |
| MCP 서버 (POC) | Python/FastAPI (Weather :5001), Node.js (DeepWiki :5002) |

---

## 내부 컴포넌트

| 패키지 | 주요 클래스 | 역할 |
|---|---|---|
| `registry` | `McpServerController` | 서버 등록·조회·삭제·probe REST API |
| `registry` | `McpServerRegistry` | ConcurrentHashMap + JPA 이중 저장, 앱 시작 시 DB 복원 |
| `registry` | `HealthCheckPoller` | 60s 스케줄, 3회 연속 실패 → INACTIVE |
| `registry` | `McpCapabilityCollector` | 등록 시 tools/list · resources/list 수집 |
| `app` | `McpAppController` | Backoffice·WEB 앱 메타데이터 API |
| `agent` | `AgentService` | Spring AI ChatClient + ToolCallback 동적 등록 (Agent Demo) |
| `web` | `WebExecuteController` | OAuth token 교환 후 MCP 서버 tools/call 호출 |
| `auth` | `OAuthClient` | 인터페이스. 현재 StubOAuthClient, 추후 실서버 연동 |

---

## 데이터 모델

**`mcp_server`**

| 컬럼 | 타입 | 설명 |
|---|---|---|
| `serverId` | UUID (PK) | |
| `name` | string | 등록명 |
| `url` | string | Base URL |
| `type` | `MCP` / `WEBAPP` | |
| `status` | `PENDING` / `ACTIVE` / `INACTIVE` / `REGISTRATION_FAILED` | |
| `toolsJson` | TEXT | tools/list 결과 JSON 직렬화 |
| `resourcesJson` | TEXT | resources/list 결과 JSON 직렬화 |
| `registeredAt` | timestamp | 최초 등록 시각 |
| `healthCheckFailures` | int | 연속 실패 카운트 |

**`mcp_app`** — 서버 등록 시 자동 생성 (1:1)

| 컬럼 | 타입 | 설명 |
|---|---|---|
| `id` | UUID (PK) | |
| `mcpServerId` | UUID (FK, unique) | |
| `displayName` | string | Backoffice 설정, WEB 노출 |
| `thumbnail` | TEXT | 이미지 URL |
| `credit` | int | 실행당 크레딧 (참고용) |
| `description` | TEXT | 공개용 설명 |
| `isVisible` | boolean | WEB 공개 여부 (기본 false) |

---

## POC 한계 및 미구현 항목

| 항목 | 현재 상태 | 예정 |
|---|---|---|
| 인증 | StubOAuthClient (토큰 검증 없음) | OAuth 서버 연동 (스펙 확정 후) |
| 크레딧 차감 | 미구현, credit 필드는 참고용 | MCP 서버가 Credit Server 직접 호출 |
| DB | H2 File (재시작 시 데이터 유지) | 추후 RDS 전환 |
| WEB UI | Thymeleaf (관리·데모 겸용) | 별도 프론트엔드 앱 |
