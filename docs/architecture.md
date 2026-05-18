# 시스템 아키텍처 및 통신 구조

## 전체 구성도

```
┌─────────────────────────────────────────────────────────────┐
│                      MCP Orchestrator                        │
│                                                             │
│  ┌──────────────┐   ┌──────────────┐   ┌────────────────┐  │
│  │ McpServer    │   │ Agent        │   │ Web UI         │  │
│  │ Registry     │   │ Service      │   │ (Thymeleaf)    │  │
│  │              │   │              │   │                │  │
│  │ - 서버 등록  │   │ - LLM 연동   │   │ /servers       │  │
│  │ - Tool 수집  │   │ - Tool 선택  │   │ /backoffice    │  │
│  │ - 헬스체크   │   │ - Tool 실행  │   │ /web           │  │
│  └──────┬───────┘   └──────┬───────┘   └────────────────┘  │
│         │                  │                                 │
└─────────┼──────────────────┼─────────────────────────────────┘
          │                  │
    MCP Protocol        MCP Protocol
    (JSON-RPC/HTTP)     (JSON-RPC/HTTP)
          │                  │
    ┌─────▼──────────────────▼──────┐
    │         MCP 서버들             │
    │                               │
    │  weather-server (8081)        │
    │  deepwiki-adapter (8082)      │
    │  기타 MCP 서버 ...             │
    └───────────────────────────────┘
```

## MCP 서버 등록 흐름

등록 시 MCP 관리 서버가 MCP 서버로 **3번의 HTTP 요청**을 순서대로 보낸다.

```
Apidog / Client          Orchestrator               MCP Server
     │                       │                           │
     │── POST /register ────▶│                           │
     │                       │── POST /mcp ─────────────▶│  ① tools/list (필수)
     │                       │◀─ { tools: [...] } ────────│
     │                       │                           │
     │                       │── POST /mcp ─────────────▶│  ② resources/list (선택)
     │                       │◀─ { resources: [...] } ────│
     │                       │                           │
     │◀─ { serverId, status }│                           │
```

- `tools/list` 실패 → `REGISTRATION_FAILED`
- `resources/list` 실패 → 무시하고 `ACTIVE`

## 헬스체크 사이클

```
Orchestrator                MCP Server
     │                           │
     │── GET /health ───────────▶│   60초마다
     │◀─ 200 OK ─────────────────│
```

- 3회 연속 실패 → `INACTIVE`
- 성공 시 `ACTIVE` 복구 + 실패 카운트 리셋

## Agent Tool 실행 흐름

```
사용자 질문
    │
    ▼
AgentService
    │  등록된 ACTIVE 서버의 전체 Tool 목록 로드
    ▼
LLM (GPT-4o-mini)
    │  "어떤 tool을 어떤 파라미터로 쓸지" 판단
    │  → system prompt: 반드시 tool 사용, 자체 지식 사용 금지
    ▼
McpToolCallback
    │
    │── POST {serverUrl}/mcp ──▶ MCP Server
    │   { method: "tools/call",
    │     params: { name, arguments } }
    │◀─ { result: { content: [...] } }
    │
    ▼
LLM이 결과를 자연어로 정리
    │
    ▼
AgentResponse { sessionId, answer, trace[] }
```

## MCP 프로토콜 (간소화된 HTTP 방식)

본 MCP 관리 서버는 MCP 표준의 Streamable HTTP 전송을 단순화하여 사용한다.

| 항목 | 표준 MCP | 본 구현 |
|------|---------|---------|
| 전송 | Streamable HTTP (SSE 포함) | 동기 HTTP POST만 사용 |
| 헬스체크 | 없음 | `GET /health` 추가 |
| 엔드포인트 | `/mcp` | `/mcp` |
| 메시지 포맷 | JSON-RPC 2.0 | JSON-RPC 2.0 |

### 요청 포맷

```json
POST {serverUrl}/mcp
Content-Type: application/json

{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "tools/call",
  "params": {
    "name": "get_current_weather",
    "arguments": { "city": "Seoul" }
  }
}
```

### 응답 포맷

```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "result": {
    "content": [
      { "type": "text", "text": "[서울 현재 날씨] 온도: 25°C ..." }
    ]
  }
}
```

## 외부 상용 MCP 서버 연결 (어댑터 패턴)

표준 MCP Streamable HTTP 서버는 `GET /health`가 없고 SSE 응답을 포함할 수 있어 직접 연결이 불가하다. 얇은 어댑터로 간극을 메운다.

```
Orchestrator ──▶ 어댑터 (로컬) ──▶ 상용 MCP 서버 (원격)
                  - GET /health 추가
                  - SSE → JSON 변환
                  - Accept 헤더 조정
```

**예시: DeepWiki 연결**

```
POST localhost:8082/mcp  →  POST https://mcp.deepwiki.com/mcp
GET  localhost:8082/health  →  어댑터 자체 200 반환
```

## 데이터 저장

- **DB**: H2 파일 DB (`./data/mcporchestrator.mv.db`)
- **재시작 시**: 등록된 서버·앱 메타 유지, Tool 목록도 DB에 저장
- **볼륨 (Docker)**: `/app/data` 마운트 필요
