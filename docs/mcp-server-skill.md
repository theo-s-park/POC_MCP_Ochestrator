# MCP 관리 서버 연동 스킬

이 파일을 읽은 Claude는 개발자가 **MCP 관리 서버에 연동 가능한 MCP 서버**를 구현하도록 도와야 한다.

---

## MCP 관리 서버 개요

MCP 관리 서버는 MCP 서버를 등록·관리하고, PO Web 같은 소비자에게 툴 목록과 실행 API를 제공하는 오케스트레이터다.  
서버 개발자는 정해진 엔드포인트를 구현한 뒤 MCP 관리 서버에 등록하면 된다.

---

## 1. 구현해야 할 엔드포인트

JSON-RPC 2.0, 단일 엔드포인트 `POST /mcp` 방식이다.

| 엔드포인트 | 메서드 | 필수 |
|---|---|---|
| `/health` | GET | O |
| `/mcp` | POST | O |

---

## 2. 각 엔드포인트 스펙

### GET /health

```json
{ "status": "ok" }
```

MCP 관리 서버가 60초마다 폴링한다. 3회 연속 실패 시 서버 상태가 `INACTIVE`로 전환된다.

---

### POST /mcp — tools/list

등록 시 MCP 관리 서버가 1회 자동 호출한다. **1개 이상 반환 필수.**

```json
// Request
{ "jsonrpc": "2.0", "id": 1, "method": "tools/list", "params": {} }

// Response
{
  "jsonrpc": "2.0", "id": 1,
  "result": {
    "tools": [
      {
        "name": "tool_name",
        "description": "툴 설명",
        "inputSchema": {
          "type": "object",
          "properties": {
            "param1": { "type": "string", "description": "파라미터 설명" }
          },
          "required": ["param1"]
        }
      }
    ]
  }
}
```

---

### POST /mcp — tools/call

MCP 관리 서버가 실행 요청을 전달할 때 `arguments` 안에 **`credit` 필드를 자동 주입**한다. 서버는 이를 꺼내서 실행 성공 후 크레딧 서버에 차감 요청을 보내야 한다.

```json
// Request (MCP 관리 서버 → 서버)
{
  "jsonrpc": "2.0", "id": 1,
  "method": "tools/call",
  "params": {
    "name": "tool_name",
    "arguments": {
      "param1": "값",
      "credit": {
        "serviceType": "GPT3",
        "deductCredit": 5
      }
    }
  }
}

// Response (서버 → MCP 관리 서버)
{
  "jsonrpc": "2.0", "id": 1,
  "result": {
    "content": [
      { "type": "text", "text": "실행 결과" }
    ]
  }
}
```

---

### POST /mcp — resources/list (선택)

미구현 또는 빈 배열 반환해도 등록 실패 처리하지 않는다.  
`mimeType: image/*` 리소스가 있으면 MCP 관리 서버가 앱 썸네일로 자동 추출한다.

```json
// Response
{
  "jsonrpc": "2.0", "id": 1,
  "result": {
    "resources": [
      { "uri": "image://logo.png", "name": "App Logo", "mimeType": "image/png" }
    ]
  }
}
```

---

### POST /mcp — resources/read (선택)

`resources/list`를 구현했다면 함께 구현한다.

```json
// Request
{ "jsonrpc": "2.0", "id": 1, "method": "resources/read", "params": { "uri": "image://logo.png" } }

// Response
{
  "jsonrpc": "2.0", "id": 1,
  "result": {
    "contents": [
      { "uri": "image://logo.png", "mimeType": "image/png", "blob": "base64..." }
    ]
  }
}
```

---

## 3. Credit 처리

`tools/call` 요청의 `arguments`에는 항상 `credit` 필드가 포함된다. MCP 관리 서버가 자동 주입하므로 서버가 별도 요청할 필요 없다.

**서버가 해야 할 것**
1. `arguments`에서 `credit` 꺼내기 (비즈니스 로직에 전달하지 않음)
2. 비즈니스 로직 실행
3. 성공 시 → 크레딧 서버에 차감 요청
4. 실패 시 → 차감 없음

**사용자 식별**  
MCP 관리 서버가 `Authorization: Bearer {token}` 헤더를 그대로 전달한다. 크레딧 차감 시 이 토큰을 함께 전달하면 사용자가 특정된다.

| 항목 | 전달 방식 |
|---|---|
| 사용자 식별 | `Authorization: Bearer {token}` 헤더 |
| 서비스 타입 | `arguments.credit.serviceType` (string) |
| 차감 금액 | `arguments.credit.deductCredit` (integer) |

---

## 4. MCP 관리 서버에 등록

서버 구현이 완료되면 아래 API를 한 번 호출하면 된다. CI/CD 파이프라인 마지막 단계에 추가하는 것을 권장한다.

```
POST http://{hub-host}/api/mcp/servers/register
Content-Type: application/json
```

```json
{
  "name": "my-mcp-server",
  "url": "https://my-server.example.com",
  "description": "서버 설명",
  "version": "1.0.0",
  "type": "MCP"
}
```

| 필드 | 필수 | 설명 |
|---|---|---|
| `name` | O | 서버 등록명 |
| `url` | O | Base URL (`/mcp`, `/health` 경로 제외한 루트) |
| `description` | 선택 | |
| `version` | 선택 | |
| `type` | 선택 | `MCP` (기본값) |

**MCP 관리 서버 응답**
```json
{ "serverId": "550e8400-...", "status": "ACTIVE" }
```

| status | 의미 |
|---|---|
| `ACTIVE` | 등록 완료, tools 수집 성공 |
| `REGISTRATION_FAILED` | `/health` 또는 tools 수집 실패 |

동일 URL로 재등록 시 업데이트로 처리되며 tools/resources가 최신 상태로 갱신된다.

---

## 5. 등록 후 MCP 관리 서버 동작

- 등록 즉시 `tools/list`를 호출해 툴 목록 수집
- 이후 60초마다 `GET /health` 폴링
- 헬스체크 3회 연속 실패 → `INACTIVE` (이후 복구 감지 시 자동 `ACTIVE` 전환)
- 재등록 시 tools/resources 재수집

---

## 6. MCP 관리 서버가 소비자에게 노출하는 공개 API (참고)

서버가 등록되면 MCP 관리 서버 Backoffice에서 툴별로 `serviceType`, `deductCredit`, `visible`을 설정할 수 있다.  
설정 후 소비자(PO Web 등)는 아래 API로 앱과 툴 정보를 조회한다.

```
GET /api/mcp/apps/public
```

```json
[
  {
    "id": "app-uuid",
    "displayName": "앱 이름",
    "description": "설명",
    "thumbnail": "https://...",
    "mcpUrl": "https://my-server.example.com",
    "tools": [
      {
        "name": "tool_name",
        "description": "툴 설명",
        "inputSchema": { "type": "object", "properties": { "param1": { "type": "string" } } },
        "serviceType": "GPT3",
        "deductCredit": 5
      }
    ]
  }
]
```
