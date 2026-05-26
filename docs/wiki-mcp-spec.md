# MCP 서버 구현 및 등록 가이드

이 문서는 **MCP 관리 서버에 등록하려는 서버 개발자**를 대상으로 한다.  
등록을 위해 구현해야 할 엔드포인트와 데이터 포맷을 정의한다.

> **확정된 내용만 기술한다.** 실행 흐름(누가 어떻게 호출하는지)은 별도 확정 예정이며 이 문서에 포함하지 않는다.

---

## 1. 서버 타입 선택

등록 시 `type` 필드로 통신 방식을 선택한다.

| type | 통신 방식 | 사용 대상 |
|---|---|---|
| `MCP` | JSON-RPC 2.0, 단일 엔드포인트 `POST /mcp` | MCP 표준을 따르는 서버 |
| `WEBAPP` | REST HTTP, 엔드포인트가 메서드 역할 | 일반 웹 서버 |

두 타입 모두 **데이터 구조는 동일**하다. MCP는 JSON-RPC 래퍼가 있고 WEBAPP은 없는 차이뿐이다.

---

## 2. MCP 타입 구현 요건 (JSON-RPC 2.0)

### 필수 엔드포인트

| 엔드포인트 | 메서드 | 필수 | 설명 |
|---|---|---|---|
| `/health` | GET | O | 60초마다 폴링. 3회 연속 실패 시 INACTIVE |
| `/mcp` | POST | O | JSON-RPC 2.0. 모든 메서드를 이 경로로 수신 |

### GET /health

```json
{ "status": "ok" }
```

### POST /mcp — tools/list

등록 시 1회 자동 호출. **1개 이상 반환 필수.**

```json
// Request
{ "jsonrpc": "2.0", "id": 1, "method": "tools/list", "params": {} }

// Response
{
  "jsonrpc": "2.0", "id": 1,
  "result": {
    "tools": [
      {
        "name": "get_current_weather",
        "description": "도시의 현재 날씨를 조회합니다.",
        "inputSchema": {
          "type": "object",
          "properties": {
            "city": { "type": "string", "description": "도시 이름" }
          },
          "required": ["city"]
        }
      }
    ]
  }
}
```

### POST /mcp — tools/call

```json
// Request
{
  "jsonrpc": "2.0", "id": 1,
  "method": "tools/call",
  "params": {
    "name": "get_current_weather",
    "arguments": {
      "city": "서울",
      "credit": { "serviceType": "GPT3", "deductCredit": 50 }
    }
  }
}

// Response
{
  "jsonrpc": "2.0", "id": 1,
  "result": {
    "content": [
      { "type": "text", "text": "[서울 현재 날씨] 온도: 25°C ..." }
    ]
  }
}
```

