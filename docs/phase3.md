# Phase 3 - MCP 오케스트레이터 구축 및 통신 규약 검증

## 목표

여러 MCP 서버를 단일 엔드포인트로 묶는 오케스트레이터를 구축하고, `mcp-communication-spec.md`에 정의한 통신 규약이 실제로 동작함을 검증한다.

---

## 구조

```
mcp-test-server (포트 8081)      ← 통신 규약 준수 MCP 서버 (Spring AI 미사용, 순수 JSON-RPC 2.0 구현)
  └─ GET  /health
  └─ POST /mcp  (initialize / tools/list / tools/call)
  └─ Tools: random, echo

mcporchestrator (포트 8080)      ← MCP 오케스트레이터
  └─ POST   /api/mcp/servers/register   MCP 서버 등록
  └─ DELETE /api/mcp/servers/{id}       MCP 서버 삭제
  └─ GET    /api/mcp/servers            등록 서버 목록 조회
  └─ POST   /mcp                        외부 에이전트 엔드포인트 (JSON-RPC 2.0 프록시)
```

---

## 구축 내용

### mcporchestrator

| 클래스 | 역할 |
|--------|------|
| `McpServerRegistry` | 등록된 MCP 서버 인메모리 저장소 (ConcurrentHashMap) |
| `McpServerService` | 등록/삭제/조회 비즈니스 로직 |
| `McpServerController` | REST API (register / delete / list) |
| `ToolsListCollector` | 서버 등록 시 `tools/list` 자동 수집 |
| `HealthCheckPoller` | 5분 주기 헬스체크, 3회 연속 실패 시 INACTIVE |
| `McpProxyController` | `POST /mcp` — initialize / tools/list 집계 / tools/call 라우팅 |

### mcp-test-server

Spring AI 없이 순수 JSON-RPC 2.0으로 직접 구현한 테스트용 MCP 서버. 외부 개발자가 Spring AI 없이도 통신 규약만 따르면 연동 가능함을 증명하는 목적으로 제작했다.

---

## 인증

PAT 포맷 미확정으로 `X-Mcp-Token` 헤더에 임의 토큰을 담는 방식을 플레이스홀더로 사용한다. 파이프라인 로직은 완성된 상태이며, PAT 포맷 확정 시 해당 헤더 검증 로직만 교체하면 된다.

---

## 검증 결과

### 1. 서버 등록

```http
POST /api/mcp/servers/register
X-Mcp-Token: test-token-123
```
```json
{ "name": "mcp-test-server", "url": "http://localhost:8081", "version": "0.0.1" }
```
```
[Registry] registered: mcp-test-server (uuid)
[ToolsCollector] 2 tool(s) collected - server uuid
```

등록 즉시 `tools/list`가 자동 수집되고 상태가 ACTIVE로 전환된다.

---

### 2. tools/list (오케스트레이터 집계)

```http
POST /mcp
{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}
```

등록된 모든 ACTIVE 서버의 tool을 집계하여 반환한다. 외부 에이전트는 8081의 존재를 알 필요 없다.

---

### 3. tools/call 라우팅

```http
POST /mcp
{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"random","arguments":{"min":1,"max":100}}}
```

```
[mcporchestrator] [McpProxy] tool=random routed-to=mcp-test-server
[mcp-test-server] [Tool:random] range=1-100 result=14
```

오케스트레이터(8080)가 tool 이름으로 소유 서버를 찾아 8081로 라우팅한다. `X-Mcp-Token`이 그대로 전파된다.

---

### 4. 헬스체크 폴링 (자동)

```
[HealthCheck] active: mcp-test-server
```

별도 트리거 없이 5분 주기로 등록된 서버의 `/health`를 자동으로 체크한다.

---

### 5. 서버 삭제

```http
DELETE /api/mcp/servers/{serverId}
```

삭제 후 `GET /api/mcp/servers` → `{"servers": []}` 확인.

---

## Phase 2와의 차이

| | Phase 2 | Phase 3 |
|---|---|---|
| MCP 서버 연결 방식 | application.properties 정적 설정 | REST API 동적 등록/삭제 |
| 서버 관리 | 재시작 필요 | 런타임 등록/삭제 |
| tool 수집 | Spring AI 자동 | 등록 시 직접 수집 |
| 헬스체크 | 없음 | 5분 주기 자동 폴링 |
| 인증 | 없음 | X-Mcp-Token 플레이스홀더 |
| 외부 서버 호환 | Spring AI 전용 | MCP 표준 준수 서버 모두 가능 |

---

## 산출물

- `mcporchestrator` — MCP 오케스트레이터 (registry 패키지)
- `mcp-test-server` — 통신 규약 검증용 MCP 서버 (Spring AI 미사용)
- `mcp-communication-spec.md` — 검증 완료된 통신 규약 (연구소 전달 가능 상태)

---

## 다음 단계

→ [Phase 4](./phase4.md): 멀티 서비스 MCP 연동 + 인증 전파