> `arguments.credit`은 MCP 관리 서버가 자동 주입한다. [→ 3절 참고](#3-credit-처리)

### POST /mcp — resources/list (선택)

미구현 또는 빈 배열 반환 시 등록 실패로 처리하지 않는다.  
`mimeType: image/*` 리소스가 있으면 썸네일로 자동 추출한다.

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

## 2. WEBAPP 타입 구현 요건 (REST)

JSON-RPC 래퍼 없이 엔드포인트 자체가 메서드 역할을 한다.  
**데이터 구조는 MCP 타입과 동일하다.**

### 필수 엔드포인트

| 엔드포인트 | 메서드 | 필수 | 설명 |
|---|---|---|---|
| `/health` | GET | O | MCP 타입과 동일 |
| `/tools` | GET | O | 등록 시 1회 자동 호출. 1개 이상 반환 필수 |
| `/tools/call` | POST | O | Tool 실행 |
| `/resources` | GET | 선택 | Resource 목록 |
| `/resources/read` | GET | 선택 | `?uri=` 쿼리 파라미터 |

### GET /tools

```json
{
  "tools": [
    {
      "name": "convert_hwp",
      "description": "HWP 파일을 PDF로 변환합니다.",
      "inputSchema": {
        "type": "object",
        "properties": {
          "fileUrl": { "type": "string" }
        },
        "required": ["fileUrl"]
      }
    }
  ]
}
```

### POST /tools/call

```json
// Request
{
  "name": "convert_hwp",
  "arguments": {
    "fileUrl": "https://example.com/doc.hwp",
    "credit": { "serviceType": "GPT3", "deductCredit": 50 }
  }
}

// Response
{
  "content": [
    { "type": "text", "text": "변환 완료. 다운로드 URL: https://..." }
  ]
}
```

> `arguments.credit`은 MCP 관리 서버가 자동 주입한다. [→ 3절 참고](#3-credit-처리)

### GET /resources (선택)

```json
{
  "resources": [
    { "uri": "image://logo.png", "name": "App Logo", "mimeType": "image/png" }
  ]
}
```

### GET /resources/read?uri={uri} (선택)

```json
{
  "contents": [
    { "uri": "image://logo.png", "mimeType": "image/png", "blob": "base64..." }
  ]
}
```

---

## 3. Credit 처리

`tools/call` 요청의 `arguments`에는 항상 `credit` 필드가 포함된다.  
MCP 관리 서버가 자동 주입하며, 서버 개발자가 별도로 요청할 필요 없다.

```json
"arguments": {
  // ... 비즈니스 파라미터 ...
  "credit": {
    "serviceType": "GPT3",
    "deductCredit": 50
  }
}
```

**서버가 해야 할 것**

1. `arguments.credit` 추출 (비즈니스 로직에 전달하지 않음)
2. 비즈니스 로직 실행
3. 성공 시 → 크레딧 서버에 차감 요청
4. 실패 시 → 차감 없음

```python
def call_tool(name, args):
    credit = args.pop("credit", None)
    result = execute_business_logic(name, args)
    if result["success"] and credit:
        deduct_credit(
            service_type=credit["serviceType"],
            deduct_credit=credit["deductCredit"],
            token=request.headers.get("Authorization")
        )
    return result
```

**사용자 식별**

MCP 관리 서버가 `Authorization: Bearer {token}` 헤더를 그대로 전달한다.  
크레딧 차감 시 이 토큰을 크레딧 서버에 함께 전달하면 사용자가 특정된다.

| 항목 | 전달 방식 |
|---|---|
| 사용자 식별 | `Authorization: Bearer {token}` 헤더 |
| 차감 금액 | `arguments.credit.deductCredit` |
| 서비스 타입 | `arguments.credit.serviceType` |

---

## 4. 등록

```
POST /api/mcp/servers/register
Content-Type: application/json
```

```json
{
  "name": "my-mcp-server",
  "url": "https://my-mcp.example.com",
  "description": "서버 설명",
  "version": "1.0.0",
  "type": "MCP"
}
```

| 필드 | 필수 | 설명 |
|---|---|---|
| `name` | O | 서버 등록명 |
| `url` | O | Base URL (`/mcp`, `/health` 제외한 루트) |
| `description` | 선택 | |
| `version` | 선택 | |
| `type` | 선택 | `MCP` (기본값) / `WEBAPP` |

```json
// Response
{ "serverId": "550e8400-...", "status": "ACTIVE" }
```

등록 시 자동으로 처리되는 것:

| type | 자동 수집 | 실패 시 |
|---|---|---|
| MCP | `tools/list` (필수), `resources/list` (선택) | REGISTRATION_FAILED |
| WEBAPP | `GET /tools` (필수), `GET /resources` (선택) | REGISTRATION_FAILED |

---

## 5. 헬스체크 & 재등록

**헬스체크**: 등록 후 60초마다 `GET /health` 폴링. 3회 연속 실패 시 `INACTIVE`, 이후 복구 확인 시 `ACTIVE` 자동 전환.

**재등록**: 동일 URL로 재등록 요청 시 업데이트로 처리. 배포 파이프라인 마지막에 등록 호출 추가를 권장한다.

```yaml
# GitHub Actions 예시
- name: Register MCP server
  run: |
    curl -X POST https://mcp-manager/api/mcp/servers/register \
      -H "Content-Type: application/json" \
      -d '{"name":"my-mcp","url":"https://my-mcp.example.com","version":"1.0.0","type":"MCP"}'
```

| 항목 | 동작 |
|---|---|
| `serverId` | 기존 ID 재사용 |
| `tools` / `resources` | 재수집하여 최신 상태로 덮어씀 |
| `registeredAt` | 최초 등록 시각 유지 |

---

## 6. ServerStatus

| 값 | 설명 |
|---|---|
| `PENDING` | 등록 요청 수신, 수집 진행 중 |
| `ACTIVE` | 정상 등록 완료 및 헬스체크 통과 |
| `INACTIVE` | 헬스체크 3회 연속 실패 |
| `REGISTRATION_FAILED` | tools 수집 실패 또는 빈 배열 반환 |

---

## 참고

- MCP 공식 스펙: https://modelcontextprotocol.io
- JSON-RPC 2.0: https://www.jsonrpc.org/specification
- 인증 방식 및 실행 흐름은 별도 확정 예정
